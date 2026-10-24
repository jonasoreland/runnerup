# Health Connect Export and Centralized Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish completed Relven workouts to Health Connect on an opt-in, retry-safe, strictly one-way basis and consolidate existing and new controls into a functional Relven Settings experience.

**Architecture:** Preserve Room/RunnerUp workout stores as the source of truth and add a local export outbox with a WorkManager publisher using stable Health Connect client IDs. Add Settings to the Compose home navigation and route settings pages to typed preference-backed controls or existing supported integrations; migrate legacy preferences without resetting their current values.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, AndroidX Health Connect 1.1.0, WorkManager, Room or the existing durable local-store pattern, JUnit/Robolectric.

**Spec:** `C:\Users\hampe\.codex\attachments\a1d00dbb-1421-484a-a194-03b27146e21f\Inklistrad text.txt`

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- Health Connect is strictly one-way: Relven writes; it never reads external workouts or health records.
- Health Connect export is opt-in; workout save must work when Health Connect is unavailable.
- Never fabricate unavailable metrics or export planned workouts as completed sessions.
- Preserve existing preferences, workout history, JSON import/export, and Wear OS Data Layer behavior.
- Do not use physical-device ADB or add cloud services.
- Build phone and Wear debug APKs from `master` after each merged PR.

## Review Focus

- Repeated/partially failed Health Connect writes must not duplicate sessions: assert stable client IDs and retry recovery.
- Revoked permissions and unavailable provider must retain sessions and expose actionable status: test each worker result.
- Missing telemetry must remain absent instead of becoming zero: test sparse running and strength mappings.
- Existing preference values must survive migration and remain visible to legacy readers: test migration with prepopulated SharedPreferences.
- Long settings pages must remain reachable above floating navigation and system bars: verify insets and scroll semantics.

---

### Task 1: Settings destination and entry point

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/HomeNavigationScaffold.kt` only if needed for measured safe-area support
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/SettingsDestination.kt`
- Create: `app/test/java/org/runnerup/relven/ui/SettingsDestinationTest.kt`

**Interfaces:** `SettingsDestination` provides a stable enum for the Settings overview and subpages. The main home screen exposes a gear action from Today and persists/restores the selected settings page.

- [ ] Test destination labels/group membership and saved-state fallback.
- [ ] Run the focused unit test and confirm it fails before implementation.
- [ ] Add the Settings route and a scrollable Relven-styled overview grouped as Devices, Training, App, About.
- [ ] Verify safe-area behavior and run focused tests plus `:app:testLatestDebugUnitTest`.

### Task 2: Typed preference access and compatibility migration

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/settings/RelvenSettingsRepository.kt`
- Create: `app/test/java/org/runnerup/relven/settings/RelvenSettingsRepositoryTest.kt`
- Modify: legacy preference entry points only when a compatible bridge is verified.

**Interfaces:** Repository exposes observable immutable settings state and update methods; old keys/defaults remain authoritative until one-time migration is idempotent.

- [ ] Test defaults, migration idempotency, retention of existing keys, and restart persistence.
- [ ] Run tests red, implement repository and migration, then verify green.

### Task 3: Functional settings pages for current app capabilities

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenSettingsScreens.kt`
- Modify: `RelvenHomeActivity.kt`
- Modify: existing voice settings integration only to launch from its centralized page.
- Tests: `app/test/java/org/runnerup/relven/ui/SettingsNavigationTest.kt`, repository tests.

**Interfaces:** each category screen consumes repository state and explicit action callbacks. Watch state comes from the existing Wear Data Layer; voice controls reuse the existing TTS/prefs implementation; unsupported switches are omitted.

- [ ] Add navigation and persistence tests for each settings subpage.
- [ ] Wire functional training, GPS/sensors, appearance, units, notifications, data/export, watch, voice, and About controls where currently supported.
- [ ] Verify long-screen scrolling, system insets, persistence, and no false sensor/watch status.

### Task 4: Health Connect dependency, permission boundary, and mapping

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectAvailability.kt`
- Create: `app/src/main/kotlin/org/runnerup/relven/health/WorkoutHealthConnectMapper.kt`
- Tests: `app/test/java/org/runnerup/relven/health/WorkoutHealthConnectMapperTest.kt`

**Interfaces:** pure mapping converts recorded running and completed strength data into Health Connect write records, omitting unknown metrics; no API exposes external-record reads.

- [ ] Test session types, timestamps, distance, sparse HR/speed/cadence/elevation, route ordering, laps, and strength detail limits.
- [ ] Add stable AndroidX Health Connect 1.1.0 and only the required write permissions.
- [ ] Verify exact SDK permission names and mapping tests.

### Task 5: Durable export outbox, worker, retries, and deletion policy

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectExportStore.kt`
- Create: `app/src/main/kotlin/org/runnerup/relven/health/HealthConnectExportWorker.kt`
- Modify: workout completion/deletion integration points identified during implementation audit.
- Tests: store/worker retry, idempotency, edit versioning, and deletion ownership.

**Interfaces:** stable `clientRecordId` is derived from Relven session identity; monotonically increasing client version represents updates; only IDs recorded as Relven-owned may be deleted.

- [ ] Test outbox persistence across recreation and unique-work scheduling.
- [ ] Test success, transient retry, permission denial, partial export, repeated delivery, update, and owned-record deletion.
- [ ] Connect completion, edit, and delete paths without making workout save depend on Health Connect.

### Task 6: Health Connect Settings, opt-in, history backfill, and status

**Files:**
- Modify: `RelvenSettingsScreens.kt`
- Modify: Health Connect worker/store as required
- Tests: permission flow, export toggle, backfill eligibility/deduplication, status history.

- [ ] Add user-initiated write permission request and opt-in.
- [ ] Add manual historical export preview/progress and per-session failure reporting.
- [ ] Show real persisted statuses, retry action, permission settings action, and Swedish Samsung Health guidance.
- [ ] Ensure no Health Connect reads/import paths exist.

### Task 7: Integration verification, docs, builds, and PR delivery

**Files:** docs for architecture, permissions, mapping limits, settings keys, and Samsung validation limits.

- [ ] Run focused and full app unit tests, lint, phone debug build, and Wear debug build.
- [ ] Verify package IDs and inspect changed permission declarations.
- [ ] Capture emulator screenshots for Settings and Health Connect state where available.
- [ ] Open one PR, obtain code review, merge it, then begin the next PR.
- [ ] After each merge, build both APKs from `master` and report exact absolute paths.
