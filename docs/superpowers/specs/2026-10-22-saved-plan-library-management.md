# Saved plan library management

## Goal
Make saved plans manageable from Relven's Plan destination while preserving immutable plan packages and their revision history.

## Design
- Group imported plans and app-created revisions by `rootPlanId`, so each logical plan appears once in the library.
- Show the active revision when a group is active; otherwise show its highest saved revision. Expose revision history so users can preview or reactivate a specific saved version.
- Add Saved and Archived library filters. Archiving moves an entire inactive plan group into Archived and retains all plan JSON files. Restoring returns the group to Saved.
- Require confirmation before archiving. The active plan cannot be archived until it is cleared or switched.
- Store archive state as local app metadata keyed by `rootPlanId`; do not add fields to the canonical plan schemas or rewrite revision documents.
- Keep the existing month preview, day/week/month active calendar, create/import flows, and workout engine behavior.

## Boundaries
- Archive and restore work offline and change only the selected root group's archive metadata.
- The selected active snapshot and all prior revisions remain available and unchanged.
- No plan files are permanently deleted.
- Localize new controls and feedback in English and Swedish, and keep screen-reader labels and confirmation behavior.
