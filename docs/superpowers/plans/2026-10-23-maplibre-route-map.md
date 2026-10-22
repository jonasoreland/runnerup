# MapLibre route screen implementation plan

1. Add pure route sample, metric range, color, and GeoJSON helpers; establish test-first coverage for coordinates, lap/pause boundaries, pace, and missing data.
2. Read activity locations into the route model on the IO dispatcher, then close the database immediately.
3. Replace the Relven route screen's RunnerUp map wrapper with a lifecycle-bound MapLibre view and visible metric selector/legend.
4. Load OpenFreeMap Liberty style, fit the camera to the route, apply route GeoJSON, show attribution, and preserve a route-only fallback on tile/style failure.
5. Preserve the inherited RunnerUp detail maps and no-map build source set.
6. Update feature parity, architecture, and implementation status; run tests, release lint, Spotless, debug/release builds, no-map build, and isolated emulator review.
7. Request independent code review, create one PR, wait for CI, merge it, sync master, and rebuild the debug APK from master.
