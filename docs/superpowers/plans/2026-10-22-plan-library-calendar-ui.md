# Plan Library and Calendar UI Implementation Plan

**Goal:** Expose plan creation, saved-plan management, and calendar navigation inside Relven.

**Architecture:** Add a Compose library and empty-month calendar, while keeping the existing revision-aware calendar for active plans. RelvenHomeActivity owns document picking and calls the existing local RelvenPlanStore/AI Plan Kit APIs.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android document picker, existing Java plan store/importer.

**Spec:** `docs/superpowers/specs/2026-10-22-plan-library-calendar-ui.md`

## Global Constraints
- Preserve `se.hampusandersson.relven` and RunnerUp workout authority.
- Plans remain local and offline; import validates before saving.
- Use the existing plan v1/v2 schemas and revision APIs.
- One PR at a time; use the existing worktree; no physical ADB.

## Review Focus
- No-plan users see a real calendar grid and clear create/import actions.
- Invalid plan files never save; preview is explicit before saving.
- Importing a valid plan refreshes the library and never activates it implicitly.
- Activation and clear actions update Today and Plan destinations.
- Reopening the active plan calendar preserves existing revision/edit behavior.

### Task 1: Calendar grid and library state

- [ ] Add a tested Monday-first empty-month grid helper.
- [ ] Define loading/loaded/error library states and Compose library/calendar.

### Task 2: Local plan operations

- [ ] Add plan JSON picker, validated preview/save, AI Plan Kit copy action, and activation/clear callbacks.
- [ ] Wire Plan tab to library when no plan is active and expose library from the active calendar.

### Task 3: Verification

- [ ] Run focused tests, full unit suite, spotless, and debug APK build.
- [ ] Review the diff and create one PR; merge before starting another.
