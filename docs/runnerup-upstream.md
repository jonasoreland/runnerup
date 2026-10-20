# RunnerUp upstream provenance

- Source: <https://github.com/jonasoreland/runnerup>
- Baseline commit: `ac4f8baa0473299e389f6bc70107db8ffbef23c7` (18 October 2026, `fix: minSDK 24 Android 7 (#1402)`)
- RunnerUp baseline version: `2.12.0.1`. Relven's displayed version is `2.0.0-dev`; the inherited version-code formula is `374` plus the minimum-SDK base and must be checked against Play before release.
- Git `upstream` points to RunnerUp; `origin` points to the separate `HampusAndersson01/Relven-2.0` fork. The original private `HampusAndersson01/Relven` repository was used only for an application-ID check and was not changed.

The fork retains upstream commit history, `LICENCE`, `CREDITS.md`, source copyright headers and `Documentation/`. Relven modifications remain under GPLv3. Review third-party notices when assets or dependencies change.

## Build baseline before edits

On Windows with JDK 25, Gradle wrapper 9.6.1, Android SDK platform 37.0/37.1 and Build Tools 37.0.0:

```text
./gradlew :app:assembleLatestDebug :wear:assembleDebug :app:testLatestDebugUnitTest :wear:testDebugUnitTest
BUILD SUCCESSFUL in 1m 48s (122 actionable tasks)
```

`wear:testDebugUnitTest` had no test sources. No device was connected to ADB. Compilation does not prove GPS, timer, speech, export or watch runtime behavior.

## Compatibility

The `org.runnerup` Java namespaces, wearable message paths (`/org.runnerup`), legacy database import deep link (`/runnerup.db.export`), storage schema and exporters stay intact. The release ID matches the old Relven app, but RunnerUp's storage schema is unrelated to its Room database. Android may preserve that private data during an upgrade; this app cannot read it without an explicit migration. User-data migration has not been implemented.
