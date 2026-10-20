# Implementation status — 20 October 2026

## 20 October 2026 serial integration checkpoint

- **Current increment — free route maps:** the existing free osmdroid route map supports pace, heart rate, speed, and elevation coloring with a green-to-red legend. Pace is derived from stored active distance and elapsed time; route speed is now loaded from the existing activity database. Missing samples render gray, and a metric with fewer than two values is reported unavailable. Unit tests cover availability and color buckets. Local device rendering and live OpenStreetMap tile availability remain unverified; no ADB device was used.

- PR #21 (production Today launcher with Start/History retained in RunnerUp) merged to `master` as `9335a6e`. Local debug/release builds, app unit tests, lint and style passed; hosted build/test/lint/style/F-Droid CI passed.
- PR #22 (lifecycle-bound, read-only Tracker session status on Today) merged to `master` as `dbaaee2`. Local debug/release builds, app unit tests, lint and style passed; hosted build/test/lint/style/F-Droid CI passed.
- PR #23 (open the existing RunnerUp workout screen from the active/paused Today card) merged to `master` as `04674c4`. Local debug/release builds, app unit tests, lint and style passed; hosted Android CI passed.
- PR #25 (explicitly activate, switch, or clear a saved plan without mutating its package or workout history) merged to `master` as `2cc3858`. Local app tests, debug/release builds, lint and style passed; hosted Android CI passed.
- PR #26 (show the active plan item on Today, using its local timezone and localized loading/empty/rest/workout/error states) merged to `master` as `c67da79`. Local app tests, debug/release builds, lint and style passed; independent review and hosted Android CI passed. No emulator or physical ADB device was used.
- PR #27 (read-only active-plan monthly calendar with selected-day workout/rest details) merged to `master` as `181c2e9`. Local debug/release builds, app tests, lint and style passed; independent review and hosted Android CI passed. No device or ADB server was used.
- PR #28 (immutable plan revisions with explicit calendar rescheduling) merged to `master` as `6ba5ea7`. Local debug/release builds, app tests, lint and style passed; hosted build/test/release-lint/style/F-Droid checks passed. Independent review findings were fixed with regression coverage. No device or ADB server was used.
- PR #29 (native offline AI Plan Kit copy/share/save and reproducible schema bundle) merged to `master` as `da75d46`. Local debug/release builds, app tests, lint, style, Python archive tests and stale-asset checks passed; hosted Android CI passed. No emulator or physical ADB device was used.
- PR #20 (legacy migration review and confirmed import of supported workouts and eligible completed running activities) merged to `master` as `2b4198c`. Hosted build/test/lint/style/F-Droid checks passed.
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

- **Completed:** Verified current project `15389433782836702529`; downloaded all ten screen screenshots and seven available HTML exports. The [Stitch manifest](../design/stitch/manifest.json) records IDs and, where available, local files. Extracted primary Android colors, font and shell elements in the [reference notes](../design/stitch/README.md). Added native Kotlin/Compose theme tokens, bundled licensed Plus Jakarta Sans, and a Today screen that reads the latest activity from the inherited local database. The production launcher opens this screen; Start and History continue into RunnerUp's workout and history screens. Today observes the authoritative Tracker state and shows an active or paused session card with a button opening RunnerUp's existing active workout screen. Today also shows the active plan’s item for its local date, and Plan has a read-only monthly calendar with workout/rest markers and selected-day details.
- **Pending:** Correct the three inconsistent planning/detail screens in Stitch; implement the remaining home data, phone screens, watch screens and production navigation.
- **Changed modules:** `design/stitch`, root and app Gradle, app manifest, `app/src/main/kotlin`, a narrow legacy tab-intent hook and documentation.
- **Build/test status:** Launcher manifest regression test passed on PR #21; Tracker status and binding lifecycle tests passed on PR #22. Active-session route and plan date/state tests pass. The Plan calendar has dedicated tests for timezone, Monday-first month grids, workout/rest/empty entries, multiple workouts on one date, leap February, year rollover, and short-month clamping. Local build/test/lint/style results and hosted CI are recorded in the corresponding PR checks. The monthly screen has not been rendered on a device.
- **Emulator/physical devices:** No app visual run for the current increment. The first AVD was ARM and unsupported by this x86 host; the x86_64 Relven AVD appeared unauthorized to ADB because the server reported `ADB_VENDOR_KEYS` unset. No shared ADB server/key changes were made. The physical device remains unused until all phases are complete and the app is ready for manual testing.
- **Known issues:** Stitch edit of the three exact screens returned HTTP 502. A fresh read showed unchanged file entries; the edit may still be pending server-side. Current three exports are content inventory only.
- **Next step:** Continue migrating essential running UI and navigation while retaining the inherited `Tracker`.

