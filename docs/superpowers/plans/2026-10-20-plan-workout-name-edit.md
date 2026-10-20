# Plan: edit active plan workout names

## Files

- `app/src/main/org/runnerup/workout/RelvenPlanRevision.java`: pure immutable revision transform.
- `app/src/main/org/runnerup/workout/RelvenPlanStore.java`: narrowly validate a single workout-name change in revision lineage and persist/activate it.
- `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarScreen.kt`: selected workout edit action and dialog.
- `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`: save handler.
- `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarState.kt`: name validation helper if needed.
- `app/test/java/org/runnerup/workout/RelvenPlanRevisionTest.java`, `RelvenPlanStoreTest.java`, and UI state tests: regression coverage.
- English/Swedish common string resources and implementation/parity documentation.

## Tasks

1. [x] Add failing tests for revision-only workout-name changes, unknown IDs, blank/unchanged names, preserved steps and schedule, and store acceptance/rejection of exact changes.
2. [x] Implement the immutable revision transform and store lineage rule; focused Java/UI state tests pass.
3. [x] Add localized calendar edit-name interaction, validation, saving state, and Activity store handler. Input has a localized label, inline validation, specific button text, accessibility label, and existing polite live-region revision feedback; physical rendering remains unverified by instruction.
4. [x] Update feature parity and implementation status; full Latest debug/release build, full app tests, debug lint (no new findings), and Spotless pass. `git diff --check` and application ID check remain before commit; no ADB used.
5. [ ] Commit, push, open one PR, wait for hosted CI, merge, verify empty PR queue, and record the milestone.
