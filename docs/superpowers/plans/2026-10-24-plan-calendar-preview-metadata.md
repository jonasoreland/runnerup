# Plan Calendar Workout Preview Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show category, defensible duration, and schedule/completion status in selected-day plan workout previews.

**Architecture:** Add a pure Kotlin preview projection for category, duration and status. Render the projection in the existing Compose `SelectedPlanDay` section; do not change plan/workout persistence formats.

**Tech Stack:** Kotlin, Jetpack Compose, Android resources, JUnit/Robolectric, Gradle.

**Spec:** [Plan Calendar Workout Preview Metadata](../specs/2026-10-24-plan-calendar-preview-metadata.md)

## Global Constraints

- Preserve the Play application ID `se.hampusandersson.relven`.
- Preserve the RunnerUp Tracker/session owner and existing plan/workout snapshots.
- No physical Android ADB device; use an isolated emulator only.
- One PR at a time; merge before beginning another.

## Review Focus

- Running segments without a defensible duration must not receive a fabricated duration — add unknown/manual-segment tests.
- Strength estimate arithmetic must not overflow or emit zero/negative ranges — cover minimum/maximum repetitions and timed exercises.
- Old/future schedule dates must remain launchable — preview status must not gate or alter start actions.
- Repeated strength completion must continue to show `Avklarat` — test completed state precedence over date status.
- Narrow layouts and long localized names must wrap — verify on an isolated 360dp emulator capture.

---

### Task 1: Add the tested preview projection

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/PlanWorkoutPreview.kt`
- Create: `app/test/java/org/runnerup/relven/ui/PlanWorkoutPreviewTest.kt`
- Modify: `app/test/java/org/runnerup/relven/ui/PlanCalendarStateTest.kt` only if fixture support is required.

- [x] Write tests for timed and repeated running segments, distance-paced segments, unknown manual/distance segments, strength repetition/time estimates, and date/completion status.
- [x] Run `:app:testLatestDebugUnitTest --tests org.runnerup.relven.ui.PlanWorkoutPreviewTest` and confirm the expected failures.
- [x] Implement the minimal pure projection and bounded estimates.
- [x] Re-run the focused tests and confirm they pass.

### Task 2: Render selected-day preview metadata

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarScreen.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`

- [x] Render workout category, duration/unknown state, and schedule/completion status alongside each name.
- [x] Place the Add action after the selected day's entries and retain empty/rest behavior.
- [x] Run the focused Compose/state tests and `:app:assembleLatestDebug`.

### Task 3: Emulator visual check and final verification

- [x] Install/run the latest debug app on an isolated emulator using ADB port 5039; do not use a physical device.
- [x] Capture the selected-day plan calendar and inspect narrow-phone wrapping and safe-area spacing (tested at the emulator's 393dp width).
- [x] Run the full `:app:testLatestDebugUnitTest`, `:app:lintLatestDebug`, `:app:assembleLatestDebug`, and `spotlessCheck`.
- [x] Run `git diff --check` and update `docs/implementation-status.md` with actual evidence and limitations.
- [ ] Review the diff, commit, open one PR, wait for CI/review, and merge before the next PR.
