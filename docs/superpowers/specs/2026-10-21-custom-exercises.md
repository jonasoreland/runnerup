# Custom Exercises

## Goal

Let a user add and edit personal exercises in the existing offline catalog without changing bundled exercises or breaking plan and history references.

## Constraints

- Keep the bundled exercise dataset immutable. Store personal entries in the app's private files and never require an account or network.
- Give every new entry a stable lowercase UUID. Editing changes its revision while preserving its ID; bundled entries cannot be edited here.
- Keep entries compatible with the catalog's existing categories, muscle-group keys, equipment strings, and bilingual English/Swedish name and instruction fields.
- Validate before saving and use atomic file replacement. A failed write must leave the prior catalog and edits intact.
- Keep workout execution and completed-session formats unchanged. Plan snapshots and historical matches continue to use exercise IDs.
- Do not add media, custom exercise deletion, favorites, collection import/export, or remote exercise data in this increment.
- No physical device or ADB is used.

## Design

Add a strict, bounded custom-exercise JSON store beside the bundled catalog repository. Load the bundled catalog first and merge validated custom records by stable ID, rejecting collisions. Add a localized create/edit form from the Exercises destination. The form collects English and Swedish names and instructions, category, muscle groups, and optional equipment; detail view exposes editing only for custom entries. Persist each change atomically and reload the catalog so search, plan lookup, and detail views use the updated entry.

## Acceptance Criteria

1. A valid custom exercise can be created and appears in catalog search and detail.
2. Editing preserves its UUID, increments revision, and replaces editable fields.
3. Bundled entries cannot be edited, and duplicate IDs or malformed/oversized records are rejected.
4. Invalid form fields and failed atomic writes preserve existing data and expose actionable feedback.
5. Custom entries survive repository reload and work in English and Swedish.
6. Catalog tests, persistence tests, UI state tests, app/Wear build/test/lint/style gates and hosted CI pass.
