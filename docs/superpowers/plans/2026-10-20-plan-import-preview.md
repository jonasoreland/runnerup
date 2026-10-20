# Native Plan Import and Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user select a Relven plan JSON package, review its one-time schedule, and explicitly save it to private local storage.

**Architecture:** Reuse the existing workout-management activity and Android document picker. Read package bytes under the existing 4 MiB cap off the UI thread, parse fully before showing a preview, and call the existing atomic store only after explicit acceptance.

**Tech Stack:** Java, Android Activity Result API, AppCompat AlertDialog, JUnit/Robolectric.

**Spec:** `docs/plan-format.md`; Atomic `2f0d1e56-bfa7-4c8c-884b-4cb8f57677d8`.

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- Do not use the owner's physical device via ADB until all phases are complete and the app is ready for manual testing.
- Validate the full package before any write; import is all-or-none.
- Saving a package does not activate it or change the active schedule.
- Scheduled dates are one-time; never imply recurrence.
- Keep one open PR at a time and merge it before starting another PR.

## Review Focus

- Input streams larger than 4 MiB are rejected without unbounded allocation.
- Invalid package JSON and unsupported workout semantics cause no storage write.
- Canceling preview causes no storage write.
- Re-importing the same plan ID reports a duplicate without overwriting the saved file.
- Preview states date range, session counts, and that dates are one-time and inactive until a later activation flow.

---

### Task 1: Bounded read and plan preview summary

**Files:**
- Create `app/src/main/org/runnerup/workout/RelvenPlanImport.java`
- Test `app/test/java/org/runnerup/workout/RelvenPlanImportTest.java`

- [x] Write tests for bounded UTF-8 reading and the one-time, non-activating schedule summary.
- [x] Run tests to confirm expected failures.
- [x] Implement only the bounded reader and summary formatter needed by the assertions.
- [x] Run the focused tests to confirm they pass.

### Task 2: Workout manager entry and accepted import

**Files:**
- Modify `app/res/layout/manage_workouts.xml`
- Modify `app/src/main/org/runnerup/view/ManageWorkoutsActivity.java`
- Modify `common/src/main/res/values/strings.xml` and `common/src/main/res/values-sv/strings.xml`
- Modify `docs/plan-format.md` and `docs/implementation-status.md`

- [x] Add a separate plan import action with the Android document picker.
- [x] Read and validate off the main thread, then show plan preview with Save and Cancel.
- [x] Save only after acceptance through `RelvenPlanStore.save`; show clear duplicate/error/success feedback.
- [x] Verify build, all unit tests, lint, and formatting; request independent review before PR.
- [ ] Commit, open one PR, attach it to this task, wait for hosted checks, then merge before any next phase.
