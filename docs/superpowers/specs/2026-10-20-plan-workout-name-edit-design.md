# Active plan workout name editing

## Goal

Let a user change a running workout's display name in the active plan while preserving the imported package and every earlier plan revision.

## Behavior

- A selected workout entry in the Plan calendar exposes an edit-name action.
- The dialog starts with the current workout name. A trimmed, non-empty name of at most 160 characters can be saved; unchanged names cannot be submitted.
- Saving updates only the `name` field for that workout ID in a new schema-v2 plan revision. All schedule references keep the same workout ID, so every occurrence uses the new name.
- Revision metadata follows the saved lineage tip and the revision is activated only after it is saved.
- The imported root package, previous revisions, workout steps, targets, exercise snapshots, sessions, timezone, and RunnerUp workout files remain unchanged.
- English and Swedish labels and validation feedback follow existing calendar patterns. Save status, conflict, and failure states use the current revision status region.

## Validation and compatibility

- Revision creation rejects unknown IDs, invalid/unchanged names, and invalid revision metadata.
- Store lineage validation accepts a revision only when the sole content difference is one workout name; every other existing invariant remains in force.
- Existing schedule and plan-name edit revisions retain their current rules.
- No plan schema version change is required because the serialized workout shape is unchanged.

## Out of scope

Editing running steps/targets, strength workout authoring, plan-package mutation, editing the RunnerUp workout library, plan-linked execution, and device testing.

## Verification

Add revision transformation and store-lineage tests, plus state/UI action and localized resource checks. Run focused tests, full app tests, Latest debug/release builds, lint, Spotless, and hosted Android CI. Do not use ADB.
