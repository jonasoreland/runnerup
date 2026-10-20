# Strength Progression History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline, read-only per-exercise history of actual sets from completed guided strength sessions.

**Architecture:** Enumerate and validate recent private completion records in `StrengthSessionStore`. Project matching set results by stable exercise ID in a pure model, then pass a loading state into the existing exercise catalog detail screen. This adds no history schema, server, or running-engine changes.

**Tech Stack:** Kotlin, Android private files/AtomicFile, Jetpack Compose, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-20-strength-progression-history.md`

## Global Constraints

- Completion records remain private and offline.
- Exercise identity uses stable exercise IDs, not localized names or workout step IDs.
- Display only recorded values; never invent training-load scores or recommendations.
- Keep the history bounded to 10 sessions per exercise, 500 files and 32 MiB per refresh; disclose invalid or omitted records.
- Preserve Relven visual tokens and do not use the physical ADB device.

## Review Focus

- Same exercise name with different stable IDs must not combine history; test distinct IDs and localized name changes.
- Multiple sets in a session must remain in set order; test ordering and exact values.
- Timed results must not render as repetitions and vice versa; test both result types.
- A damaged file must not hide valid records or appear as valid history; test partial load behavior.
- Large history must remain bounded and newest-first; test the 10-session projection and store scan cap.

---

### Task 1: Project completed results into per-exercise history

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/strength/StrengthProgression.kt`; create `app/test/java/org/runnerup/relven/strength/StrengthProgressionTest.kt`.

**Interface:** `exerciseProgression(exerciseId: String, sessions: List<CompletedStrengthSession>, limit: Int = 10): List<ExerciseProgressSession>` returns matching completed workouts newest first, each with its original matching `SetResult`s in recorded order.

- [x] Write failing tests named `groupsActualSetsByStableExerciseId`, `keepsSetOrderAndExactValues`, `sortsNewestFirstAndLimitsToTenSessions`, `doesNotMatchByDisplayName`, and `keepsTimedResultsDistinctFromRepetitions`.
- [x] Run `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.strength.StrengthProgressionTest` and confirm the expected missing API/test failures.
- [x] Implement the projection using `session.state.workout.exercises()[result.exerciseIndex].exerciseId()`; use `finishedAtMillis` for ordering and preserve matching result order.
- [x] Rerun the focused tests and confirm all pass, including matching stable IDs when the stored localized exercise name differs.

### Task 2: Load bounded completed history from private storage

**Files:** Modify `app/src/main/kotlin/org/runnerup/relven/strength/StrengthSessionStore.kt`; create `app/test/java/org/runnerup/relven/strength/StrengthSessionHistoryStoreTest.kt`.

**Interface:** `completedSessions(context: Context): StrengthSessionHistoryLoad` reads up to the 500 newest completion files; it returns validated records and an invalid-record count. A directory/listing failure is surfaced as an IO failure for the caller to handle.

- [x] Write Robolectric tests named `loadsValidCompletedRecordsNewestFirst`, `skipsAndCountsInvalidCompletionRecords`, `ignoresActiveSessionFile`, and `boundsTheFileScanToFiveHundredRecentRecords`.
- [x] Run the focused store test and correct the workout fixture to the existing schema after its parse failure identified the invalid fixture.
- [x] Implement the bounded private-file scan, strict completion validation, finished-time ordering, omitted/partial-record count, and 32 MiB read budget without changing codec schema.
- [x] Rerun store tests in the full app test suite; `StrengthSessionCodecTest` also passes unchanged.

### Task 3: Add history states and render exercise detail

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseProgressionState.kt`; create matching tests; modify `RelvenHomeActivity.kt`, `ExerciseCatalogScreen.kt`, and English/Swedish resources in `common/src/main/res/values/strings.xml` and `values-sv/strings.xml`.

- [x] Write state-mapping tests for loading, empty, loaded, partially unavailable, and retryable unavailable history.
- [x] Run the focused state test and verify the state mappings for loading, empty, partial, and unavailable history.
- [x] Load private history on `Dispatchers.IO`, map it by catalog exercise ID, refresh after completion, and pass typed state plus retry callback to the catalog screen.
- [x] Add localized exercise-detail history with observable-locale dates and exact per-set values, loading, empty, partial-record, retry, and unavailable states.
- [x] Run the state and catalog tests as part of the full app test suite.

### Task 4: Verify and integrate one PR

- [x] Run `spotlessApply` and the full local suite: app unit tests, latest debug/release builds, latest debug/release lint, Wear debug/release builds and tests, HR device tests/lint, and `spotlessCheck`.
- [x] Build the free/F-Droid release variant, then restore the Play release variant and verify `aapt dump badging` reports `se.hampusandersson.relven`.
- [x] Review the rendered Compose detail code for loading/empty/partial/error states, narrow-width scrolling, semantics, focus, and TalkBack text. No ADB was used; no emulator was available for a render inspection.
- [x] Fetch and apply the current interface guidelines to the changed Compose UI; release lint found one non-observable locale access, which was corrected and both app lint variants pass.
- [x] Request independent code review; approved with no findings. Confirmed `git diff --check` is clean.
- [ ] Commit and open exactly one PR after confirming there are no open PRs in `HampusAndersson01/Relven-2.0`.
- [ ] Wait for hosted build/test/lint/style/F-Droid checks; merge only when green, verify the merge commit and empty PR queue, and record actual results in `docs/implementation-status.md` and `docs/feature-parity.md`.
