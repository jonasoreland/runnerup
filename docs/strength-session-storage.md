# Strength session performance storage

Strength and prehab execution is stored locally in the app-private
`relven-strength-sessions` directory. The single resumable execution is written
to `active.json`; finished executions are immutable files keyed by the execution
ID. Writes replace a complete JSON document atomically. No network or separate
database is required.

## Session file version 3

The root `schemaVersion` is `3`. `workoutJson` is the original validated Workout
v3 document and remains the source of the **planned** prescription. `results`
contains only user-confirmed **actual** set records. A prescription such as
`1–20 repetitions` never supplies an actual result.

Each result is associated with one execution, exercise step, and set number. That
combination is its stable idempotency key; a retry replaces that exact set
instead of adding a duplicate. The exercise index, step ID, and exact name are
checked against the workout snapshot when loaded.

```json
{
  "exerciseIndex": 0,
  "exerciseStepId": "push-ups",
  "exerciseName": "Armhävningar",
  "exerciseCategory": "upper_body",
  "setNumber": 2,
  "status": "COMPLETED",
  "repetitions": 17,
  "durationSeconds": 30,
  "startedAtMillis": 1792830000000,
  "completedAtMillis": 1792830030000,
  "restDurationSeconds": 20,
  "rpe": 8.5,
  "painRating": 0,
  "notes": "Clean repetitions"
}
```

Fields other than the stable exercise/set identity and status are optional.
`exerciseCategory` is an optional category snapshot and does not extend Workout
v3 or mutate its immutable exercise snapshot.
`repetitions` is a bilateral count; unilateral performance uses
`leftRepetitions` and `rightRepetitions` instead. The fast logger requires both
side counts together and does not also store a bilateral total. Zero is a real,
explicitly recorded value. Unknown measurements are omitted, never changed to zero. Timed
sets retain actual duration, which may be shorter than the planned duration;
they can also retain repetitions for a timed maximum-repetition test.

The default logger records only the total count. Per-side counts and optional
RPE, discomfort (0–10), and notes (up to 1,000 characters) are entered from its
advanced-details section. No advanced value is prefilled or recorded without
the user's explicit set confirmation. Confirmed fields are written with that
set's stable identity and are available in the saved session summary. The same
fields survive an offline save and reload through the versioned session codec;
this feature does not change the Workout v3 prescription or private file schema.

The completion summary derives set counts from the workout prescription and its
unique result rows. It separates completed, skipped, explicitly incomplete,
and unrecorded sets. Repetition totals and per-exercise best/average values use
only completed results with a known bilateral count or both side counts. A
one-sided result is not treated as a complete total, and an unrecorded set is
never shown as zero. Recorded timed work durations are summed separately.

`status` is `COMPLETED`, `SKIPPED`, or `INCOMPLETE`. A skipped/incomplete marker
has no performance counts or duration. Replacing a saved result preserves its
stable key. Clearing it leaves an `INCOMPLETE` marker so that progression is not
misreported as a completed result.

Timed work and rest store a monotonic elapsed-realtime deadline and its clock
anchor. Display refreshes derive remaining seconds from that deadline instead
of subtracting one per UI callback. A delayed frame therefore does not extend a
timer, and a process restart in the same boot resumes the remaining interval.
The timer refresh updates in-memory state; the full session file is written at
phase transitions and confirmed result changes rather than every display tick.

When timed maximum-repetition work ends (a timed prescription with a maximum
repetition count greater than one), its exercise/set identity and planned test
duration enter `pendingResults`. A rest countdown advances independently while
the result is unlogged. If rest ends first, the next set can begin and the
pending record remains attached to its original set. Only explicit repetition
confirmation moves it into `results`; no repetition value is inferred from the
prescription. Fixed one-repetition timed holds record their elapsed work
duration automatically and never ask the user for a repetition count.

The user may explicitly skip a pending timed result or the current exercise.
Skipped sets are stored with `status: "SKIPPED"` and no performance values. An
exercise skip marks its remaining sets skipped and advances immediately. A
pending result from the same exercise remains available and blocks final session
completion until the user records or skips it.

Ratings are bounded (RPE and discomfort 0–10), durations are 0–86,400 seconds,
repetition counts are 0–10,000, notes are at most 1,000 characters, and supplied
timestamps must be positive and ordered. These are storage validation bounds,
not recommended workout targets.

## Backward compatibility

Version 1 and version 2 records remain readable. Version 1 results map to
`COMPLETED` with no optional metadata and keep the original 1–500 repetition
validation and planned-duration equality check. Version 2 retains its expanded
result metadata but has no monotonic deadline or pending timed-result state;
its first active timer refresh anchors the remaining saved seconds. When next
written, both use version 3. Workout v3, Plan v1/v2, exercise snapshots, and older JSON
plan exports are unchanged.

Completed filenames use a hash of the execution ID, so repeating a scheduled
plan entry creates a separate session. Repeating the same completion write with
the same execution contents and finish timestamp is idempotent; conflicting
reuse of that execution ID is rejected. The prescribed workout and confirmed
results remain together in each session file for offline recovery. Portable
Relven JSON export/import is a later implementation step and will use its own
versioned public schema rather than exposing this private file format.
