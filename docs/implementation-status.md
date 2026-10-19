# Implementation status — 19 October 2026

## Phase Zero — repository migration

- **Completed:** Existing history-preserving GitHub fork checked out on `codex/phase-zero`; `upstream` remote added. Original Relven repository untouched. Baseline phone/watch APKs and selected tests passed before edits. The release ID now matches the old Relven Play ID per owner instruction. Relven launcher labels, uploaded icon, CI artifact labels, README and migration documents changed locally.
- **Pending:** Real-device launcher check and safe Play upgrade migration. Dedicated splash and remaining legacy service/account copy need audit.
- **Changed modules:** root Gradle configuration, `common` resources, phone/watch icon resources, Wear manifest, CI and documentation. Workout engine, database, GPS, audio and export logic are unchanged.
- **Build results:** Untouched baseline and branded debug phone/watch builds passed. Branded lint: Wear had no errors/warnings; phone had no new issues (23 errors filtered by inherited baseline). `spotlessCheck` fails on inherited CRLF formatting in 197 Java files on Windows; no bulk reformat was made.
- **Automated tests:** Existing app unit target passed at baseline. Wear test target had no test sources. No new behavior tests for this identity/resource migration.
- **Emulator:** Not run. `adb devices -l` showed no devices.
- **Physical devices:** Phone and Samsung Galaxy Watch6 Classic unverified.
- **Known issues:** Same-ID upgrade cannot read the old Relven Room data without migration; signing identity and published version-code floor require verification before upload. Optional legacy Mapbox and upstream integrations remain. Some legacy UI and provider-specific strings still show upstream terminology. Runtime GPS, audio, export and wearable paths remain unverified.
- **Next step:** Audit workout and wearable paths and PR #1378; design an explicit migration for the old Relven Room data before any Play update.

## Later phases

Wear HR, Stitch UI, FIT/JSON import, AI Plan Kit, strength library, map coloring and unified calendar are pending. See [roadmap](roadmap.md) and [feature parity](feature-parity.md).
