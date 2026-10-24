# Relven

Relven is a free, open-source Android fitness app with a phone-dependent Wear OS companion. It preserves [RunnerUp](https://github.com/jonasoreland/runnerup)'s workout engine and local activity database while replacing user-facing flows incrementally with Relven's Compose interface.

## Available now

- Record phone workouts with RunnerUp's foreground Tracker, GPS filtering, elapsed time, distance, pace, laps and history persistence.
- Run free, goal-based and structured interval workouts with pace or heart-rate targets and configurable audio coaching.
- Use Relven's workout setup, active-run screen, live route map and workout controls while the existing Tracker remains the source of session state.
- Build offline Gym workouts from the bundled bilingual bodyweight catalog and custom exercises, then run guided sets, repetitions or timed work with durable progress.
- Import supported FIT running workouts, Workout v3 JSON, running-plan packages, and GPX/TCX activities. Review and save imported activities to local history.
- Manage plans in a day/week/month calendar, preview scheduled sessions, start them on any date, repeat completed strength sessions as new executions, and save named workout favorites.
- Review saved activities in Relven's summary and route-map screens, including available splits, charts, notes and route metric coloring; edit and share supported activity files.
- Use the offline AI Plan Kit to copy a complete generation prompt or share a ZIP with schemas, capabilities, examples, all bundled exercises and the current custom exercise collection.
- Use the Wear OS companion for workout status and remote controls. The phone remains authoritative for workout state and GPS recording.

Workout and plan data is stored locally and does not require an account or paid service. Maps use MapLibre with OpenFreeMap styles and need network access for uncached tiles; GPS recording continues if tiles are unavailable. Some secondary RunnerUp settings and flows remain inherited. Wear heart-rate integration has automated build/test coverage, but paired-watch, Galaxy Watch6 Classic, screen-off capture and reconnection behavior are unverified and are not release-ready.

## Build and install

Requirements: JDK 17 or newer, Android SDK platform 37.1, Build Tools 37.0.0, and the included Gradle wrapper. Set `ANDROID_HOME` to the SDK directory.

```sh
./gradlew :app:assembleLatestDebug :wear:assembleDebug
./gradlew :app:testLatestDebugUnitTest :wear:testDebugUnitTest :hrdevice:testDebugUnitTest :common:testDebugUnitTest
python tools/test_build_ai_plan_kit.py
python tools/build_ai_plan_kit.py --check
```

The phone APK is `app/build/outputs/apk/latest/debug/app-latest-debug.apk`; the Wear APK is `wear/build/outputs/apk/debug/wear-debug.apk`. Install them on their matching emulators or devices and pair the devices normally. The latest master debug APK was built from commit `656705a7` on 24 October 2026 and launched on an isolated API 36 emulator. No physical-device testing has been done for that build.

Release phone and Wear variants retain the original Play application ID `se.hampusandersson.relven`; debug adds `.debug` and the free phone variant adds `.free`. The current local release App Bundles are unsigned; building them does not verify the Play upload key or establish that an in-place upgrade will be accepted. The internal publishing workflow performs signature/package/version checks before upload. Internal `org.runnerup` namespaces and wearable protocol paths remain for compatibility.

## Workouts, plans and exports

Use **Import workout** to select a supported RunnerUp workout, FIT Workout, or Workout v3 JSON file. The Plan library imports and activates supported plan JSON; its calendar supports schedule and running-segment edits through immutable revisions. Strength and prehab plan entries use the canonical offline exercise catalog and can be run as guided sessions. The **AI Plan Kit** action copies a complete generation instruction or shares/saves a ZIP with current schemas, capabilities, examples, all bundled exercises and custom exercises. AI-created plans target schema v1; app-managed plan revisions use schema v2. Unsupported file semantics are rejected rather than silently changed.

History supports GPX/TCX import and sharing for supported formats. Multi-segment GPX import is declined because the inherited route model cannot retain segment gaps. See [feature parity](docs/feature-parity.md), [implementation status](docs/implementation-status.md), [migration notes](docs/migration.md), and the [AI Plan Kit guide](docs/ai-plan-kit/README.md).

## Privacy and offline use

Workout and activity data is stored locally by the inherited engine and Relven's offline stores. Recording and session state do not depend on map-tile availability. Map providers receive tile requests for the displayed area when uncached tiles are requested; optional third-party synchronization can send activities to configured services. Relven does not require a paid map or voice service.

## Status and release gates

On 24 October 2026, master commit `656705a7` passed 519 app, 22 Wear, 9 HR-device and 12 common unit tests. Phone/Wear debug APKs and release App Bundles built locally; release manifests retain the Play application ID, but local bundles are unsigned. Hosted CI passed for the latest merged PR.

Remaining release gates include testing a copied production-era legacy v18 backup, validating a signed same-ID upgrade with the existing upload key, checking Play's current version-code and signing state, and completing paired-Wear and final device checks. The owner's physical ADB device remains reserved until the app is ready for manual testing. See the [implementation status](docs/implementation-status.md) and [roadmap](docs/roadmap.md) for evidence and open work.

## Upstream, license and contributions

Relven is derived from RunnerUp version 2.12.0.1 at commit [`ac4f8baa0473299e389f6bc70107db8ffbef23c7`](https://github.com/jonasoreland/runnerup/commit/ac4f8baa0473299e389f6bc70107db8ffbef23c7). Git history, copyright notices, the [GPLv3 license](LICENCE) and [third-party credits](CREDITS.md) are retained. See [upstream notes](docs/runnerup-upstream.md). Contributions should include focused tests and preserve attribution; open an issue before large engine changes.
