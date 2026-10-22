# In-app plan creation

## Goal

Let a user create a useful running plan entirely inside Relven, save it to the existing local plan library, activate it, and immediately see its sessions on the Plan calendar.

## Design

Add a localized Compose plan builder reachable from the Plan library. The first builder supports a plan name, start date, duration of 1–16 weeks, selected training weekdays, and a workout type for each selected weekday (easy, interval, or long run). The builder creates valid schema-v1 plan JSON with stable generated workout/session IDs, one immutable running workout document per selected type, and dated sessions repeated on the chosen weekdays. The save action validates through `RelvenPlanStore.parse`, saves, activates, refreshes Today and the plan library, and opens the existing day/week/month calendar. The exact generated package is persisted as a pending operation before saving starts; if save or activation is interrupted, retry safely saves only if the same ID and content are not already stored, then activates that package. The Plan library exposes pending retry and prevents starting another in-app plan until recovery completes.

The empty library calendar remains a real Monday-first calendar grid and gains a clear “Create plan” entry point. The current AI Plan Kit and JSON import remain available as advanced alternatives. Existing imported plans and schema-v2 revisions are never rewritten. RunnerUp remains the workout execution and activity-history authority.

## Constraints

- Preserve Play application ID `se.hampusandersson.relven`.
- Store plans only through `RelvenPlanStore`; use canonical plan/workout schemas.
- Support English and Swedish UI, including validation and save errors.
- Do not use the physical ADB device before all product phases are ready for manual testing.
- Keep one PR open at a time in the existing worktree.

## Out of scope

Strength workout authoring, editing existing workout definitions, recurring availability preferences, cloud sync, and replacing the existing calendar/revision flow.

## Verification

Unit tests validate generated JSON for each workout type, selected weekdays, week boundaries, leap/month/year/DST transitions, invalid input, and exact-ID/source save/reuse/conflict decisions. Run the full app test suite, Latest debug build, release lint, and Spotless; hosted Android/F-Droid CI must pass before merge. No ADB or emulator.
