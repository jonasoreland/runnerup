# Active plan name editing design

## Goal

Let a runner rename the currently active plan from its calendar while retaining the same immutable revision and activation rules used by schedule edits.

## Behavior

- Renaming creates and activates a complete schema-v2 plan revision whose predecessor is the active revision.
- The name is trimmed, nonempty, no longer than 160 characters, and must differ from the current name.
- Plan timezone, workouts, schedule, and all earlier snapshots remain unchanged.
- Store validation accepts the name change only as the sole content change in a revision. A rename cannot be combined with a schedule edit or workout-definition change.
- The calendar exposes a localized rename action and dialog with inline validity feedback. Save, cancel, conflict, failure, and success follow the existing accessible revision feedback flow.
- RunnerUp remains authoritative for workout execution and history.

## Scope and non-goals

This change edits the plan name only. It does not edit workout definitions, dates, notes, timezone, recurrence, or strength content.

## Verification

Tests cover revision creation, invalid and no-op names, store activation and source preservation, rejection of mixed edits, and UI name validation. Run the full local debug/release build, app unit suite, lint, and formatting before a single PR. Physical ADB remains prohibited until all phases are complete.
