# Active plan name editing implementation plan

## Tasks

1. Add failing revision tests for a trimmed name revision, source immutability, no-op/empty/too-long names, and sequential revision lineage.
2. Implement a plan rename transform and extend lineage validation to accept exactly one name-only edit while rejecting mixed schedule/content edits.
3. Add a store operation requiring the current saved active plan, appending and activating the next immutable revision.
4. Add localized calendar rename UI with inline validation, cancel/save states, and accessible progress/error/success feedback.
5. Add store/UI regression coverage and update plan-format, feature-parity, and implementation-status documentation.
6. Run focused RED/GREEN tests, full debug/release build, complete app unit tests, lint, formatting, and diff checks. Open and merge only this PR before another phase.

## Test commands

- `:app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanRevisionTest`
- `:app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanStoreTest`
- `:app:testLatestDebugUnitTest --tests org.runnerup.relven.ui.PlanCalendarStateTest`
- `:app:assembleLatestDebug :app:assembleLatestRelease :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck`

## Execution notes

Write each behavior test first and record expected RED and GREEN output. Do not use ADB or install the app on a physical device in this phase.
