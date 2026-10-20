# Offline strength workout details

## Goal

Let a user inspect supported strength and prehab workouts already included in a saved plan, using the workout's pinned exercise snapshots. This is a read-only plan and detail capability; recording/execution remains later work.

## Behavior

- Strictly accept schema-v3 `strength` and `prehab` workouts only when every step, exercise prescription, snapshot, and reference validates.
- Preserve the original workout document within the containing plan snapshot. Show workout name and ordered exercises with snapshot name/instructions, sets, repetition range, rest, and pinned library plus exercise revisions.
- On import, require the exercise ID, both revisions, and English snapshot metadata to match the installed catalog exactly. Once imported, preserve that snapshot in the plan and its revisions; do not rewrite it when catalog content changes.
- Existing running workouts keep their RunnerUp parsing/execution behavior. Strength/prehab documents never become RunnerUp executable files.
- Calendar workout details show the selected workout mode and its exercise prescription; invalid documents fail plan import before any write.
- Keep the view localized (English/Swedish) and usable offline. No images are required in this first read-only slice; asset IDs remain preserved for a later media increment.

## Validation

Reject unknown fields, unsupported sport, invalid schema/revision/UUIDs, duplicate step IDs, invalid nesting/repeats, malformed exercise snapshots/media references, noncanonical catalog snapshots, impossible repetition ranges, equipment not permitted by the current no-equipment catalog policy, and over-limit content. Do not partially import the plan.

## Out of scope

Strength authoring, custom exercises, media downloads, set completion logging, rest timers, plan progression, RunnerUp strength execution, and device testing.

## Verification

Add strict parser/plan-import tests for valid strength/prehab documents and invalid boundaries; verify imported package/revisions preserve snapshots; verify localized read-only detail state. Run full local builds/tests/lint/Spotless and hosted CI. Do not use ADB.
