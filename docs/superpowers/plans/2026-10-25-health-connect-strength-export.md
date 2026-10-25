# Health Connect strength export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish completed strength/prehab sessions and confirmed set performance through Relven's existing opt-in Health Connect outbox.

**Architecture:** Extend the shared strength-session snapshot with an optional real start timestamp and populate confirmed set intervals from actual runtime events. Adapt finalized strength records to the existing `RecordedWorkoutForHealthConnect` payload, outbox, and worker; make startup reconciliation and explicit historical backfill include those records.

**Tech Stack:** Kotlin/Java, Health Connect Client 1.1.0, existing versioned strength codec, file outbox, WorkManager, Robolectric/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-25-health-connect-strength-export-design.md`

## Global Constraints

- Preserve the release application ID `se.hampusandersson.relven`.
- Keep Health Connect one-way and opt-in; do not request read permissions.
- Do not start a second outbox or tracking engine.
- Do not fabricate workout start, set intervals, repetitions, heart rate, load, calories, or distance.
- Preserve compatibility with strength session codec versions 1–5.
- Work in the existing checkout with at most one open Relven PR.
- Do not use physical-device ADB; emulator use is allowed.

## Review Focus

- Legacy or imported completion without a valid start time is not exported as a fabricated interval.
- A retry after process death keeps the same execution-derived identity and export version.
- Repetition prescriptions and skipped/incomplete sets never become performed values.
- Missing, overlapping, or out-of-session set timestamps produce no Health Connect exercise segment.
- Turning export off prevents new automatic exports while existing retry/delete semantics remain consistent.

---

### Task 1: Persist actual session and set timestamps

**Files:**
- Modify: `common/src/main/kotlin/org/runnerup/relven/strength/StrengthSession.kt`
- Modify: `common/src/main/kotlin/org/runnerup/relven/strength/StrengthSessionCodec.kt`
- Modify: phone and Wear strength execution call sites in `RelvenHomeActivity.kt`, `StartFragment.java`, `WearStrengthExecutionController.kt`, and `StrengthSessionAuthorityProtocol.kt`
- Test: shared state and codec tests under `common/src/test` and `app/test`

- [x] Add failing tests for a real start timestamp, confirmed set start/completion timestamps, and decoding v1–v5 without those fields.
- [x] Run the focused tests and verify they fail for the missing fields/behavior.
- [x] Add optional session and active-set wall-clock timestamps; keep elapsed-realtime deadlines as the timer source. Capture boundaries at begin, confirmed completion, timed completion, and rest transition. Clear active-set timestamp on a paused work set so a paused interval cannot be represented as continuous exercise.
- [x] Bump the codec to v6, serialize the optional values, and decode older versions as null.
- [x] Pass wall-clock observations from the actual phone/watch execution entry points without changing the Wear authority protocol identity or timer behavior.
- [x] Run focused common/app/Wear strength tests.

### Task 2: Map recorded strength sessions to Health Connect records

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/health/WorkoutHealthConnectMapper.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectExportStore.kt`
- Test: `app/test/java/org/runnerup/relven/health/WorkoutHealthConnectMapperTest.kt` and codec/outbox tests

- [x] Add failing mapper tests for a strength session, valid repetition segments, RPE, per-side notes, omitted invalid/overlapping intervals, and unknown exercises.
- [x] Run the focused mapper tests and verify the expected failures.
- [x] Add an optional exercise-segment model to the existing recorded-workout payload and its version-compatible JSON codec.
- [x] Map strength to `EXERCISE_TYPE_STRENGTH_TRAINING`; map only valid contained set intervals to `ExerciseSegment`, preserving explicit clean reps/RPE and using a standard segment type or `UNKNOWN`.
- [x] Build bounded notes from exact exercise snapshots and confirmed results; exclude prescribed-only, skipped, and incomplete data.
- [x] Verify existing run mapping and outbox payload round trips remain unchanged.

### Task 3: Queue completion and reconcile historical strength sessions

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectActivitySync.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectSettingsAccess.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Test: `app/test/java/org/runnerup/relven/health/HealthConnectActivitySyncTest.kt`, `HealthConnectSettingsAccessTest.kt`, and strength history tests

- [x] Add failing tests for automatic finalized-session enqueue, opted-out behavior, identical completion retry, modified imported/edit records, startup reconciliation, and explicit history backfill/count.
- [x] Run the focused tests and verify they fail for the missing strength path; add a regression proving startup does not silently backfill unrequested sessions.
- [x] Create stable IDs as `strength-<executionId>` and adapt only completed records with valid positive session bounds.
- [x] Queue after local completion persistence, reconcile only completions that durably captured export opt-in, and include eligible strength sessions in the explicit Settings backfill/count.
- [x] Preserve the current activity backfill and deletion behavior.
- [x] Run focused Health Connect tests; full verification is tracked in Task 4.

### Task 4: Review and integration

- [x] Update Health Connect documentation with strength sources, identifiers, eligibility, and the no-inferred-segments rule.
- [x] Run all app/common/Wear unit tests, app and Wear lint, Spotless, and phone/Wear debug APK builds.
- [x] Inspect the diff for package identity, no Health Connect read permissions, no duplicate outbox, and no prescription-to-result leakage.
- [ ] Open one PR, wait for checks/review, merge it, and rebuild both debug APKs from updated `master`.
