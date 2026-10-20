# Implementation status — 20 October 2026

## 20 October 2026 serial integration checkpoint

- PR #14 (initial debug-only Compose preview and supported Relven v3 running-workout import) merged to `master` as `70e2845`. The nested-repeat importer limit is 10,000 expanded segment executions and has boundary tests.
- The earlier stacked draft PRs #2–#13 are closed; their branches are retained for serial selection. Work proceeds with one open PR at a time.
- The old Relven database is now snapshotted read-only at startup when present, and a maintenance action can export that snapshot as JSON. This is a preservation bridge only: workouts and activity history are not yet imported into the current library.
- The physical Android device remains off limits until all phases are complete and the app is ready for manual testing. This checkpoint used local unit tests/build tooling only.

## Phase Zero — repository migration

- **Completed:** Existing history-preserving GitHub fork checked out on `codex/phase-zero`; `upstream` remote added. Original Relven repository untouched. Baseline phone/watch APKs and selected tests passed before edits. The release ID now matches the old Relven Play ID per owner instruction. Relven launcher labels, uploaded icon, CI artifact labels, README and migration documents changed locally.
- **Pending:** Real-device launcher check and complete Play upgrade migration. Dedicated splash and remaining legacy service/account copy need audit.
- **Changed modules:** root Gradle configuration, `common` resources, phone/watch icon resources, Wear manifest, CI and documentation. Workout engine, database, GPS, audio and export logic are unchanged.
- **Build results:** Untouched baseline and branded debug phone/watch builds passed. Unsigned release phone/watch APKs also built after the HR integration; APK metadata confirms `se.hampusandersson.relven` for both, with version codes `24000374` and `24000375`. Branded lint: Wear had no errors/warnings; phone had no new issues (23 errors filtered by inherited baseline). `spotlessCheck` fails on inherited CRLF formatting in 197 Java files on Windows; no bulk reformat was made.
- **Automated tests:** Existing app unit target passed at baseline. Wear test target had no test sources. No new behavior tests for this identity/resource migration.
- **Emulator:** Not run. `adb devices -l` showed no devices.
- **Physical devices:** Debug APK installed and cold-launched on Samsung SM-S948B (API 37) over ADB; `MainLayout` resumed without a startup crash. A first-run background-location explanation was visible. Permission selection and workout behavior remain unverified. Galaxy Watch6 Classic unverified.
- **Known issues:** Same-ID upgrade data is preserved in a private JSON vault and can be exported, but old workouts/activities are not imported into the new databases. Signing identity and published version-code floor require verification before upload. Optional legacy Mapbox and upstream integrations remain. Some legacy UI and provider-specific strings still show upstream terminology. Runtime GPS, audio, export and wearable paths remain unverified.
- **Next step:** Convert explicitly supported old workout records and activity history from a copied production-era database; retain unsupported records in the vault and report them clearly.

## Phase One — engine audit

- **Completed:** Source owners for workout steps, triggers, session state, GPS, activity persistence, voice feedback, import/export, maps and wearable messages are recorded in [engine audit](engine-audit.md). The existing phone `Tracker` remains the authoritative session owner.
- **Pending:** Device-level recording, pause/resume, GPS, Swedish voice, export round-trip and process recovery checks.
- **Changed modules:** Documentation only.
- **Build/test status:** No engine refactor was made; Phase Zero build remains the baseline.
- **Emulator/physical devices:** None connected.
- **Known issues:** This is a source-level map, not a functional acceptance test.
- **Next step:** Preserve the engine while adding tested import adapters and UI observation.

## Phase Two — Wear heart-rate integration

- **Completed:** Reviewed and integrated upstream PR #1378 as an initial branch, adapted provider signatures and Bluetooth gating, added source-node validation, first-reading connection confirmation, 15-second stale sample expiry, a 30-second initial timeout and unit tests. See [Wear HR audit](wear-hr-audit.md).
- **Pending:** Watch foreground/background sensor lifecycle, permission request flow, visible sensor status, Galaxy Watch6 Classic tests and reconnection proof.
- **Changed modules:** `hrdevice`, `wear`, `app` HR settings/tracker, and shared message constants.
- **Build/test status:** Phone/watch debug builds, app/Wear/HR unit targets and lint passed after service hardening. App lint has 23 inherited baseline-filtered errors; Wear has none and HR library has one inherited baseline-filtered error. No new issues were reported.
- **Emulator/physical devices:** None connected.
- **Known issues:** Screen-off/background HR is unsupported in the current started-service implementation. Do not present it as release-ready.
- **Next step:** Make watch sensor collection lifecycle compliant, then test with a paired watch and phone.

## Phase Three — Stitch reference retrieval

- **Completed:** Verified current project `15389433782836702529`; downloaded all ten screen screenshots and seven available HTML exports. The [Stitch manifest](../design/stitch/manifest.json) records IDs and local files. Extracted primary Android colors, font and shell elements in the [reference notes](../design/stitch/README.md). Added native Kotlin/Compose theme tokens, bundled licensed Plus Jakarta Sans, and a debug-only preview with an action into the inherited workout controls.
- **Pending:** Correct the three inconsistent planning/detail screens in Stitch; implement real home data, remaining phone screens, watch screens and production navigation. The preview is not the release launcher.
- **Changed modules:** `design/stitch`, root and app Gradle, debug manifest, `app/src/main/kotlin`, a narrow legacy tab-intent hook and documentation.
- **Build/test status:** `:app:assembleLatestDebug`, `:app:testLatestDebugUnitTest` and `:app:lintLatestDebug` passed. Lint reported no new issues, with 23 inherited baseline-filtered errors.
- **Emulator/physical devices:** Compose preview cold-launched on Samsung SM-S948B and was inspected by screenshot. Initial black capture was a transient screen/transition state; a clean launch rendered correctly. No navigation or workout behavior was verified in the preview.
- **Known issues:** Stitch edit of the three exact screens returned HTTP 502. A fresh read showed unchanged file entries; the edit may still be pending server-side. Current three exports are content inventory only.
- **Next step:** Connect the home screen to real activity/session data, then migrate essential running UI while retaining the inherited `Tracker`.

## Phase Four — workout import

- **In progress:** The workout library now has a system file-picker button. Relven v3 running-workout JSON enters through this picker or the inherited document intent. Time, distance and manual ends, pace, speed and heart-rate targets, sequences and repeats convert to the RunnerUp engine and are saved in its existing workout file format. The parser rejects unsupported composite/heart-rate-below ends, effort targets, strength documents and exercise snapshots rather than changing their meaning.
- **Verification:** Focused importer unit tests, phone debug build and lint passed. The generated file structure is tested; import through UI and execution on a device have not been verified.
- **Pending:** FIT Workout decoding, richer schema validation, a full saved-file execution test, import UX review and activity export round trip. [FIT research](fit-import-research.md) records the official SDK license constraint.

## Later phases

AI Plan Kit, strength library, map coloring and unified calendar remain pending. See [roadmap](roadmap.md) and [feature parity](feature-parity.md). Do not use the owner's phone via ADB again until all phases are complete and the app is ready for manual testing.
