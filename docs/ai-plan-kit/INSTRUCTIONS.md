# Create a Relven training plan

Return exactly one UTF-8 JSON object for a new plan. Do not wrap it in Markdown or add text outside the JSON.

## Plan the schedule

Use the runner's goal, experience, available days, preferences, and desired start date. Use the runner's IANA timezone for all schedule dates. Make modest assumptions explicit in notes; do not invent health data or completed workouts. Keep recovery appropriate to the stated experience.

## Output format

- Generate an initial package with format: relven.plan and schemaVersion: 1. Use a unique nonempty plan ID, readable name, IANA timezone, nonempty workouts, and nonempty days.
- Each workout uses schemaVersion: 3, a unique ID, and sport running, strength, or prehab. Give every step a unique ID. Supported step kinds are segment, sequence, and repeat; segment phases are warmup, work, recovery, and cooldown.
- Running workouts may use time (seconds), distance (meters), or manual ends and pace, speed, or heart-rate targets. Use sensible positive values and omit heart-rate targets without suitable sensor or zone information.
- Strength and prehab workouts can be created in the app's Plan creator or imported as plan details. Use only active bodyweight exercises from schemas/exercise-library-v1.schema.json and the catalog embedded in the app. Copy the exact exerciseId, catalog libraryRevision, exercise revision as exerciseRevision, English name, and English instruction steps joined with newline characters into snapshot. Copy the equipment list exactly; supported plan authoring requires no equipment. Use an empty images array. Never invent IDs, substitute a name match, or alter catalog snapshot text.
- For each strength/prehab exercise, provide sets from 1 through 100, minRepetitions from 1 through 500, maxRepetitions at least the minimum and no more than 500, and restSeconds from 0 through 3600. Timed work uses an end of type time with seconds from 1 through 86400; repetition work uses a manual end. The app's authoring UI supports either timed work or a repetition range for each exercise. Strength targets and distance ends are unsupported. Repeat counts must remain reasonable.
- Each day has a unique nonempty ID, real YYYY-MM-DD local date, and kind workout or rest. Workout days reference an included workout ID. A rest day has no workout ID. Do not place rest and workout on the same date. Multiple workouts may share a date. Schedule one-time entries only.

## Unsupported features

Do not create equipment-based/custom exercises, exercise media, strength targets, effort targets, composite ends (any / all), heart-rate-below ends, plan ZIPs, sync operations, or schema-v2 revision metadata. Schema v2 represents immutable revisions created by Relven after a plan is saved. Strength and prehab sessions can be guided and recorded offline on the phone; watch-based strength control and editing existing workout definitions are not supported by this format.

## Validate before returning

1. Validate the package against schemas/plan-v1.schema.json; resolve its relative workout reference using the included schemas.
2. Check unique IDs, workout references, real dates, timezone, and rest/workout collisions.
3. Keep the UTF-8 package at or below 4 MiB. The phone validates again before saving and shows errors before any write.

If the request needs an unsupported capability, explain the limitation and ask how to adjust it. Never invent fields or silently change the request.
