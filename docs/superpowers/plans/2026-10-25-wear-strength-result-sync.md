# Wear Strength Result Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reliably synchronize confirmed strength set results between phone and Wear OS without losing partial results or overwriting conflicting data.

**Architecture:** A shared bounded binary protocol carries one immutable set result at a time. Both devices persist outgoing events in a bounded local outbox; the phone commits incoming results through `StrengthSessionStore` before acknowledging them, and the watch retries until it receives a matching commit acknowledgement.

**Tech Stack:** Existing Android Java/Kotlin modules, Wear Message API, SharedPreferences-backed synchronous outbox, existing AtomicFile-backed `StrengthSessionStore`, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-25-wear-strength-result-sync-design.md`; parent task: `docs/superpowers/specs/2026-10-24-reliable-wear-session-design.md`.

## Global Constraints

- Preserve `se.hampusandersson.relven` and existing saved strength sessions.
- Keep one Relven PR open at a time; merge before starting the next.
- Build phone and Wear debug APKs from updated `master` after every merge.
- Do not use physical-device ADB.
- No cloud dependency; retain phone `StrengthSessionStore` as the durable history.
- Unknown performance measurements remain null; only confirmed results are transmitted.

## Review Focus

- Replayed result ID with changed payload must be rejected without replacing the first commit.
- Delayed delivery after the session is completed must update the matching completed record without changing its completion time.
- Unknown or mismatched execution IDs must remain unacknowledged and must not attach to another workout.
- Outbox write/ACK failure must leave the event retryable after service recreation.
- Partial sessions must retain each accepted set without being represented as completed workouts.

---

### Task 1: Versioned result and acknowledgement protocol

**Files:**
- Create `common/src/main/java/org/runnerup/common/wear/StrengthResultProtocol.java`
- Test `common/src/test/java/org/runnerup/common/wear/StrengthResultProtocolTest.java`
- Modify `common/src/main/java/org/runnerup/common/util/Constants.java`

**Interfaces:** `encodeResult(Result)`, `decodeResult(byte[])`, `encodeAcknowledgement(Acknowledgement)`, and `decodeAcknowledgement(byte[])`; result values include execution/result UUIDs, exercise index/step/name, set number, nullable measured fields, timestamps, notes, and status; ACK outcomes are COMMITTED, DUPLICATE, CONFLICT, and SESSION_MISSING.

- [x] Write round-trip, bounds, nullable-value, malformed, unsupported-version, and result-ID validation tests.
- [x] Run the focused protocol test and confirm RED before implementation.
- [x] Implement protocol version 1, maximum 4 KiB encoded payload, UUID validation, positive set/exercise indexes, bounded text and optional measurement validation.
- [x] Re-run the focused test and confirm GREEN.

### Task 2: Durable shared result outbox

**Files:**
- Create `common/src/main/java/org/runnerup/common/wear/StrengthResultOutboxStore.java`
- Test `common/src/test/java/org/runnerup/common/wear/StrengthResultOutboxStoreTest.java`

**Interfaces:** `enqueue(Result)`, `pending(maxCount)`, `acknowledge(Acknowledgement)`, and `acceptAcknowledgement(Acknowledgement)`; use injectable `Storage` for deterministic tests and a synchronous SharedPreferences implementation for production.

- [x] Test persistence across store recreation, exact duplicate insertion, conflicting result ID, matching ACK removal, wrong ACK preservation, and 256-event / 1 MiB bounds.
- [x] Run the focused common test and confirm RED before implementation.
- [x] Implement append-before-send and exact ID/payload comparison.
- [x] Re-run the focused common test and confirm GREEN.

### Task 3: Commit synchronized results in the existing phone history

**Files:**
- Modify `app/src/main/kotlin/org/runnerup/relven/strength/StrengthSession.kt`
- Modify `app/src/main/kotlin/org/runnerup/relven/strength/StrengthSessionStore.kt`
- Test `app/test/java/org/runnerup/relven/strength/StrengthSessionHistoryStoreTest.kt`

**Interfaces:** `StrengthSessionStore.applySynchronizedResult(context, result): ApplyResult`, returning COMMITTED, DUPLICATE, CONFLICT, SESSION_MISSING, or INVALID; update active or completed JSON atomically and persist source result ID with the set result.

- [x] Test receiving a set into an active session, retry after reopening, a conflicting result ID, a different result for the same set, completed-session update retaining `finishedAtMillis`, and unknown execution.
- [x] Run the focused app test and confirm RED before implementation.
- [x] Add backward-compatible optional `sourceResultId` decoding and validated application through existing store operations.
- [x] Re-run the focused app test and confirm GREEN.

### Task 4: Watch result outbox and acknowledgement retry

**Files:**
- Modify `wear/src/main/java/org/runnerup/service/StateService.java`
- Test Wear result-sync policy/store integration in `wear/src/test/java/org/runnerup/service/` and common outbox tests.

**Interfaces:** `queueStrengthResult(Result)` persists before send; pending results replay on reconnect, and only a matching COMMITTED or DUPLICATE ACK removes an item.

- [x] Add common outbox/inbox tests for durable enqueue, idempotent receive, duplicate conflict detection, bounded retention, and matching ACK behavior.
- [x] Implement connected-peer filtering, result/ACK message paths, durable result retry after reconnect, and bounded retry scheduling without a second timer engine.
- [x] Re-run the full Wear unit suite and confirm GREEN. The Wear Data Layer itself remains for paired-emulator verification.

### Task 5: Phone receive, commit, and acknowledge

**Files:**
- Modify `app/src/wear/org/runnerup/tracker/component/TrackerWear.java`
- Modify `common/src/main/java/org/runnerup/common/util/Constants.java`
- Add focused phone integration tests or an extracted result-sync coordinator test.

**Interfaces:** Verify the source is the paired watch; decode the message; call `StrengthSessionStore.applySynchronizedResult`; persist a correlated acknowledgement before sending it; retry pending ACKs after reconnect. Do not ACK unknown sessions as committed.

- [x] Add receive-store and phone-history coverage for duplicate result handling, conflict response, session validation, and retry-safe outbox behavior.
- [x] Implement same verified-peer filtering, phone commit before acknowledgement, duplicate/conflict response, and retry after ACK delivery failure.
- [x] Re-run focused tests and confirm GREEN. Message transport and listener scheduling still need paired-emulator verification.

### Task 6: Finish Task 5A record, documentation, and verification

**Files:**
- Modify `docs/superpowers/plans/2026-10-24-reliable-wear-session.md`
- Modify this plan as tasks complete.

- [x] Mark Task 5A checklist complete based on merged PR #167.
- [x] Document result synchronization scope and record that Wear quick-entry UI and paired-emulator verification remain follow-up integration requirements.
- [x] Run common, app, Wear unit tests, both debug APK assemblies, applicable lint, and `spotlessCheck`.
- [x] Inspect final diff and `git diff --check`. Self-review performed because this turn has no reviewer-agent tool; no Critical/Important finding remains in the inspected diff.
- [ ] Commit and open the one serial PR; merge after hosted checks pass. Rebuild both debug APKs from updated `master` after merge.

## Remaining parent-task work

This PR supplies the durable per-set result transport and phone persistence. It does **not** complete the parent task's watch workout executor, watch-side quick repetition entry, synchronized rest/exercise progression, or paired phone/Wear runtime verification. Those remain a subsequent single PR before the recovery phase can be reported complete.
