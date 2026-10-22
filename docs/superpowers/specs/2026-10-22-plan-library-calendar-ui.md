# In-app plan library and calendar entry

## Goal
Make plan creation, import, saved-plan selection, activation, and the calendar reachable from Relven's Plan tab without opening RunnerUp's standalone manager.

## Design
Keep the existing revision-aware `PlanCalendarScreen` for an active plan and add a Relven-styled plan library for saved plan packages. The library exposes saved plans, active status, activation/clear actions, a JSON plan import picker with validation preview and explicit save, and a Create with AI action that copies the bundled Plan Kit instructions. When no plan is active, show a real Monday-first current-month calendar grid with an empty-schedule message and the create/import/library actions. From an active calendar, a clear Manage plans action opens the library. All plan storage remains local and uses `RelvenPlanStore`; saved plan documents and RunnerUp remain authoritative.

## Boundaries
- Users can create a basic weekly plan in-app or generate a richer plan with the bundled AI Plan Kit and import its validated JSON. Both paths use the canonical plan v1 schema; do not add a second plan schema or mutate stored plan JSON in place.
- Activating, clearing, importing, and calendar revisions use existing store APIs.
- Physical-device ADB remains prohibited until all product phases are complete.
