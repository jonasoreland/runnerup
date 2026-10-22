# Relven activity route map

## Goal

Render saved activity routes in Relven's history UI with a native MapLibre map, OpenFreeMap tiles, and selectable route coloring by pace, heart rate, speed, or elevation.

## Boundaries

- The Relven route screen reads the existing RunnerUp activity and location database; it does not change recording, import, or stored activity data.
- The route loader runs on IO and closes the database before rendering begins.
- Route segments stop at lap and pause/resume boundaries. Pace derives from active elapsed time and cumulative distance. Metrics with fewer than two values are unavailable; individual missing values use gray.
- OpenFreeMap style/tiles are loaded without an application API key. Visible attribution includes OpenStreetMap, OpenMapTiles, and OpenFreeMap.
- A map-load failure leaves a local route-only rendering available.
- RunnerUp's existing `DetailActivity` map wrappers remain available and are not migrated in this increment.
- No offline map tile download is included.
- Release application ID stays `se.hampusandersson.relven`.

## Verification

- Unit tests cover coordinate order, lap boundaries, pace derivation, missing metric availability, and database route loading.
- Build the latest debug APK and the no-map release variant; run unit tests, release lint, and Spotless.
- Inspect the rendered route screen on an isolated emulator; do not use the user's physical device before all phases are complete.
