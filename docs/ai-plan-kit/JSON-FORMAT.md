# Relven plan and workout JSON format

This guide explains the Android import contract. The JSON Schemas in `schemas/` are authoritative for exact property definitions. Importer support is narrower than the shapes allowed by `workout-v3.schema.json`; the supported subset is stated here.

## Plan document

An AI-created plan is one JSON object:

```json
{
  "format": "relven.plan",
  "schemaVersion": 1,
  "id": "unique-plan-id",
  "name": "Kort plan name",
  "timezone": "Europe/Stockholm",
  "workouts": [],
  "days": []
}
```

`id` and `name` are nonblank strings up to 160 characters. `timezone` must be a recognized IANA timezone. A plan needs 1–100 workouts and 1–2,000 day entries. Each day has `id`, local `date` in exact `YYYY-MM-DD` form, and `kind` (`workout` or `rest`). Workout days also need a `workoutId` matching one included workout. Optional `notes` are strings up to 1,000 characters. Multiple workouts can share a date. Rest dates cannot repeat or overlap a workout date. Entries are one-time; recurrence rules are unsupported.

| Plan field | Required | Meaning |
|---|---|---|
| `format` | Yes | Exact string `relven.plan`. |
| `schemaVersion` | Yes | Integer `1` for a new AI-authored plan. |
| `id` | Yes | Unique plan identifier; any nonblank string up to 160 characters. |
| `name` | Yes | Human-readable plan name; any nonblank string up to 160 characters. |
| `timezone` | Yes | Recognized IANA timezone used to interpret local schedule dates, such as `Europe/Stockholm`. |
| `workouts` | Yes | 1–100 full Workout v3 documents. |
| `days` | Yes | 1–2,000 one-time workout/rest calendar entries. |

Each `days[]` entry requires `id`, `date`, and `kind`. `kind: "workout"` requires `workoutId`; `kind: "rest"` forbids `workoutId`. `notes` is optional. Every object rejects unrecognized properties. Dates are calendar dates, not timestamps; include the timezone once at the plan root.

Use unique nonblank IDs for the plan, each workout, each day, and every step in its workout. IDs are unique within each entity list; UUIDs are recommended. The package is JSON rather than a ZIP and is limited to 4 MiB UTF-8, 32 nested levels, and 100,000 JSON tokens.

## Workout document

Every workout is schema version 3:

```json
{
  "schemaVersion": 3,
  "id": "unique-workout-id",
  "name": "Lugn distans",
  "sport": "running",
  "steps": []
}
```

`sport` is `running`, `strength`, or `prehab`. Optional `warmupExempt` and `cooldownExempt` must be omitted or false; true is currently rejected. Workouts allow 1–256 parsed step nodes, at most 16 nested levels, repeat count 1–100, and no more than 10,000 expanded steps/exercises.

| Workout field | Required | Meaning |
|---|---|---|
| `schemaVersion` | Yes | Integer `3`. |
| `id` | Yes | Unique workout identifier, nonblank and at most 160 characters. |
| `name` | Yes | Nonblank workout label, at most 160 characters. |
| `sport` | Yes | `running`, `strength`, or `prehab`. |
| `steps` | Yes | Ordered nonempty array of step nodes (1–256 top-level entries). |
| `warmupExempt`, `cooldownExempt` | No | Omit or set `false`; `true` is not importable. |

Each step is one of:

- `segment`: a phase with an end condition, optional target, and (for strength/prehab) an exercise prescription.
- `sequence`: an ordered, nonempty `steps` list.
- `repeat`: a nonempty `steps` list and integer `count` 1–100.

Segment phases: `warmup`, `work`, `recovery`, `cooldown`. Every step needs a unique `id`; optional names are nonblank strings up to 160 characters.

All step objects require `id` and `kind`; IDs must be unique throughout that workout's tree. `name` is optional. Objects reject fields outside their defined shape:

| `kind` | Additional required fields | Purpose |
|---|---|---|
| `segment` | `phase`, `end` | One running phase or one exercise prescription. Optional `target`; strength/prehab requires `exercise`. |
| `sequence` | nonempty `steps` | Run child steps once in the listed order. |
| `repeat` | `count` (1–100), nonempty `steps` | Repeat the child sequence the requested number of times. |

### Supported running segments

The running engine accepts `end` as:

| Type | Required value | Unit |
|---|---|---|
| `time` | `seconds` > 0 | seconds |
| `distance` | `meters` > 0 | meters |
| `manual` | none | user advances the step |

An optional `target` can be null or one of:

| Type | Range fields | Canonical unit |
|---|---|---|
| `pace` | `minSecondsPerKm`, `maxSecondsPerKm` | seconds per kilometer |
| `speed` | `minMetersPerSecond`, `maxMetersPerSecond` | meters per second |
| `heart_rate` | `minBpm`, `maxBpm` | beats per minute; each bound 30–240 |

Every range must have minimum ≤ maximum. Time/distance/pace/speed values must be finite, positive, and at most 1,000,000,000. Heart-rate targets are optional; only use them when an appropriate target and sensor are known. The importer converts these documents to the existing running engine. It rejects `effort` targets, strength exercise objects in running workouts, `any`/`all` composite ends, and `heart_rate_below` recovery even though some are present in the broader schema.

