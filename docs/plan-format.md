# Offline running plan format (draft)

The source of truth is [plan-v1.schema.json](../schemas/plan-v1.schema.json), which references the copied [workout-v3.schema.json](../schemas/workout-v3.schema.json) from the original Relven repository. A [running example](../schemas/examples/plan-running.json) validates against both schemas.

This initial package represents scheduled running workouts and rest days. It uses `format: "relven.plan"`, `schemaVersion: 1`, an IANA timezone, workout documents with stable IDs, and dated entries that reference those IDs. Dates are local calendar dates in the stated timezone. The parser checks duplicate IDs, real dates, timezone IDs, missing workout references, and conflicting rest/workout days. Each embedded workout must also be executable by the current running engine adapter. Unsupported strength or composite workout semantics reject the whole package.

Before parsing, importers enforce a 4 MiB UTF-8 input limit, a maximum JSON nesting depth of 32, and a maximum of 100,000 tokens. These bounds prevent oversized or deeply nested input from building an unbounded in-memory JSON tree.

`RelvenPlanStore` can atomically keep a validated package in app-private storage, with duplicate ID detection. This is a foundation only: the file picker is not yet connected to the plan importer, and there is no calendar or plan activation UI. The schema does not claim strength plan support yet. The original Relven v3 plan-package schema had an incompatible `enum: [1, 2]` and `const: 3` on `workoutSchemaVersion`; it was not copied as the new canonical plan schema.
