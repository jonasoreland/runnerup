# Relven AI Plan Kit v2

This offline package gives an external AI the plan contract Relven actually imports, the complete built-in exercise catalog, examples, and—when exported from the phone—the user's current custom exercise collection. Relven does not send an AI request or require a network connection.

## Use the kit

Attach the complete ZIP to the AI conversation. It contains the full prompt, detailed format guide, machine-readable capabilities, canonical schemas, examples, and all 116 bundled bilingual exercises. The phone adds a snapshot of every custom exercise, including its revision, equipment, bilingual instructions, and archived state, when the user copies, shares, or saves the kit.

For a single-message workflow, use **Copy complete AI kit** in Relven. The copied text embeds the same instructions, schemas, built-in catalog, examples, and current custom exercise collection, so the AI does not need a separate attachment.

Tell the AI your goal, current ability and recent training, constraints, availability, desired start date, preferred units, and IANA timezone. This kit does not automatically include workout history, existing plans, or medical/profile data. Add such context yourself if you want the AI to use it.

AI output must be one JSON plan document using `format: "relven.plan"` and `schemaVersion: 1`. Import the JSON file in Relven, review it, save it, and activate it explicitly. The app does not import a ZIP as a plan, activate a plan on import, or contact the AI.

## Package contents

- `INSTRUCTIONS.md` — generation prompt and strict output rules.
- `JSON-FORMAT.md` — field-by-field structure, units, actual importer subset, and examples.
- `capabilities.json` — machine-readable support and exact limits.
- `schemas/plan-v1.schema.json` — new AI plan package schema.
- `schemas/plan-v2.schema.json` — Relven-managed immutable revision schema; do not generate new v2 plans.
- `schemas/workout-v3.schema.json` — running and exercise workout document structure.
- `schemas/strength-session-v1.schema.json` — completed strength-session export containing both a Workout v3 prescription and actual per-set performance. This is a history/export format, not a plan-import format.
- `schemas/activity-v1.schema.json` — compact saved-activity export with measured summary, lap/split results and stored planned lap targets; route coordinates are omitted.
- `schemas/exercise-library-v1.schema.json` — bundled exercise catalog structure.
- `schemas/exercise-collection-v1.schema.json` — user-created custom exercise collection structure.
- `catalog/exercise-library-v1.json` — every bundled exercise with stable IDs, revisions, bilingual names/instructions, category, pattern, muscle groups, and equipment.
- `catalog/exercise-inventory.md` — a readable Swedish/English index of all bundled exercise IDs, categories, patterns, and equipment. The JSON file remains canonical.
- `user-data/custom-exercise-collection-v1.json` — the current device's custom exercise snapshot, appended by the phone at export time, including archived entries.
- `user-data/custom-exercise-inventory.md` — a readable index of the same device-local custom exercises, including IDs, revisions, active/archive status, and equipment.
- `examples/plan-running.json` and `examples/plan-strength.json` — full v1 plan examples that reference bundled exercises.
- `examples/activity.json` — compact example of an exported running activity; unavailable values are omitted.

The bundled inventory is 116 exercises from the offline catalog. The phone also exports all user-created custom exercises currently stored on that device; archived entries remain visible for context but cannot be selected for new plans. These local records remain on-device and are added only when the user explicitly chooses Copy, Share, or Save. The two Markdown inventories are quick indexes; use the canonical JSON documents for exact instructions and exercise snapshots.

## Maintainers

The checked-in Android asset contains stable bundled sources. From the repository root run `python tools/build_ai_plan_kit.py` to rebuild it or `python tools/build_ai_plan_kit.py --check` to detect stale output. Runtime exports append the current local custom collection without changing the deterministic base asset.
