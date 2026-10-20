# Running segment editor implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement inline task-by-task. Steps use checkbox syntax.

**Goal:** Add immutable active-plan revisions that change one existing running segment end or target and expose those edits from the Plan calendar.

**Architecture:** Keep the original Workout v3 JSON as the source of truth. Add a bounded segment summary parser for the UI, pure JSON revision transforms, strict one-field revision-lineage checks, and localized edit dialogs. The RunnerUp execution model remains unchanged and is rebuilt from the revised plan snapshot as it is today.

**Tech Stack:** Android Java/Kotlin, Compose Material 3, org.json, Gradle/JUnit/Robolectric.

**Spec:** docs/superpowers/specs/2026-10-20-running-segment-edit-design.md

## Global Constraints

- Keep application ID se.hampusandersson.relven.
- Keep each user edit as a complete immutable schema-v2 snapshot that follows the saved lineage tip.
- Do not modify Tracker, RunnerUp execution, activity history, or source v1 packages.
- Do not use physical ADB or emulator for this increment.
- Maintain one open PR at a time and merge it before starting the next increment.

## Review Focus

- Nested sequence/repeat: stable segment IDs resolve to exactly one source node; repeat counts only describe executions.
- Missing or duplicate segment ID: reject the revision without changing the saved plan.
- Target removal: deleting the target key is a single target edit and must pass all running workout validation.
- Non-finite, fractional where integer is required, or out-of-range input: reject it before persistence.
- Forged v2 snapshot that changes two segments or a strength segment: reject it in store lineage validation.

---

### Task 1: Parse running segment summaries

**Files:** Create app/src/main/kotlin/org/runnerup/relven/ui/RunningWorkoutDefinitions.kt; modify PlanCalendarState.kt; test PlanCalendarStateTest.kt.

**Interfaces:** runningWorkoutSegments(plan, workoutId): List<RunningSegmentSummary> returns ordered source segment IDs, labels, phase, end, target, and repeat count for running workouts only.

- [x] Write tests for a normal segment, nested sequence, repeated nested segment, non-running workout, and malformed plan source.
- [x] Run the focused UI state test and verify the new API is missing.
- [x] Implement the bounded summary parser, preserving source order and annotating repeat count.
- [x] Run focused UI tests and verify they pass.

### Task 2: Transform and validate one segment field

**Files:** Modify RelvenPlanRevision.java; test RelvenPlanRevisionTest.java.

**Interfaces:** editRunningSegmentEnd(source, workoutId, stepId, end, revisionId) and editRunningSegmentTarget(source, workoutId, stepId, target, revisionId) produce complete v2 Plan snapshots.

- [x] Add tests for end changes, target add/change/remove, nested segments, unknown IDs, non-running workouts, unchanged values, bad ranges, and preserved order/repeats.
- [x] Run the focused revision tests and verify they fail for the new operations.
- [x] Implement the two transformations by locating a unique segment ID and replacing only end or target.
- [x] Run focused revision tests and verify they pass.

### Task 3: Enforce narrow revision lineage

**Files:** Modify RelvenPlanStore.java; test RelvenPlanStoreTest.java.

- [x] Add store tests accepting each one-field edit and rejecting multi-field, multi-segment, and schedule-mixed edits; strength/prehab edits are rejected by the revision transform.
- [x] Run focused store tests and verify the valid edits are rejected before the rule is added.
- [x] Implement recursive structural comparison that permits one end/target difference inside one running segment and preserves all current revision rules.
- [x] Run focused store tests and verify they pass.

### Task 4: Add the localized calendar editor

**Files:** Modify PlanCalendarScreen.kt, PlanCalendarState.kt, RelvenHomeActivity.kt, common English/Swedish strings; test UI state and Compose behavior where practical.

- [x] Add state tests for duration/target input parsing, bounds, target ranges, and no-target selection.
- [x] Run focused UI tests and verify they fail for the missing editor state.
- [x] Render ordered running segment summaries with separate Edit duration and Edit target actions. Use localized time/distance/pace/speed/heart-rate units, field validation, explicit cancel/save controls, and existing revision status feedback.
- [x] Wire actions to Store revision methods; do not expose edit actions for strength/prehab.
- [x] Run the focused UI and store tests and verify they pass.

### Task 5: Verify and ship one PR

- [x] Update implementation status and feature parity.
- [x] Run :app:assembleLatestDebug :app:assembleLatestRelease :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck.
- [x] Run git diff --check, verify release application ID configuration and open PR queue.
- [ ] Review the branch, commit, push, open one PR, wait for hosted CI, merge, and verify no PR remains open.
