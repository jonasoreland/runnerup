# Relven

Relven is a free, open-source fitness app for Android with a phone-dependent Wear OS companion. This repository preserves [RunnerUp](https://github.com/jonasoreland/runnerup)'s working workout engine while Relven's training-plan and native UI work proceeds incrementally.

## Available now

- Record activities on the phone with GPS, time, distance and pace.
- Create and run free or structured interval workouts with pace or heart-rate targets.
- Hear configurable workout announcements through Android text-to-speech.
- Review saved activities and use the inherited GPX/TCX and service export paths.
- Use the Wear OS companion for live workout display and remote actions. The phone owns the workout and GPS recording.
- Import or share the workout formats currently supported by RunnerUp.

FIT Workout and Relven JSON training-plan imports, watch heart-rate capture, strength tracking, a unified calendar, AI Plan Kit, route coloring and the new Compose UI are **not yet implemented**. The current screens still use the inherited layout beneath Relven launcher branding.

## Build and install

Requirements: JDK 17 or newer, Android SDK platform 37.1, Build Tools 37.0.0, and the included Gradle wrapper. The local baseline used Gradle 9.6.1 and JDK 25. Set `ANDROID_HOME` to the SDK directory.

```sh
./gradlew :app:assembleLatestDebug :wear:assembleDebug
./gradlew :app:testLatestDebugUnitTest :wear:testDebugUnitTest
```

The phone APK is under `app/build/outputs/apk/latest/debug/`; the watch APK is under `wear/build/outputs/apk/debug/`. Install each APK on its matching device with `adb install` and pair the devices normally. No physical-device or emulator run has yet been verified for this fork.

Phone and watch share the original Relven Play application ID `se.hampusandersson.relven` (debug suffix `.debug`); the free variant uses `se.hampusandersson.relven.free`. Internal `org.runnerup` Java namespaces and wearable protocol paths remain for compatibility. An in-place upgrade needs the existing Play signing identity, a higher version code and a safe path for old app data; these release checks are pending.

## Workouts, plans and exports

Use the current workout management screen to create and share inherited RunnerUp-format workouts. Relven JSON multiweek plans and FIT Workout files cannot yet be imported. Use activity details and export controls for available recording formats; installed options depend on the build variant and configured integrations. See [feature parity](docs/feature-parity.md).

## Privacy and offline use

Workout and activity data is stored locally by the inherited engine. Local recording needs no account or subscription. Map tiles and optional third-party synchronization can use the network and may disclose the map area or uploaded activity to those services. The default no-token map implementation uses osmdroid; an optional legacy Mapbox path remains for review. Relven must not require paid map or voice services.

## Status and limitations

See [implementation status](docs/implementation-status.md), [roadmap](docs/roadmap.md) and [migration notes](docs/migration.md). The untouched and initially branded phone/watch APKs and selected tests built on 19 October 2026. The shared Play ID change still needs a fresh build. GPS, audio, import/export and watch behavior still need emulator and device smoke tests. Samsung Galaxy Watch6 Classic heart-rate behavior is unverified.

## Upstream, license and contributions

Relven is derived from RunnerUp version 2.12.0.1 at commit [`ac4f8baa0473299e389f6bc70107db8ffbef23c7`](https://github.com/jonasoreland/runnerup/commit/ac4f8baa0473299e389f6bc70107db8ffbef23c7). Git history, copyright notices, the [GPLv3 license](LICENCE) and [third-party credits](CREDITS.md) are retained. See [upstream notes](docs/runnerup-upstream.md). Contributions should include focused tests and preserve attribution; open an issue before large engine changes.
