# Wear Strength Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the same persisted strength session on Wear and phone, with watch authority while connected and retry-safe actual set results.

**Architecture:** Move the platform-independent strength reducer, workout parser and codec into `common`, then add a bounded prepare/projection exchange and a Wear-local durable store. The phone continues to own history; the watch owns connected live transitions and submits confirmed results over the existing durable outbox.

**Tech Stack:** Kotlin/Java, Android library module, Wear Data Layer, Compose for Wear, existing SharedPreferences/JSON persistence and JUnit.

**Spec:** `docs/superpowers/specs/2026-10-25-wear-strength-execution-design.md`; parent: `docs/superpowers/specs/2026-10-24-reliable-wear-session-design.md`.

## Global Constraints

- Preserve `se.hampusandersson.relven`, existing RunnerUp run behavior, phone-only execution, and old strength session records.
- Keep one PR open at a time; merge it before starting another; build both debug APKs from updated master after each merge.
- Never use the user's physical phone or watch through ADB.
- Persist watch state before sending results or acknowledging an authoritative transition.
- Never infer actual repetitions from planned ranges; confirm a value explicitly.
- Never compare monotonic clock values from different devices without an offset.

## Review Focus

- Malformed or oversized strength workouts must be rejected without replacing an already prepared session; cover parser and store boundaries.
- Duplicate and conflicting result IDs must not double-advance the shared reducer; cover outbox/inbox retries.
- A stale prepared session must not become active after a newer session replaces it; cover session ID and revision matching.
- Recreated watch UI must resume the persisted timer from a monotonic deadline; cover store reopen and paused/rest state.
- Disconnected controls must not appear accepted while phone history remains unchanged; cover authority rejection.

---

### Task 1: Shared strength execution core

**Files:**
- Move `app/src/main/kotlin/org/runnerup/relven/strength/StrengthSession.kt` to `common/src/main/kotlin/org/runnerup/relven/strength/StrengthSession.kt`.
- Move `app/src/main/org/runnerup/workout/RelvenStrengthWorkoutParser.java`, `RelvenJsonLimits.java`, and `app/src/main/kotlin/org/runnerup/workout/RelvenExerciseReference.kt` to matching `common/src/main` packages.
- Move `StrengthSessionCodec` from `StrengthSessionStore.kt` into a shared codec file without changing encoded fields or v1–v4 decoding.
- Add a durable `PREPARING` state and explicit begin transition so choosing a strength workout does not start its timer or imply active execution.
- Test shared reducer/parser behavior in common; retain app persistence compatibility tests.

- [x] Add common-module tests for reducer timed results after rest and current/legacy persistence compatibility.
- [x] Run focused common tests and confirm failure due to the missing common execution API.
- [x] Move the pure reducer, Workout v3 parser, exercise reference and session codec to common while retaining package names and serialization compatibility.
- [x] Add explicit strength preparation and begin behavior to the phone session flow.
- [x] Run focused common and app strength/session regression tests.

### Task 2: Strength preparation payload and watch store

**Files:**
- Add `common/.../wear/StrengthExecutionProtocol.java` for bounded strength preparation, acknowledgement and cancellation messages.
- Add `wear/.../service/PreparedStrengthSessionStore.java` using synchronous durable storage and the shared codec.
- Modify phone `WearConnectionProvider` and `RelvenHomeActivity` to prepare an existing in-progress strength execution to Wear with stable execution ID.
- Modify the existing Wear preparation listener and tests to validate and store the session before ACK.

- [x] Add protocol/store tests for round trip, size limits, stale replacement, exact retry, conflict and store recreation.
- [x] Run focused tests RED before implementation; oversized payload and delayed superseded retry regressions failed for their expected reasons.
- [x] Implement the versioned protocol and durable-before-ack store path. Wear replacement/cancellation state is committed atomically; superseded IDs are tombstoned so delayed retries cannot resurrect them.
- [x] Run common and Wear protocol/store tests plus the full common/app/Wear verification matrix.

**Task 2 scope note:** This task transfers and displays prepared strength workouts only. It does not yet implement authoritative watch transitions, watch start controls, GPS/sensor readiness, run-workout changes, or paired-emulator runtime verification; those remain in Tasks 3–5 and the parent Wear plan.

### Task 3: Watch-authoritative session transitions

**Files:**
- Modify Wear `StateService` to persist accepted strength transitions and expose observed state to `StartFragment`.
- Add phone receive/apply logic that commits the watch projection to the existing `StrengthSessionStore` before ACK.
- Reuse existing result outbox and stable event IDs; do not send an ACK before durable commit.

- [ ] Test watch authority, stale revision, duplicate action, reconnect replay, and result/projection ordering.
- [ ] Implement requests from phone as pending until watch acknowledgement; do not mutate phone state optimistically.
- [ ] Persist accepted watch changes before notifying the UI or outbox.
- [ ] Run focused session protocol, store and phone/Wear unit tests.

### Task 4: Wear active strength UI and quick logger

**Files:**
- Modify `wear/src/main/kotlin/org/runnerup/wear/ui/WearControlsScreen.kt` and `wear/src/main/java/org/runnerup/view/StartFragment.java`.
- Add Wear presentation state tests under `wear/src/test/kotlin/org/runnerup/wear/ui/`.

- [ ] Test current exercise/set mapping, manual and timed entry readiness, and compact-screen layout state.
- [ ] Add large plus/minus repetition controls with explicit save, zero support, and no keyboard launch.
- [ ] Show actual timer/rest from the shared reducer; keep remaining result input accessible after rest ends.
- [ ] Add start/pause/resume/skip/finish actions that call the shared authoritative coordinator.
- [ ] Verify composition disposal and listener cleanup across fragment lifecycle.

### Task 5: Verification, documentation, and integration

**Files:**
- Update `docs/superpowers/plans/2026-10-24-reliable-wear-session.md` and this plan.
- Update strength/Wear architecture docs where the ownership contract is documented.

- [ ] Run common, full app, and Wear unit tests; assemble phone and Wear debug APKs; run applicable lint and Spotless.
- [ ] Confirm release application ID and debug variant IDs remain unchanged.
- [ ] Inspect final diff, run `git diff --check`, and review all authority and persistence transitions.
- [ ] Open exactly one PR, wait for hosted checks, merge it, then rebuild both APKs from updated `master`.
