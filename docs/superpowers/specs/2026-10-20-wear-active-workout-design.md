# Wear Active Workout Glance — Design

## Intent and constraints

Implement the first production Wear OS Compose screen in the app's existing active workout flow. It is for a runner checking their current interval and live workout values with a quick glance while moving. Use the verified Stitch Wear screen's black circular canvas, pale blue perimeter, compact phase label, and large readable metrics. Keep the watch screen single-purpose and sparse.

RunnerUp's existing `Tracker`, phone-to-watch data payloads, current metric selection, and pause/lap actions remain authoritative. The screen only presents values already sent by the engine: current phase, current configured time/distance/pace values, and the existing pause countdown. Heart rate remains on its existing Wear page; no value is fabricated or duplicated. Existing pager, pause/resume, lap, completion and sensor permission flows stay in place.

The change replaces only the active run information card rendering inside `RunInfoFragment` with Compose. The fragment retains its lifecycle-bound periodic data reads. A small pure display model maps payload strings to metric rows and exposes paused/live presentation for unit testing. There is no new recording path, network service, sensor access, or stop/discard gesture.

## Verification

Test payload-to-display mapping and missing-value handling. Run Wear unit tests, debug/release builds, lint, Spotless, and release app-ID checks. Device rendering and control interaction remain part of the final manual-test phase; no ADB device is used in this increment.
