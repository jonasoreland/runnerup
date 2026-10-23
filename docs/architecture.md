# Architecture

## Inherited modules

| Module | Responsibility |
| --- | --- |
| `app` | Android phone UI, workout execution, GPS/activity recording, persistence, audio, maps, import/export and integrations |
| `wear` | Phone-dependent Wear OS display and remote control |
| `common` | Shared resources, models and wearable communication contracts |
| `hrdevice` | External heart-rate device support |

The current app is primarily Java and XML Views. Java packages remain `org.runnerup` while the installable application ID is the existing Relven Play ID `se.hampusandersson.relven`. Phone and watch must share that ID for existing communication. The phone remains the authoritative session owner. New Kotlin/Compose screens should observe adapters around existing session and storage logic, not create a second workout state machine.

## Engine entry points to preserve

- `app/src/main/org/runnerup/workout/`: workout, step, repeat, trigger, range, builder, serializer and feedback models.
- `app/src/main/org/runnerup/tracker/`: GPS and tracker state; `tracker/filter/` includes persistent GPS logging.
- `app/src/main/org/runnerup/view/RunActivity.java`: current active workout UI and its engine connection.
- `app/src/main/org/runnerup/workout/feedback/`: audio, countdown, coaching, text-to-speech and vibration feedback.
- `app/src/main/org/runnerup/content/`: activity and workout file providers.
- `app/src/main/org/runnerup/export/`: recording export and optional synchronization.
- `wear/src/main/java/org/runnerup/service/`: watch listener and state service.

These are source-level landmarks, not a completed call-graph audit. Time/distance transitions, auto-pause, persistence, GPS sampling, watch messages and export serializers require deeper tests before adapters or refactors.

## Planned boundaries

Keep workout plans, scheduled occurrences and completed activities distinct. Add testable import/validation and planning code at the edge of the existing engine. Stable exercise IDs should resolve through a local catalog. Sensor failure must not terminate a valid phone recording. New UI state should come from the real session owner through lifecycle-aware observation.

Without a Mapbox token, the current build selects osmdroid 6.1.20. Optional Mapbox 11.32.1, Google Play services Wearable 20.0.1 and location 21.4.0, and legacy sync integrations need license/privacy review before release.

The Relven live-workout and activity route screens use MapLibre Native 13.7.1 with OpenFreeMap's Dark style and local GeoJSON overlays. Standard running routes use the Stitch orange accent; metric-colored routes retain their measured palettes. Live camera follow initializes at zoom 16 on the first accepted location, preserves that zoom during later 700 ms animations, and recenters after 12 m of accepted movement; the first accepted location also progressively draws the live route on the isolated emulator. Active map snapshots are coalesced to at most two per second during bursts, while Tracker events and persisted GPS samples remain unthrottled; lifecycle boundaries and restored routes force an immediate render. The inherited RunnerUp detail map continues to use its existing osmdroid/Mapbox wrappers outside the redesigned Relven flows. The route screen reads saved points on IO, closes the DB before display, and keeps a route-only fallback if basemap loading fails. No-map builds exclude the MapLibre implementation.
