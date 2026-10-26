# Wear route reconciliation implementation plan

> **For agentic workers:** follow test-first, one task at a time. This feature follows the accepted design in `docs/superpowers/specs/2026-10-26-wear-offline-telemetry-design.md` and the next-PR note in `2026-10-26-wear-phone-telemetry-inbox.md`.

**Goal:** Convert the durable Wear telemetry inbox into idempotent route and heart-rate data in the existing RunnerUp activity, fill only verified gaps in phone GPS coverage, refresh opted-in Health Connect route exports, and preserve authoritative activity totals and correct route chronology across app consumers.

**Constraints:** Keep `se.hampusandersson.relven`; do not create another activity, workout state machine, route database, or tracker. The phone route remains primary. Commit route materialization before ACK. Preserve each accepted inbox sample. Only use coordinates with measured acceptable accuracy. Never modify the physical phone or watch through ADB. Maintain one Relven PR at a time in this checkout.

## Pre-flight findings

- PR #196 stores exact protocol payloads in `relven_wear_telemetry_sample`, associates each session with one activity, and ACKs after inbox commit. It does not yet write `location` rows.
- `ActivityDetailState.readActivityRoute`, `LocationEntity.LocationList`, `PathCursor`, and legacy summary recomputation currently consume route rows by `_id`; late offline rows therefore need a chronology policy.
- `HealthConnectActivitySync.onActivitySaved` already performs idempotent owned-export updates and uses its outbox; route reconciliation must finish before invoking it.
- Existing phone tracker rows store active elapsed and cumulative distance checkpoints. Wear elapsed is interpolated only between valid phone checkpoints; wall timestamps remain the route-placement fallback only while the pre-existing phone route is monotonic. A phone wall-clock regression falls back to durable insertion order.

## Planned files

- `app/src/main/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryRouteReconciler.kt`: route reconciliation, accuracy/coverage decisions, durable per-sample disposition, and checkpoint-safe route metrics.
- `app/src/main/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStore.kt`: expose pending committed samples and mark materialization in the same database transaction as route-row writes.
- `app/src/main/org/runnerup/db/DBHelper.java`: additive reconciliation-ledger schema and idempotent upgrade backfill for samples already ACKed by #196.
- `app/src/main/org/runnerup/db/ActivityRouteOrder.java`, `PathCursor.java`, `ActivityCleaner.java`, `db/entities/LocationEntity.java`, and `ActivityDetailState.kt`: keep legacy and Relven route consumers chronological for reconciled activities while preserving existing ordering for untouched activities.
- `app/src/wear/org/runnerup/relven/strength/StrengthResultListenerService.kt`: reconcile before ACK and invoke Health Connect refresh only after a finalized activity has its route persisted.
- Tests under `app/test/kotlin/org/runnerup/relven/telemetry` and existing route/detail/Health Connect test packages.

## Tasks

### 1. Define durable route disposition and coverage policy

- Add focused tests first for sample decoding, invalid/unknown coordinates, missing or poor accuracy, phone point coincidence, gap insertion, paused interval rejection, and duplicate disposition.
- Add an additive receipt table keyed by `(session_id, sequence)` recording disposition and optional `location_id`; keep existing inbox samples and ACK cursor untouched.
- Implement deterministic coverage policy: phone GPS wins within the defined time window; watch GPS can fill a gap only with measured accuracy within the accepted limit; HR-only samples enrich a nearby phone point only when it has no HR; unsupported/low-quality samples are retained in the inbox and marked intentionally not routeable.
- Verify a failing test before production changes and then focused green tests.

### 2. Materialize committed samples transactionally and backfill #196 receipts

- Add test-first reconciliation for a new batch, duplicate delivery, partial overlap, process reopen, and an already-committed inbox sample with no receipt.
- Write accepted location/HR fields to the existing `location` table and the receipt in one SQLite transaction; never create activity rows.
- Reconcile on the receiver before returning ACK. If reconciliation fails after inbox commit, do not ACK; a duplicate replay must retry pending materialization.
- Run an idempotent backfill for committed pre-PR samples on database open, so watch samples already ACKed by #196 are not stranded.

### 3. Preserve chronology and refresh derived outputs

- Tests first: late watch samples interleave chronologically, repeated backfill has no duplicate route rows, paused samples are excluded, and summaries remain valid.
- Add a single per-activity route-order helper. Untouched/legacy activities retain `_id` order; activities with materialized Wear points use the recorded route timestamp with `_id` as a stable tie-breaker.
- Apply this order to Relven detail/map, RunnerUp graph/map, GPX/TCX/RunKeeper readers, and ActivityCleaner recomputation paths discovered in the audit.
- Keep the persisted RunnerUp activity totals authoritative. A watch route can be partial and its monotonic clock has no shared origin with the phone, so do not rewrite a known saved duration/distance or turn a partial watch-only route into a total. Route points may receive distance only when anchored to persisted phone checkpoints; elapsed is interpolated only between monotonic phone checkpoints in one active segment. Unknown watch-only distance/elapsed stays unknown, and the detail route reader must not substitute wall-clock elapsed for those rows.
- Break pace analysis around unanchored Wear timestamps so later phone checkpoints cannot create a misleading pace segment. If phone wall timestamps regress, preserve phone insertion chronology; watch-only routes also retain committed sample sequence order. The protocol still lacks a shared phone/watch monotonic timestamp anchor, so exact mixed-device route placement under a constant clock offset remains a known limitation; only anchored elapsed/route metrics are treated as authoritative.
- Queue a Health Connect update only after the route/receipt transaction commits, and only for an already opted-in or Relven-owned export. Never auto-publish a new activity based on late telemetry.
- Test the summary-preservation invariant, the unknown watch-only route metrics, and that phone elapsed interpolation rejects pause-spanning checkpoints.

### 4. Verify, review, and prepare one PR

- Run focused tests, all app unit tests, app lint, Spotless, `git diff --check`, and phone/Wear debug builds.
- Review every route consumer and ensure no physical ADB device is used.
- Push one branch and open one PR; wait for CI, merge it, fast-forward master, then rebuild both debug APKs from master.

## Verification commands

Focused suite: `:app:testLatestDebugUnitTest --tests org.runnerup.relven.telemetry.* --tests org.runnerup.relven.ui.ActivityDetailStateTest --tests org.runnerup.relven.health.*`

Final local gate: `spotlessApply :common:testDebugUnitTest :wear:testDebugUnitTest :app:testLatestDebugUnitTest :wear:lintDebug :app:lintLatestDebug :wear:assembleDebug :app:assembleLatestDebug spotlessCheck`

Do not use physical ADB. Hosted Android/F-Droid CI and the merged master APKs are required before starting another PR.

## Local verification (2026-10-26)

- `spotlessApply :common:testDebugUnitTest :wear:testDebugUnitTest :app:testLatestDebugUnitTest :wear:lintDebug :app:lintLatestDebug :wear:assembleDebug :app:assembleLatestDebug spotlessCheck` completed successfully: 928 unit tests, 0 failures/errors/skips; both lint tasks passed with no new findings; both debug APKs assembled; formatting passed.
- `git diff --check` passed.
- No physical phone/watch ADB was used. Hosted CI and post-merge master APK rebuild remain pending.
- The telemetry protocol has no shared monotonic clock anchor between phone and watch. Anchored route metrics are mapped using persisted phone checkpoints; mixed-device route placement still assumes sufficiently synchronized wall clocks when the phone route is monotonic. This is a documented limitation for paired-emulator verification and future protocol work.
