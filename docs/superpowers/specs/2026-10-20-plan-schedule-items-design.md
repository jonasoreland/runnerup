# Plan schedule item editing design

## Goal

Let a runner add a planned workout or rest day to a selected calendar date and remove one existing scheduled entry, while preserving imported plan snapshots and the saved revision lineage.

## Behavior

- Each add or delete creates and activates one complete schema-v2 plan revision whose predecessor is the current active revision.
- Add workout selects a workout already included in the plan and creates a unique schedule-entry ID. Multiple workouts may share a date; a rest day may not share a date with a workout or another rest entry.
- Add rest creates one unique rest entry only when the selected date has no scheduled workouts or rest entry.
- Delete removes exactly one entry by its ID. The final schedule entry cannot be removed because valid plan packages require at least one day.
- A revision keeps plan metadata, timezone, workout definitions, and every unaffected schedule entry unchanged. Invalid dates, duplicate IDs, stale active revisions, and schema-invalid results are rejected without activating a revision.
- The calendar exposes add actions on the selected day and a delete action per entry, with a confirmation step. Saving, conflict, failure, and success feedback follow the existing localized revision flow.
- RunnerUp remains responsible for workout execution and history. This change only edits future schedule metadata.

## Scope and non-goals

This increment does not edit workout definitions, notes, goals, or plan metadata; it does not implement strength sessions or plan-linked execution. It does not alter schema v1 or the existing reschedule behavior.

## Verification

Unit tests cover revision construction, added/deleted single-entry invariants, collision and last-entry rejection, stale and invalid lineage, store save/activation behavior, and calendar UI state. Full local debug/release builds, app unit tests, lint, and style checks must pass before one PR is opened. Physical-device ADB remains prohibited until all product phases are complete.
