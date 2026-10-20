# Progress Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an offline screen showing evidence-based 7-day and 28-day activity summaries.

**Architecture:** Query RunnerUp activity rows off the main thread, project them through a pure Kotlin summary function, and render a typed state in a new home destination. No schema or recording changes.

**Tech Stack:** Kotlin, Android SQLite, Jetpack Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-progress-overview-design.md`

## Global Constraints

- Preserve `se.hampusandersson.relven` and RunnerUp as the recording engine.
- Use local recorded evidence only; no network, inferred values, scores, or recommendations.
- Keep training load explicitly unavailable.
- Do not use physical ADB or emulator.
- Maintain one open PR at a time and merge it before beginning another increment.

## Review Focus

- Include an activity exactly on the rolling cutoff and exclude one just before it.
- Count duplicate database IDs once.
- Skip malformed timestamps and negative durations; skip invalid or zero distances without hiding otherwise valid time records. If the summed distance overflows, retain coverage count and mark the total unavailable.
- Distinguish 7-day from 28-day windows and render an empty result accurately.
- Surface database errors with an actionable retry.

### Task 1: Pure activity aggregation

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/analysis/ProgressSummary.kt`; create `app/test/java/org/runnerup/relven/analysis/ProgressSummaryTest.kt`.

- [x] Write tests for rolling boundaries, duplicate IDs, invalid fields, and exact window totals.
- [x] Run the focused tests and confirm failure because the summary API is missing.
- [x] Implement `summarizeActivities(rows, nowMillis)` returning 7-day and 28-day counts, duration, distance, and the number of sessions contributing valid distance.
- [x] Run focused tests and confirm green; add a regression test proving an invalid duplicate does not hide a valid row.

### Task 2: Database loading and typed UI states

**Files:** Create `app/src/main/kotlin/org/runnerup/relven/analysis/ProgressLoadState.kt`; create matching tests; modify `RelvenHomeActivity.kt`.

- [x] Add loading, loaded, and unavailable states with tested result mapping.
- [x] Query activity ID, start, duration, distance, and deleted status from RunnerUp's activity table on `Dispatchers.IO`.
- [x] Load on resume, retain captured time for window consistency, and support retry.
- [x] Run focused tests.

### Task 3: Add Progress destination and localized UI

**Files:** Modify `RelvenHomeActivity.kt`, English and Swedish string resources; add focused UI/model tests where the current test setup permits.

- [x] Add a Progress destination reachable from Today and retain destination across recreation.
- [x] Render loading, empty, error/retry, 7-day and 28-day summaries, plus explicit unavailable training-load copy.
- [x] Keep narrow-width content scrollable and controls accessible.
- [x] Run relevant tests and inspect the native Compose source against the design contract. Device rendering is deferred to the approved final manual-test phase; no ADB was used.

### Task 4: Verify and integrate one PR

- [x] Run app unit tests, latest debug/release builds, debug/release lint, Spotless, and `git diff --check`; all passed with no new lint findings.
- [x] Verify release APK metadata with `aapt dump badging`: `se.hampusandersson.relven`, versionCode `24000374`.
- [x] Request independent review and fix actionable findings; review cleared the stale-result race, zero-distance coverage, and overflow handling.
- [ ] Commit and open one PR after checking the fork has no open PRs.
- [ ] Wait for hosted CI, merge only when green, and verify merge SHA and empty PR queue.
- [ ] Record actual results in implementation status and feature parity.
