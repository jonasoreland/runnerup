# Relven AI Plan Kit v1

This offline kit helps an AI assistant produce a running, strength, or prehab plan that Relven can import. It contains the prompt, capability limits, canonical plan/workout schemas, and valid examples.

## Create a plan

Give your AI assistant the full ZIP, or provide `INSTRUCTIONS.md`, `capabilities.json`, `schemas/plan-v1.schema.json`, and `schemas/workout-v3.schema.json` together. Include your goal, available days, recent running experience, preferred units, and the IANA timezone for the schedule. Ask it to return one JSON object. Validate the result against `schemas/plan-v1.schema.json`, including its relative reference to `schemas/workout-v3.schema.json`, before importing it in Relven.

The app accepts an initial plan as a JSON document. It does not import plan ZIPs. New AI output must use plan schema v1. Schema v2 is included for reference because Relven uses it for app-managed immutable revisions; revision metadata must come from Relven after a predecessor has been saved.

Strength and prehab entries are read-only details backed by pinned exercise snapshots from the bundled no-equipment catalog. The app does not record or execute strength sessions yet. The RunnerUp engine executes running workouts. This kit does not enable server synchronization, automatic plan activation, or workout execution by an AI service.

## Contents

- `INSTRUCTIONS.md` — full generation instructions to copy into an AI assistant.
- `capabilities.json` — machine-readable supported formats and feature limits.
- `schemas/plan-v1.schema.json` and `schemas/plan-v2.schema.json` — plan package and app revision schemas.
- `schemas/workout-v3.schema.json` and `schemas/exercise-library-v1.schema.json` — workout and pinned exercise catalog schemas.
- `examples/plan-running.json` — supported sample package.
- `examples/plan-strength.json` — supported strength and prehab sample package.

## Rebuild

From the repository root, run `python tools/build_ai_plan_kit.py`. Run `python tools/build_ai_plan_kit.py --check` to verify the checked-in Android asset matches these sources and the canonical schemas.
