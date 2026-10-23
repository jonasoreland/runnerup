# Relven running experience redesign plan

## Delivery rules

- Execute in the existing checkout; no additional worktree.
- Work on one PR branch at a time. Merge it before creating the next branch/PR.
- Preserve release package ID `se.hampusandersson.relven`; never use the user's physical ADB device.
- Keep the design/behavior contract in `docs/superpowers/specs/2026-10-23-running-experience-redesign.md` authoritative.
- Every behavior change follows test-first: run a focused test red, implement minimally, run it green, then run the project suite before commit.

## Phase 1 and first PR: GPS quality foundation

1. Add `GpsFixFilterTest` cases for accepted accurate anchor, stale/future/non-increasing fixes, invalid coordinate/accuracy, accuracy-aware stationary jitter, slow walking, plausible running, isolated high-speed spikes, and resetting after pause/long gaps. Verify the tests fail because the filter API is absent.
2. Add a pure `GpsFixFilter` that accepts Android location samples against monotonic time and returns accept/reject, distance, filtered speed, and segment-reset metadata. Apply coordinate/accuracy/time validation, an accuracy-derived movement threshold, max plausible speed, and safe reanchoring; keep no-GPS synthetic updates on their existing path.
3. Integrate the filter at `Tracker.onLocationChanged` before elevation input, time/distance accumulation, speed smoothing, persistence, and live-log fan-out. Reset/reanchor at start, pause/resume, stop, and stale gaps; write only accepted samples and do not add gap distance.
4. Run `:app:testLatestDebugUnitTest --tests org.runnerup.tracker.GpsFixFilterTest`, related workout/route tests, full app/Wear/HR/common tests, debug APK, phone/Wear release bundles, release lint, no-map release build, Spotless, and `git diff --check`.
5. Update `docs/engine-audit.md`, `docs/implementation-status.md`, and the design/plan ledger with actual filter behavior and evidence. Review the change, push, open one PR, wait for all required hosted jobs, merge, fast-forward local `master`, and build the debug APK from that merge.

## Phase 2 and second PR: live active workout

1. Add failing presentation tests for Tracker snapshot mapping: total/active time, distance, current/average pace, lap and lap pace, optional HR, active interval, GPS freshness/quality, pause state, and route order.
2. Add a narrow lifecycle-bound accepted-location observer or equivalent immutable snapshot boundary on `Tracker`; ensure registration/unregistration follows `RunActivity` lifecycle and no observer can mutate workout state.
3. Add a mutable-route MapLibre host (and no-map fallback) that updates a GeoJSON source incrementally/coalesced, displays current location and accepted route, follows only until user gesture, supports recenter and expanded state, and keeps the same map/camera alive.
4. Replace `R.layout.run` visible presentation with one Kinetic Obsidian Compose screen using existing formatter/workout step models and actual pause/lap/stop callbacks. Add stop confirmation and show a clear offline-map state without affecting recording.
5. Verify screen dimensions/rotation/insets, controls, foreground notification state, camera persistence, no-map behavior, map failure, simulated running route, pause/resume and lap in automated tests/emulator; run full build/lint/tests, review, one PR, hosted checks, merge, and master APK.

## Phase 3 and third PR: shared post-workout review and history details

1. Add failing data/state tests distinguishing unfinished review from saved history and asserting supported metrics, route boundaries/order, chart eligibility, split/interval mapping, empty/missing states, note round-trip, and no Resume for saved activities.
2. Build a shared review loader/model using only existing RunnerUp activity/lap/location rows, with IO queries and closed cursors/DB handles before rendering.
3. Replace `DetailActivity` legacy tabs/actions with a Compose review screen for stopped-unsaved and finalized modes; preserve upload/edit/share behavior through existing callbacks/adapters.
4. Route History activity opening through the same composable/body instead of the separate outdated RunnerUp detail flow; embed route map, start/finish, fit/zoom/pan, eligible metric coloring/legend, charts with valid scales, ordered km/manual/structured splits, supported HR/elevation/cadence, and notes.
5. Preserve save idempotence and order: persist notes/edit data, finalize once through `Tracker.completeActivity`, return to History; keep Resume only for unfinished state and confirm discard before deletion. Reopen the activity after closing/restarting the app.
6. Verify tests, offline/no-route, unavailable metrics, narrow/large layouts, chart scale/noisy extremes, map offline fallback, database reopen, note persistence, editing/share/upload and saved-vs-unsaved callbacks; run full checks, emulator captures and independent review, then one PR/CI/merge/master APK.

## Phase 4 and final PR: lifecycle, performance, and cross-surface QA

1. Add recovery tests for Activity recreation, background/foreground, Tracker binding loss/rebind, process restoration of active status, duplicate Save taps, and notification action synchronization. A one-shot terminal completion gate now rejects duplicate Save/Discard callbacks; broader Activity/service recovery coverage remains outstanding. GPS-derived elevation and raw GPS-altitude export data require reported vertical accuracy at or below 5 m, and poor GPS accuracy cannot calibrate the pressure-derived offset. Missing or poorer GPS vertical quality leaves GPS elevation unavailable, while pressure-based elevation remains supported.
2. Stabilize live camera follow and throttle map source/Compose route snapshots without dropping accepted persisted samples; inspect memory/update cost on a long simulated route. Camera displacement gating, first-location zoom-16 initialization, 500 ms snapshot coalescing during event bursts, a one-feature live `MultiLineString` that preserves lap/pause gaps, and saved-route first-layout fitting after both style and viewport readiness are implemented. Unit coverage verifies the fit gate's asynchronous ordering. A 10,000-point serializer test verifies every coordinate remains in one feature. Measure renderer update cost on a long simulated route and validate burst behavior on the emulator.
3. Run phone and paired Wear emulator regression flows for workout commands, HR source state, pause/resume/lap, voice/audio callback preservation, notification controls, and saved activity synchronization. Do not claim a physical watch test unless performed after manual-testing readiness.
4. Capture the actual active and review screens on isolated emulators at compact and standard sizes; compare current Stitch system/screen and document intentional post-workout extrapolation and remaining limits.
5. Run complete app/Wear/HR/common tests, debug assembly, release bundles, release lint, no-map release, Spotless and `git diff --check`; merge one final PR only after required CI passes and rebuild master APK.

## Review focus

- Rejected GPS fixes must not affect time, distance, speed, elevation input, persisted rows, notification data, Wear telemetry, or route.
- GPS fixes with missing or poor vertical accuracy must not create GPS-derived elevation values or elevation charts; valid horizontal tracking must continue.
- A location gap, pause/resume, equal timestamp, late fix, or outlier must not bridge segments or create a distance/pace spike.
- Slow but legitimate movement must eventually accumulate against the accuracy-derived deadband.
- Saving, resuming, and discarding must not create a second recording, lose notes, leave an activity falsely finalized, or resume a saved activity.
- Removing map tiles or turning off the network must leave GPS service and core controls running.
- The no-map/F-Droid source set must compile without MapLibre references.
