# Edit running workout segments in active plans

## Goal

Let a runner revise the end condition or target of an existing running segment in the active plan while retaining the imported package and every earlier revision.

## Behavior

- In the active Plan calendar, a running workout shows its ordered segment details, including segments inside sequences and repeats. Repeated segments are listed once with their repeat count; editing one updates every execution of that stable segment ID.
- A user can change one existing segment's end condition or target per save. Supported ends are time, distance, and manual. Supported targets are pace, speed, heart rate, or none. Pace uses seconds per kilometer, speed meters per second, heart rate beats per minute, time seconds, and distance meters in the editor.
- The selected segment's phase, ID, name, position, surrounding sequence/repeat structure, and all other workout data remain unchanged.
- Each save creates one immutable schema-v2 plan revision and activates it after the store validates and writes it. The imported root and earlier revisions remain unchanged.
- The Store accepts a revision only when it changes exactly one existing running segment's end or target, or one already-supported plan/schedule/name operation. Strength/prehab definitions cannot be edited through this flow.
- RunnerUp remains responsible for executing the revised running workout. No engine behavior, recording, or activity history changes.

## Validation

- Require an active saved plan and an existing running workout and segment ID.
- Reject unsupported step kinds, missing IDs, unchanged values, invalid end/target types, non-finite or non-positive numeric values, reversed target ranges, time above 86,400 seconds, distance/pace/speed above the Workout v3 numeric limit, and heart-rate bounds outside 30–240 BPM.
- Preserve repeat and sequence nesting. Invalid revisions must not be written or activated.

## Out of scope

Adding, deleting, reordering, or renaming steps; changing phase labels; editing workout names through this editor; editing strength/prehab workouts; modifying the source plan; changing RunnerUp workout files; device testing.

## Verification

Add transformation, parser, revision-lineage, and UI-state tests for nested/repeated segments, end edits, target edits/removal, unsupported workouts, and invalid inputs. Run the full debug/release build, app unit tests, lint, and Spotless. Do not use ADB.
