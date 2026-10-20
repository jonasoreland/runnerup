# Create a Relven running plan

Return exactly one UTF-8 JSON object for a new running plan. Do not wrap it in Markdown or add text outside the JSON.

## Ask for or use the runner's details

Use the runner's goal, recent running experience, available training days, preferred units, and desired start date. Use the runner's IANA timezone for every schedule date. If a detail is missing, make a modest, clearly named assumption in the plan notes; do not invent personal health data or completed workouts. Keep rest and recovery appropriate to the stated experience and goal.

## Output format

- Create an initial package using `format: "relven.plan"` and `schemaVersion: 1`. Use a unique nonempty plan ID, a readable name, an IANA timezone, a nonempty workouts array, and a nonempty days array.
- Each workout uses `schemaVersion: 3`, a unique ID, `sport: "running"`, and one or more steps. Use unique IDs for every workout step. Supported step kinds are `segment`, `sequence`, and `repeat`; segment phases are `warmup`, `work`, `recovery`, and `cooldown`.
- Use only time ends (`seconds`), distance ends (`meters`), or manual ends. Supported segment targets are pace (`minSecondsPerKm`, `maxSecondsPerKm`), speed (`minMetersPerSecond`, `maxMetersPerSecond`), and heart rate (`minBpm`, `maxBpm`). Choose sensible positive values and make each minimum no greater than its maximum. Omit heart-rate targets if the runner does not have a suitable sensor or zone information.
- Each day has a unique nonempty ID, a real `YYYY-MM-DD` local date, and kind `workout` or `rest`. A workout day must reference an included workout ID. A rest day has no workout ID. Do not place a rest day on the same date as any workout. Multiple workouts may share a date.
- Schedule one-time dated entries. Do not imply recurrence or invent completed sessions. Keep the plan within the runner's stated dates and available days.

## Unsupported features

Do not create strength or prehab workouts, exercise snapshots, effort targets, composite ends (`any` / `all`), heart-rate-below ends, plan ZIPs, sync operations, or schema-v2 revision metadata. Relven rejects these or requires app-owned saved lineage. Schema v2 represents immutable revisions created by Relven; an AI must generate a new plan as schema v1.

## Validate before returning

1. Validate the complete JSON against `schemas/plan-v1.schema.json`; resolve its relative reference to `schemas/workout-v3.schema.json` from the included schemas directory.
2. Check semantic constraints: all IDs are unique in their applicable collections, every workout reference exists, dates and timezone are valid, and no rest/workout date collision exists.
3. Keep the UTF-8 JSON package at or below 4 MiB. The phone importer performs its own schema and semantic validation before saving.

If the user requests a capability that cannot be represented by the supported schema, explain that limitation and ask how to adjust the request before generating a plan. Never make up a field or silently change the request.
