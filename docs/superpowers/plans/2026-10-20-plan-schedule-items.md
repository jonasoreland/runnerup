# Plan schedule item editing implementation plan

## Tasks

1. Add failing tests for schema-v2 add/remove revision operations, exact-one-entry changes, duplicate IDs, rest/workout conflicts, invalid inputs, and preservation of the source package.
2. Implement add/remove transformations in `RelvenPlanRevision` and extend saved-lineage validation to accept exactly one added or deleted day while keeping plan metadata/workouts and all other days unchanged.
3. Add store operations that require the supplied source to still be the active plan, choose the next root revision number, save the immutable revision, and activate it.
4. Add localized calendar actions to create a workout/rest entry on the selected date and confirm removal of an existing entry. Report saving, conflict, failure, and success accessibly in English and Swedish.
5. Add regression tests for calendar action constraints and stored active-plan results; update feature parity and implementation status.
6. Run focused RED/GREEN tests, then full debug/release build, app tests, lint, Java/Kotlin formatting, diff checks, and hosted CI. Open and merge only this PR before starting another phase.

## Test commands

- `:app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanRevisionTest`
- `:app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanStoreTest`
- `:app:testLatestDebugUnitTest --tests org.runnerup.relven.ui.PlanCalendarStateTest`
- `:app:assembleLatestDebug :app:assembleLatestRelease :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck`

## Execution notes

Write each behavior test before its implementation and record the expected RED result, then its GREEN result. Do not use ADB or install the app on a physical device in this phase.