## Phase Four — workout import

- **In progress:** The workout library now has a system file-picker button. Relven v3 running-workout JSON enters through this picker or the inherited document intent. Time, distance and manual ends, pace, speed and heart-rate targets, sequences and repeats convert to the RunnerUp engine and are saved in its existing workout file format. The parser rejects unsupported composite/heart-rate-below ends, effort targets, strength documents and exercise snapshots rather than changing their meaning.
- **Verification:** Focused importer unit tests, phone debug build and lint passed. The generated file structure is tested; import through UI and execution on a device have not been verified.
- **Current increment:** The import confirmation previews ordered steps, repeat counts, durations and targets before saving; RunnerUp remains responsible for execution.
- **Pending:** Broader real-world FIT Workout compatibility, picker-to-execution verification, and activity export round trip. [FIT research](fit-import-research.md) records the supported subset and official SDK license constraint.

## Phase Five — plan packages and library

- **In progress:** Versioned running-plan and workout schemas plus a strict local parser and atomic app-private package store are implemented. The importer rejects unsupported workout semantics, unknown fields, non-schema JSON types, invalid dates/timezones/references, packages over 4 MiB, JSON nesting over 32, and more than 100,000 JSON tokens.
- **In progress:** Users can import, preview, save and activate plans. Today shows the active item and the Plan calendar shows its monthly schedule. Renaming the plan, rescheduling, adding one workout/rest entry, or removing one entry creates and activates a complete schema-v2 revision; imported packages and prior revisions remain unchanged. Each revision keeps a root ID, sequence number and predecessor ID. Workout definitions and completed history are unchanged.
- **Current increment:** Calendar actions rename the active plan, add one workout already included in the plan or a rest day to the selected date, or remove one scheduled entry after confirmation. Renaming creates a name-only revision. The last schedule entry cannot be removed. Revisions are accepted only when they follow the saved lineage tip and preserve workout definitions and unaffected plan data; each revision contains one name or schedule operation. Stale branches, mixed operations, and unrelated edits are rejected.
- **Current increment:** Workout Manager provides native copy of the generation instructions and share/save of a reproducible AI Plan Kit ZIP built from the canonical plan and workout schemas. AI output targets initial plan schema v1; app-managed revision v2 is included for reference. The kit documents only the currently executable running-plan subset and has no online AI dependency.
- **Pending:** Edit workout definitions. Strength plans and plan-linked execution also remain pending.
- **Verification:** Parser, revision transformation, store lineage/activation, Today-state and calendar-state tests pass locally. The lineage regression test was first observed failing, then passed with the validation fix. For the AI Plan Kit increment, Python generator tests and `RelvenPlanKitTest` pass; the bundled example validates against the packaged schemas. For plan schedule edits, revision creation, store lineage/activation, collision/final-entry rejection, and calendar availability tests pass. For this name-edit increment, revision/store lineage and name validation tests pass. The complete local Latest debug/release builds, app unit tests, debug lint (no new findings), and Spotless pass. No emulator or physical ADB device has been used for this increment.

## Later phases

See [roadmap](roadmap.md) and [feature parity](feature-parity.md). Do not use the owner's phone via ADB again until all phases are complete and the app is ready for manual testing.
