# Implementation status — 20 October 2026

## 20 October 2026 serial integration checkpoint

- PR #14 (initial debug-only Compose preview and supported Relven v3 running-workout import) merged to `master` as `70e2845`. The nested-repeat importer limit is 10,000 expanded segment executions and has boundary tests.
- PR #15 (read-only legacy database archive and explicit JSON export) merged to `master` as `f8450df`.
- PR #16 (bounded FIT running-workout importer) merged as `19395fa`; local build, tests, lint and fixture checks passed, as did hosted build/test/lint/style/F-Droid CI. Real-device FIT samples and picker-to-execution verification remain pending.
- PR #17 (debug-only recent-activity card in the Compose preview) merged as `9b72e12`; local build, tests and lint plus hosted CI passed. No device was used.
- PR #18 (bounded, strict running plan package parser and private atomic store) merged as `eed60df`; local build/tests/lint/style and hosted build/test/lint/style/F-Droid checks passed. Independent review approved. No device was used.
- PR #19 (validated legacy database snapshot and bounded archive preview) merged to `master` as `138781e`. The serial legacy importer is being implemented on one branch: workouts are staged idempotently and eligible completed running activities convert transactionally into history.
- The earlier stacked draft PRs #2–#13 are closed; their branches are retained for serial selection. Work proceeds with one open PR at a time.
- Work continues serially with no more than one open PR. The physical Android device remains off limits until all phases are complete and the app is ready for manual testing.

## Legacy data migration — in progress

- **Completed in the current migration branch:** Read-only archive preview; staged, hash-checked and idempotent legacy workout imports; conversion of eligible completed running activities into RunnerUp history with route and technical pause points; transactional source-identity ledger; synthetic archive and recovery tests.
- **In progress:** Maintenance review/confirmation UI, localized import results and final documentation of supported and archived data.
- **Verification still required:** Full local phone build/test/lint/style gate, independent branch review, one-at-a-time PR/CI/merge, and later signed upgrade testing against a copied production-era backup. Physical ADB remains unused until all product phases are complete and the app is ready for manual testing.
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

- **Completed:** Verified current project `15389433782836702529`; downloaded all ten screen screenshots and seven available HTML exports. The [Stitch manifest](../design/stitch/manifest.json) records IDs and local files. Extracted primary Android colors, font and shell elements in the [reference notes](../design/stitch/README.md). Added native Kotlin/Compose theme tokens, bundled licensed Plus Jakarta Sans, and a debug-only preview with an action into the inherited workout controls. The preview now reads the latest recorded activity from the inherited local database and displays its date, distance and duration when one exists.
- **Pending:** Correct the three inconsistent planning/detail screens in Stitch; implement the remaining home data, phone screens, watch screens and production navigation. The preview is not the release launcher.
- **Changed modules:** `design/stitch`, root and app Gradle, debug manifest, `app/src/main/kotlin`, a narrow legacy tab-intent hook and documentation.
- **Build/test status:** `:app:assembleLatestDebug`, `:app:testLatestDebugUnitTest` and `:app:lintLatestDebug` passed. Lint reported no new issues, with 23 inherited baseline-filtered errors.
- **Emulator/physical devices:** Compose preview cold-launched on Samsung SM-S948B and was inspected by screenshot. Initial black capture was a transient screen/transition state; a clean launch rendered correctly. No navigation or workout behavior was verified in the preview.
- **Known issues:** Stitch edit of the three exact screens returned HTTP 502. A fresh read showed unchanged file entries; the edit may still be pending server-side. Current three exports are content inventory only.
- **Next step:** Connect the home screen to real activity/session data, then migrate essential running UI while retaining the inherited `Tracker`.

## Phase Four — workout import

- **In progress:** The workout library now has a system file-picker button. Relven v3 running-workout JSON enters through this picker or the inherited document intent. Time, distance and manual ends, pace, speed and heart-rate targets, sequences and repeats convert to the RunnerUp engine and are saved in its existing workout file format. The parser rejects unsupported composite/heart-rate-below ends, effort targets, strength documents and exercise snapshots rather than changing their meaning.
- **Verification:** Focused importer unit tests, phone debug build and lint passed. The generated file structure is tested; import through UI and execution on a device have not been verified.
- **Pending:** Broader real-world FIT Workout compatibility, picker-to-execution verification, import UX review and activity export round trip. [FIT research](fit-import-research.md) records the supported subset and official SDK license constraint.

## Phase Five — plan packages and library

- **In progress:** Versioned running-plan and workout schemas plus a strict local parser and atomic app-private package store are being added. The importer rejects unsupported workout semantics, unknown fields, non-schema JSON types, invalid dates/timezones/references, packages over 4 MiB, JSON nesting over 32, and more than 100,000 JSON tokens.
- **In progress:** Users can choose a plan JSON package from the workout manager, validate it before a review summary, save after explicit confirmation, and reopen saved plans from app-private storage. Import does not activate plans or change the current schedule; plan dates are treated as one-time.
- **Pending:** Add calendar scheduling and explicit plan activation/revisions, then build the AI Plan Kit from the canonical schema.
- **Verification:** Parser, bounded-read, summary, and store unit tests plus debug build, full app unit tests, lint, and Spotless pass locally. Independent review approved the Android API 24 compatibility changes; hosted CI is pending. No device is being used.

## Later phases

See [roadmap](roadmap.md) and [feature parity](feature-parity.md). Do not use the owner's phone via ADB again until all phases are complete and the app is ready for manual testing.
