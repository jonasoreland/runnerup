# Plan Library and Calendar UI Implementation Plan

**Goal:** Expose plan creation, saved-plan management, and calendar navigation inside Relven.

**Architecture:** Add a Compose library and empty-month calendar, while keeping the existing revision-aware calendar for active plans. RelvenHomeActivity owns document picking and calls the existing local RelvenPlanStore/AI Plan Kit APIs. The in-app creator writes the same canonical plan-v1 format and is available alongside AI generation/import.

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

- [x] Add a tested Monday-first empty-month grid helper.
- [x] Define loading/loaded/error library states and Compose library/calendar.

### Task 2: Local plan operations

- [x] Add plan JSON picker, validated preview/save, AI Plan Kit copy action, and activation/clear callbacks.
- [x] Wire Plan tab to library when no plan is active and expose library from the active calendar.

### Task 3: In-app authoring

- [x] Create a localized weekly plan builder that emits a canonical schema-v1 package.
- [x] Support active no-equipment exercises with repetitions or timed work, sets, and rest for strength/prehab days.
- [x] Persist pending creation so activation/save can be retried without duplicate plans.
- [x] Validate prescriptions and recover cleanly when the exercise catalog is unavailable.

### Task 4: Verification

- [x] Run focused tests, full unit suite, Spotless, release lint, and debug APK build.
- [x] Review the diff and fix the independent review findings before opening the single PR.
- [ ] Merge this PR before starting the next saved-plan-management PR.
