# Plan: guided strength session execution

## Global constraints

- Keep `se.hampusandersson.relven`; use the existing validated plan and exercise snapshots.
- RunnerUp remains the running execution engine; strength execution is local/offline and separate.
- Do not use ADB or release signing material before all phases are complete.

## Review focus

- Timed sets and repetition sets both support the same prescribed set/rest progression.
- A paused session survives process recreation and retains already logged results.
- Revisions retain completion history because completion is keyed by plan entry identity, not current plan JSON.
- Duplicate finish actions do not create duplicate completion records.
- Malformed saved progress is reported/recovered without marking a plan item complete.

## Tasks

### Task 1: Session progression model

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/strength/StrengthSession.kt`; test `app/test/java/org/runnerup/relven/strength/StrengthSessionTest.kt`.

- [x] Test first set, rep validation, timed set completion, rest, next exercise, pause/resume, and final completion.
- [x] Implement immutable state transitions over the existing `RelvenStrengthWorkoutParser.Workout` exercises.

### Task 2: Local resumable session store

**Files:** Create `app/src/main/org/runnerup/workout/RelvenStrengthSessionStore.java`; test codec in `app/test/java/org/runnerup/workout/RelvenStrengthSessionStoreTest.java`.

- [x] Test round-trip and malformed saved session rejection for a bounded versioned JSON record.
- [x] Store one in-progress session and completed entry records atomically in app-private storage; expose lookup by scheduled entry ID.

### Task 3: Calendar launch, guided UI, and completion summary

**Files:** Modify `RelvenHomeActivity.kt`, `PlanCalendarScreen.kt`, `PlanCalendarState.kt`; create `StrengthSessionScreen.kt`; add English/Swedish strings in app/common resources as appropriate.

- [x] Test calendar strength entry exposes start/completed state without changing running entries.
- [x] Add launch and completion summary flows; persist every set transition and resume after recreation.
- [x] Run relevant app unit tests, lint, Spotless, debug and release builds; audit changed UI for accessibility and narrow-screen behavior.

### Task 4: One-PR integration

- [ ] Review final diff, verify application ID and no ADB usage, commit and open a single PR.
- [ ] Wait for all hosted checks; fix and rerun failures, merge only when green, confirm no open PR, and update phase status.
