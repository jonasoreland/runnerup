# Create a Relven training plan

You are generating a new plan for the Relven Android app. Read every included file, especially `JSON-FORMAT.md`, `capabilities.json`, the schemas, `catalog/exercise-library-v1.json`, and `user-data/custom-exercise-collection-v1.json`. These files are the source of truth. Do not infer undocumented app behavior from schema features alone.

## Required output

Return exactly one UTF-8 JSON object for a new plan. Do not wrap it in Markdown or add explanation outside the JSON. The root must use `format: "relven.plan"` and `schemaVersion: 1`. Never generate app-managed schema-v2 metadata. Use unique nonempty IDs (UUIDs recommended) for the plan, each workout, each day, and each step within its workout.

## Use the runner's context

Use the goal, ability, recent training, constraints, availability, desired start date, units, and timezone the user actually supplied. Use an IANA timezone and local `YYYY-MM-DD` dates. Do not invent completed sessions, medical facts, sensor data, or performance benchmarks. Make necessary assumptions explicit in day notes. Schedule sensible progression and recovery for the stated experience. Use short Swedish names when the user prefers Swedish; exercise snapshots must remain exact English catalog text.

## Plan and schedule rules

- `workouts` contains 1–100 complete Workout v3 documents; `days` contains 1–2,000 one-time calendar entries.
- Each day has a unique ID, a real date, and `kind` equal to `workout` or `rest`. A workout day must reference an included `workoutId`. A rest day must omit `workoutId`. Multiple workouts may share a date. No rest day may share a date with a workout; duplicate rest dates are rejected.
- IDs must be unique within each entity list and step tree. Every workout reference must resolve exactly.
- The package is JSON, not a ZIP. The UTF-8 source is limited to 4 MiB, nesting to 32 levels, and 100,000 JSON tokens.

## Workout v3 rules

Each workout has `schemaVersion: 3`, `id`, `name`, `sport` (`running`, `strength`, or `prehab`), and nonempty `steps`. Step `kind` is `segment`, `sequence`, or `repeat`; segment `phase` is `warmup`, `work`, `recovery`, or `cooldown`. See `JSON-FORMAT.md` and `schemas/workout-v3.schema.json` for exact field names and types.

### Running

Relven executes the supported running subset through its existing running engine. Use segment `end` types `time` (positive seconds), `distance` (positive meters), or `manual`. Optional running targets may be `pace` in seconds/km, `speed` in m/s, or `heart_rate` in bpm, each with ordered `min` and `max` fields. Sequences and repeats are supported; repeat count is 1–100. Include appropriate explicit warmup and cooldown segments unless the user intentionally requests an exemption; the importer currently rejects `warmupExempt: true` and `cooldownExempt: true`.

### Strength and prehab

The phone Plan creator supports strength/prehab details, and guided sessions run on the phone. Select only an exercise present in the kit's current exercise data and not marked archived. For plan creation/import, use only entries whose `equipment` is exactly `[]`. Never invent an exercise or approximate an ID by matching names.

Copy the exact exercise `exerciseId`, catalog `libraryRevision`, and its own `revision` as `exerciseRevision`. Copy the exact English name and English instruction steps joined with newline characters into `snapshot.name` and `snapshot.instructions`. Copy `equipment` exactly and set `images` to `[]`. The app checks the ID, revisions, English snapshot, and equipment against its installed catalog.

Each exercise prescription requires `sets` 1–100, `minRepetitions` 1–500, `maxRepetitions` at least the minimum and at most 500, and `restSeconds` 0–3,600. Use an exercise segment `end` of `manual` for repetition work or `time` with integer seconds 1–86,400 for timed work. The app's Plan creator exposes repetitions or timed work, sets, and rest. Strength steps do not support non-null targets, distance ends, exercise media, or equipment. Sequences and repeats are structurally supported; keep them simple and manageable.

Custom exercises with `archived: true` are included to identify the user's full collection, but are not available for new exercise selection. Custom entries with nonempty equipment are listed for context and must not be prescribed in this no-equipment plan workflow. A custom collection may be empty.

## Schema versus importer

Schemas define structure, but the app accepts a narrower executable subset. Do not output running `effort` targets, `any`/`all` composite ends, `heart_rate_below` recovery, or true warmup/cooldown exemption flags. Do not output strength targets, distance ends, nonempty equipment, images, or unknown exercises. Plan v2 is only a Relven-generated immutable revision with a saved predecessor; a new AI plan is v1.

## Validate before returning

1. Validate the root against `schemas/plan-v1.schema.json`, resolving its relative workout reference to `schemas/workout-v3.schema.json`.
2. Check the actual importer-supported subset above, not only schema validity.
3. Confirm every exercise ID, revision, equipment list, English name, and instruction snapshot matches an active no-equipment exercise included in this kit.
4. Confirm IDs and references, schedule dates/timezone, rest/workout collisions, target ranges, repeat counts, exercise prescription ranges, and resource limits.

If a request requires an unsupported feature, state the limitation and produce a supported alternative only if the user allows it. Never invent fields or silently change the request.
