# Relven running experience redesign

## Intent

Replace RunnerUp's visible active-run and post-run presentation with a coherent Relven experience while keeping RunnerUp's `Tracker`, workout engine, foreground service, database, sensor providers, audio coaching, notification controls, and Wear bridge authoritative. The experience must show a live route without making route tiles or the UI responsible for recording.

## Evidence and current implementation

- The active workout is `RunActivity` (`app/src/main/org/runnerup/view/RunActivity.java`). It binds to the foreground `Tracker` service, ticks the existing `Workout`, and hosts Compose metrics, ordered steps, countdown, and controls inside the inherited `run` layout. It has no live map.
- The stopped-but-unsaved and saved legacy views share `DetailActivity` (`app/src/main/org/runnerup/view/DetailActivity.java`). That Activity still presents the old tab strip, RunnerUp map/graph wrappers, and large Resume/Save/Discard controls. Save, discard, and resume results are returned to `RunActivity`, which calls `Tracker.completeActivity`.
- Relven History already has `ActivityDetailScreen` and `ActivityDetailState` for saved summary, notes, and ordered laps. It opens a separate `ActivityMapActivity` for route rendering. This is not yet the same experience as the unsaved review.
- `Tracker` remains the phone session authority and owns pause/resume/stop, route distance/time accumulation, sensor components, notification state, and `PersistentGpsLoggerListener` writes to RunnerUp's activity/location tables. It currently accumulates each raw GPS displacement without a stale-fix or accuracy-aware movement gate; a low-pass speed filter does not prevent raw displacement from increasing distance.
- MapLibre is already available in the Play source set. `RelvenMapHost` renders saved routes over OpenFreeMap Liberty, adds start/finish markers and recenter, and falls back to a local route drawing when style/tile loading fails. The map is independent of the Tracker. Tiles require network unless already cached; GPS recording and route persistence must continue without them. The no-map source set must continue to build.
- Recorded activity notes, laps, summaries, location coordinates, timestamps, and available sensor values already live in RunnerUp's existing SQLite schema. Existing routes and calculations must remain evidence-based; absent data remains unavailable.

## Visual source of truth

The authoritative current Stitch project is **Relven Fitness App UI Design**, project `15389433782836702529`, design system **Kinetic Obsidian**. Use its dark slate surfaces (`#0D141E`, `#151C26`, `#19202A`), electric blue for route/navigation state, sunrise orange for active exertion and primary actions, Space Grotesk for metrics/headlines, Inter for body copy, 4/8 px spacing rhythm, and the project's restrained pill/large-surface geometry.

Exact Stitch references inspected:

- `Relven Android - Running Mode & GPS Route`, screen `df1e58957bc2454a90fee8829b877f5f` — current dark full-map running composition with a compact floating workout summary.
- `Relven Android - Löppass Detaljer`, screen `2b55738fd89c450f8d191677b633b7b2` — current typography, dark surface, spacing, workout hierarchy, and phase display.
- The earlier `Relven Design System` project `1518842303785767214`, screen `Relven — Aktivt Löppass` (`c8418a3dcd2444a4a3f68492640e9dbe`) and `Relven — Passanalys` (`9151f770d81e48af83c84ae1fbb0fbc2`) were also inspected. Their richer recorded-analysis content hierarchy is a secondary reference only; their older colors, navigation, and component styling are not carried forward.

The current Stitch project does not contain a finalized post-workout summary/detail screen. That screen is a design extrapolation using Kinetic Obsidian, the current run screen's map-and-summary composition, and the older analysis reference's evidence-rich sections. It must not be reported as an exact Stitch match.

## Goals

- Keep one workout state, timer, GPS provider, and persistence path: the existing phone `Tracker` service.
- Put a usable live MapLibre route behind/alongside glanceable active metrics, controls, current GPS quality, and structured workout phase information. Keep camera follow until the user pans; provide recenter; preserve map instance/camera when expanding or changing metric presentation.
- Preserve pause, resume, lap, stop-to-review, save, discard, notifications, voice coaching, heart-rate sources, and Wear controls through the existing callbacks and engine.
- Show a complete accepted route and only supported summary, chart, split, interval, heart-rate, elevation, cadence, and pace values after a run. Keep missing or unreliable data absent or explicitly unavailable.
- Reuse one activity review/detail presentation for stopped-unsaved and finalized saved activity states. Only an unfinished activity can resume; finalized history can never be resumed.
- Prevent stale, inaccurate, non-monotonic, jitter-contaminated, and implausibly fast location fixes from inflating distance or pace. Save/render the accepted route in chronological order and maintain pause/resume boundaries.
- Keep the release application ID `se.hampusandersson.relven`, existing database schema where possible, free OpenFreeMap provider, offline recording, no-map builds, phone/Wear behavior, and data migration compatibility.

## Non-goals