### Supported strength and prehab segments

Relven can author/import exercise steps as plan details and guide/record those sessions on the phone. A strength/prehab segment requires an exercise prescription. `end` is `manual` for repetition work or `time` with integer `seconds` from 1–86,400. `target` must be omitted or null. Distance ends and non-null targets are rejected.

Each exercise prescription has this shape:

```json
{
  "exerciseId": "exact-installed-exercise-uuid",
  "libraryRevision": 2,
  "exerciseRevision": 1,
  "snapshot": {
    "name": "Exact English catalog name",
    "instructions": "Exact English step 1\nExact English step 2",
    "equipment": [],
    "images": []
  },
  "sets": 3,
  "minRepetitions": 8,
  "maxRepetitions": 12,
  "restSeconds": 60
}
```

`exerciseId` must exist in the currently installed bundled or custom catalog. `libraryRevision` is the exact bundled library revision (`2` in this kit); `exerciseRevision` is the exact revision of that exercise. The app checks the exact English snapshot and equipment. Use the English instruction array joined with literal newline characters. Generated plans must use no equipment (`[]`) and no images (`[]`). Bounds: sets 1–100; repetitions 1–500 with max ≥ min; rest 0–3,600 seconds. Use only active exercises with empty equipment. Archived custom exercises are included for inventory context but must not be newly prescribed.

## Recorded strength-session performance

Guided phone sessions can retain actual set results offline and export them separately as `format: "relven.strength-session"`, `schemaVersion: 1`. See `schemas/strength-session-v1.schema.json` for the exact portable format. The root contains `prescription` (the immutable Workout v3 document) and `performance` (session identifiers, completion time/state, and one explicit status row for every prescribed set). Set rows retain actual repetitions or hold duration, optional left/right counts, rest, timestamps, RPE, discomfort and notes. Missing values stay absent; an explicit zero remains zero. `completion` is `complete` only when all set rows are completed; skipped/incomplete sets make it `partial`.

This performance document is for exporting or importing workout history. It is not accepted by the plan importer and must not be returned when generating a new training plan. Plan packages remain `relven.plan` v1 with Workout v3 prescriptions only. A prescription never implies that the user completed its target.

## Exercise catalog documents

The bundled library uses `format: "relven.exercise-library"`, `schemaVersion: 1`, `libraryRevision`, and an `exercises` array (maximum 256). Each entry has a stable lowercase UUID, own `revision`, localized English/Swedish `name` and `instructions` arrays, category, `movementPattern`, `muscleGroups`, and `equipment`. Categories are `lower_body`, `upper_body`, `core`, and `warmup_mobility`; valid muscle groups are defined in `schemas/exercise-library-v1.schema.json`.

The current bundled catalog contains 116 records at library revision 2. `catalog/exercise-inventory.md` indexes their Swedish and English names, IDs, categories, movement patterns, and equipment; the JSON contains full records and instructions. Custom records are separate and use `format: "relven.exercise-collection"`, `schemaVersion: 1`, and an `exercises` array (maximum 200). Each record has archive state, ID/revision, English and Swedish names/instructions, category, movement pattern, muscle groups, and equipment. Phone copy/share/save exports contain the current device's complete custom collection, including archived records, plus `user-data/custom-exercise-inventory.md` as a readable index. The custom collection is limited to 1 MiB. No exercise media or external URLs are included.

Exercise identity comes from the catalog, not its display name. Never invent, rename, translate, or substitute an exercise ID or snapshot. A workout's snapshot remains fixed even if the catalog changes later.

## Plan revisions and lifecycle

Generate only a new `schemaVersion: 1` plan. Schema v2 contains Relven-managed revision lineage (`revision.rootPlanId`, `revision.number`, `revision.previousRevisionId`) and is accepted only when it extends an already saved plan in the expected sequence. Do not guess or generate v2 metadata. Import previews and validates JSON; saving does not automatically activate a plan. The user activates it separately. Plans are stored locally; ZIP import and server synchronization are not provided.

The in-app Plan creator can create a 1–16-week plan and select running, strength, or prehab workouts. Imported workout definitions are read-only; limited plan name/schedule and running-segment changes create immutable revisions. Strength/prehab details and guided execution are phone-based. Watch-based strength controls and AI-generated workout execution are not part of this interchange format.

## Valid examples

`examples/plan-running.json` and `examples/plan-strength.json` are full schema-v1 plan packages. The strength example uses exact IDs and English snapshots from `catalog/exercise-library-v1.json`; examples explain structure and are not prescriptions for every athlete.

## Strict JSON and version rules

Use only fields defined by the schemas. Unknown fields are rejected, including plausible additions such as weekdays, recurrence rules, arbitrary workout notes, or custom targets. Do not place workout prescriptions directly in calendar days: `days[].workoutId` references a workout in the root `workouts` array. The workout document contains the ordered execution steps. Generate plan v1 and Workout v3; plan v2 is app-managed revision metadata, not a generation format.