- A second recording service, timer, database, or workout lifecycle.
- A paid or API-key-required tile provider, mandatory network access, or user-triggered tile downloads.
- Synthetic pace, elevation, heart-rate, cadence, recovery, or training-load values.
- Smoothing or editing a persisted route for visual appeal. On-screen map rendering may use normal renderer simplification only; stored accepted samples remain the analysis source.
- Redesigning unrelated plan, exercise, or strength features in this work.

## Approaches considered

1. **Keep `RunActivity` and `DetailActivity` as the presentation owners and restyle their XML/tabs.** This is smaller, but keeps the split between old review and Relven history and makes the required embedded live map difficult to maintain.
2. **Chosen: Compose presentation adapters around the existing `Tracker` and existing MapLibre host, then share a data-backed activity review screen across save/review and history.** This matches the existing Relven Compose migration, preserves proven engine callbacks, makes map failure independent of recording, and avoids introducing a second source of truth.
3. **Create a new tracker/database or move tracking into a new map SDK.** This would duplicate state and increase data migration, service, and offline risk. It is unnecessary because the current engine and MapLibre/OpenFreeMap path already work.

## Architecture and behavior decisions

- `Tracker` remains authoritative for activity state, active elapsed time, distance, steps, lap events, sensors, foreground notification, and save/discard. Compose observes a bounded immutable presentation snapshot and invokes existing service-backed callbacks.
- GPS validation runs at the single entry to `Tracker` before elevation, distance, speed, logging, and route observers consume a fix. Invalid fixes do not change workout distance/time or become accepted route points. A quality-aware deadband is based on the two reported horizontal accuracies and time, not a fixed minimum distance; implausible speed is bounded, stale/future/non-increasing monotonic timestamps are rejected, and a long fix gap starts a new route segment without inventing distance.
- MapLibre/OpenFreeMap renders the accepted points with start/current/finish markers, follow/recenter and drag/zoom. The map host stays alive across its expanded state. A local route/current-location drawing remains available when style or network loading fails; attribution and privacy copy remain visible. No-map builds retain a graceful non-map state.
- The active view uses a live map as a meaningful part of the first screen and a compact, always-reachable metric/control surface. Elapsed time, distance, and current pace are primary; average pace, heart rate when available, lap, lap pace, and active interval/target appear as secondary metrics. A separate expanded map view retains the same MapView/camera. Pause/resume, lap, end confirmation, and stop-to-review are real Tracker actions.
- A shared `ActivityReview` model is loaded from RunnerUp's existing DB on IO and used by both unsaved review and saved history. It distinguishes `UNFINISHED_REVIEW` from `SAVED`; only unfinished review exposes Resume/Save/Discard. Finalized history exposes edit/share/upload/history navigation, never Resume. Save is idempotent and writes notes to `DB.ACTIVITY.COMMENT`; discard is confirmed and deletes only the active unsaved activity through the existing completion path.
- The review screen presents date/start, summary metrics, prominent fitted route with start/finish, available route metrics/legend, chart(s), ordered laps/splits and planned intervals, and notes. Unsupported or unreliable values are omitted or marked unavailable. Map/chart computations are shared, pure, and tested; chart ranges exclude invalid GPS-derived extremes.
- Any schema change discovered during implementation requires an explicit safe migration and migration test. Prefer the existing stored fields and current database schema.

## Delivery sequence

Deliver and merge one PR at a time, always from updated `master`; do not start the next PR until the current PR is merged:

1. **GPS quality foundation:** tested fix validation, stationary jitter suppression, plausible derived speed, pause/gap handling, and no regressions to non-GPS activities.
2. **Active running screen:** Kinetic Obsidian Compose screen, live accepted route, current position/quality, MapLibre follow/recenter/expand, all metrics and workout controls through the current Tracker.
3. **Unified review/details:** replace legacy stopped-review UI and make the same modern data-backed presentation reusable from History; retain save/resume/discard, notes, routes, charts, splits, intervals, and edit/share/upload actions.
4. **Lifecycle and performance hardening:** process/background/reopen/save idempotence, route/map update throttling, sensor/notification/Wear regression checks, responsive layouts, and final visual iteration.

The physical Android device remains unused until all phases are implemented and ready for manual testing.

## Acceptance and verification

- Automated location-sequence tests cover stale, future, invalid-accuracy, non-monotonic, stationary 9 m-accuracy jitter, realistic slow walking/running, pause/resume, route gaps, and implausible spikes.
- Workout/service tests cover started → pause → resume → lap → stop/review → resume or save/discard, with exactly one authoritative activity and correct final state.
- App and no-map builds, app/Wear/HR/common unit tests, release lint, Spotless, emulator smoke runs with simulated routes, and rendered phone screenshots at compact and standard sizes pass.
- Map/tile failure does not interrupt recording; accepted route remains available in local fallback and saved history.
- Saved review and History details show the same presentation and reopen from the database after process/app restart; no Resume appears for finalized history.
- Capture and compare emulator results against the current Stitch references, recording extrapolated post-workout details separately from exact matches.
- Use no physical ADB device until all phases are complete and ready for manual testing.
