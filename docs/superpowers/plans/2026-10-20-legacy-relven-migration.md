# Legacy Relven Data Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Import every legacy running workout accepted by the existing Relven v3 importer and every eligible completed running activity into Relven 2.0 while keeping the old database and all non-imported records recoverable in the private exportable archive.

**Architecture:** Stream the existing versioned JSON table archive to calculate a review summary without retaining activity payloads in memory. After the user confirms, validate and stage accepted workouts outside the visible library, then publish their deterministic files with an atomic migration manifest so retries are idempotent. Convert eligible activity documents into RunnerUp `ActivityEntity` and `LocationEntity` rows in a database transaction with a unique source-identity ledger in the same transaction. Add the review/import flow to the existing Maintenance preferences and keep export of the complete legacy archive available.

**Tech Stack:** Android Java, `android.util.JsonReader`/`JsonWriter`, SQLite read-only snapshot already provided by `LegacyRelvenVault`, `RelvenWorkoutImporter`, `WorkoutSerializer`, AndroidX Preference, Robolectric/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-legacy-relven-data-migration-design.md`

## Global Constraints

- Keep the Play application ID `se.hampusandersson.relven` for phone and Wear release variants.
- Never write to or delete `relven-workouts.db`; never replace the private legacy archive during migration.
- Import only `cached_workout.documentJson` rows accepted by `RelvenWorkoutImporter` and eligible completed running activity documents with valid route samples.
- Do not replay credentials, server operations, schedule edits, or activate plans.
- Preserve the complete source identity: server origin, owner user ID, workout ID, and revision.
- Keep all migration work local and offline; add no paid service or required backend.
- Do not use the owner's physical Android device or ADB before all product phases are complete and the app is ready for manual testing.
- Do not describe unsupported activities, plans, calendar data, workout steps, extra-recovery intervals, or pending operations as migrated.

## Review Focus

- Legacy schema versions can omit optional tables or columns and can contain unknown tables; `archivePreviewHandlesOlderSourceVersionAndMissingOrUnknownTables` pins safe counting and skip behavior in Task 1.
- Snapshot JSON can be truncated, malformed, or have column order different from assumptions; `archivePreviewRejectsMalformedInputAndUsesColumnNames` pins fail-closed parsing in Task 1.
- Rows can share a workout ID while differing by origin, owner, or revision; `importKeepsDistinctCompositeIdentities` pins independent imports in Task 2.
- A process can stop between file publication and manifest update, or a user can edit an imported file; `importResumesAfterInterruptedPublish` and `importDoesNotOverwriteChangedOrUserFiles` pin recovery in Task 2.
- Archives can contain many activity rows, unsupported workout documents, encounter low storage, or have committed rows in a WAL sidecar; `archivePreviewStreamsActivityRows`, `reviewReportsImportFailureWithoutSuccess`, and `legacyArchiveIncludesCommittedWalRowsReadOnly` pin bounded-memory analysis and safe recovery in Tasks 1 and 3.
- Activity documents may be malformed, lack route samples, have invalid timestamps, contain non-monotonic samples, or repeat through cached and outbox rows; `activityImportRejectsUnsupportedDocumentsAndIsIdempotent` and `activityImportStoresRouteAndPauseMarkersTransactionally` pin eligibility, deduplication, and atomic persistence in Task 3.

---

### Task 1: Stream and summarize the legacy archive

**Files:**
- Create: `app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java`
- Modify if required by the WAL test: `app/src/main/org/runnerup/relven/migration/LegacyRelvenVault.java`
- Test: `app/test/java/org/runnerup/relven/migration/LegacyRelvenMigrationTest.java`
- Test: `app/test/java/org/runnerup/relven/migration/LegacyRelvenVaultTest.java`
- Use: `app/src/main/org/runnerup/relven/migration/LegacyRelvenVault.java`
- Use: `app/src/main/org/runnerup/workout/RelvenWorkoutImporter.java`

**Interfaces:**
- Consumes: `LegacyRelvenVault.captureIfPresent(Context)`, `LegacyRelvenVault.snapshotFile(Context)`, and `RelvenWorkoutImporter.parse(String) -> Workout`.
- Produces: `LegacyRelvenMigration.preview(Context) -> Preview`, throwing `IOException` when no valid archive can be read. `Preview` exposes `supportedWorkoutCount`, `unsupportedWorkoutCount`, `supportedActivityCount`, `unsupportedActivityCount`, `planCount`, `activityCount`, `activeSessionCount`, `scheduleChangeCount`, and `pendingOperationCount` through read-only accessors. It retains no activity or source-document payloads.
- Produces: a shared `LegacyRelvenActivityDocument` parser/model with validated eligibility and mapped route/pause values. Preview and the activity importer use the same parser so eligibility counts cannot drift from persisted conversions.
- Archive rows are decoded by matching each value to its name in that table's `columns` array. Unknown tables and unused fields are skipped. Required legacy workout identity fields are `serverOrigin`, `ownerUserId`, `workoutId`, `revision`, and `documentJson`.
- `planCount` counts `cached_plan` rows; `scheduleChangeCount` sums the JSON array lengths in their `scheduleMovesJson` and `scheduleSkipsJson`; `activityCount` counts unique source activity documents across `cached_activity` and `activity_outbox`; `activeSessionCount` counts `active_workout`; `pendingOperationCount` counts only rows whose `syncState` or `state` is `pending` in `activity_outbox`, `plan_mutation`, both schedule outboxes, and `activity_lifecycle_outbox`.

- [x] **Step 1: Write failing tests**

Add synthetic archive fixtures covering a supported v3 running workout, an importer-rejected workout, eligible and ineligible completed running activity documents, row values with mixed JSON types, a source database version below 18, missing optional tables and columns, an unknown table, and activity rows whose payloads must not appear in the preview. Include `cached_plan` with move/skip JSON arrays, `active_workout`, `cached_activity`, `activity_outbox`, `plan_mutation`, schedule move/skip outboxes, and `activity_lifecycle_outbox`; assert each appears in its exact count category. Add malformed/truncated archive and reordered-column tests. Name the cases `archivePreviewHandlesOlderSourceVersionAndMissingOrUnknownTables`, `archivePreviewStreamsActivityRows`, and `archivePreviewRejectsMalformedInputAndUsesColumnNames`. In `LegacyRelvenVaultTest`, add `legacyArchiveIncludesCommittedWalRowsReadOnly`: put the source database in WAL mode, commit a row, snapshot while the WAL is present, and assert the row is archived while the source stays unchanged. Assert exact category counts, supported versus unsupported classification, and `IOException` on malformed input.

- [x] **Step 2: Run the tests and verify the expected failures**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenMigrationTest --tests org.runnerup.relven.migration.LegacyRelvenVaultTest`

Expected: tests fail because `LegacyRelvenMigration` and `Preview` do not exist.

- [x] **Step 3: Implement streaming archive analysis**

Implement `preview(Context)` with `JsonReader`; validate archive `schemaVersion == 1` and `sourceDatabase == "relven-workouts.db"`, count legacy categories, resolve columns by name, and invoke `RelvenWorkoutImporter` one workout document at a time. Add `LegacyRelvenActivityDocument.parse(String)` and use it to classify and decode eligible activities: `outcome == completed`, supported running sport, valid start/end instants, active duration, and two or more finite coordinate samples with strictly increasing elapsed offsets. Deduplicate cached and outbox representations by stable source identity. Treat missing optional tables as zero, unknown tables as preserved-but-not-interpreted, and an unparseable archive as an error. Do not retain activity JSON or all workout documents in memory. If the WAL regression test fails, make only the required read-only snapshot compatibility change in `LegacyRelvenVault`.

- [x] **Step 4: Run the focused tests and verify they pass**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenMigrationTest --tests org.runnerup.relven.migration.LegacyRelvenVaultTest`

Expected: all preview, malformed-input, and unknown-schema-table tests pass.

- [x] **Step 5: Commit Task 1**

```bash
git add app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java app/src/main/org/runnerup/relven/migration/LegacyRelvenVault.java app/test/java/org/runnerup/relven/migration/LegacyRelvenMigrationTest.java app/test/java/org/runnerup/relven/migration/LegacyRelvenVaultTest.java
git commit -m "feat: summarize legacy Relven data"
```

### Task 2: Stage and safely import validated workouts

**Files:**
- Create: `app/src/main/org/runnerup/relven/migration/LegacyRelvenMigrationStore.java`
- Modify: `app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java`
- Modify: `app/src/main/org/runnerup/workout/WorkoutSerializer.java`
- Test: `app/test/java/org/runnerup/relven/migration/LegacyRelvenMigrationStoreTest.java`

**Interfaces:**
- Consumes: `LegacyRelvenMigration.preview(Context) -> Preview` and the same streaming row reader from Task 1.
- Produces: `LegacyRelvenMigration.importWorkouts(Context) -> WorkoutImportResult`; the result reports imported, already imported, unsupported, conflicting, and failed counts. `LegacyRelvenMigrationStore` stages each accepted `Workout` outside the visible workout library and commits staged files with a private migration manifest.
- The manifest records the composite legacy identity, destination filename, and SHA-256 of the generated workout JSON. Destination names are deterministic from the composite identity and safe for `WorkoutSerializer.getFile`.
- Exact store boundary: `LegacyRelvenMigrationStore.stage(Context, SourceIdentity, Workout) -> StagedWorkout` writes one staged file outside the visible library; `LegacyRelvenMigrationStore.publish(Context, List<StagedWorkout>) -> StoreResult` reconciles the manifest and publishes the complete prepared batch. `StoreResult` reports imported, already imported, conflicting, and failed counts; `LegacyRelvenMigration` adds unsupported rows to form `ImportResult`. `SourceIdentity` contains `serverOrigin`, `ownerUserId`, `workoutId`, and `revision`.

- [x] **Step 1: Write failing tests**

Add Robolectric tests named `importKeepsDistinctCompositeIdentities`, `importIsIdempotent`, `importDoesNotOverwriteChangedOrUserFiles`, `importResumesAfterInterruptedPublish`, `importStorageFailureDoesNotPublishAnyWorkout`, and `importFailureLeavesLegacyArchiveAvailable`. Assert distinct origin/owner/revision rows produce distinct files and full identities in the manifest, a repeat imports zero additional files, changed destination contents remain untouched and are reported as conflicts, an interrupted publish is safely reconciled, and storage errors do not remove the archive or claim success.

- [x] **Step 2: Run the tests and verify the expected failures**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenMigrationStoreTest`

Expected: tests fail because the migration store and `importSupported` do not exist.

- [x] **Step 3: Implement staged, idempotent publication**

Add `WorkoutSerializer.writeJSON(Writer, Workout)` as a public serialization entry point and close writers in `writeFile`. Implement a private staging directory, deterministic safe filenames, a hash-checked manifest, and atomic manifest replacement. Validate and stage every supported workout before publishing any library file. On restart, reconcile pending manifest entries by comparing destination hashes; never overwrite a pre-existing or changed file. Keep source identity and original content in the archive.

- [x] **Step 4: Run the focused tests and verify they pass**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenMigrationStoreTest`

Expected: identity, duplicate, conflict, interruption, and storage failure tests pass.

- [x] **Step 5: Commit Task 2**

```bash
git add app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java app/src/main/org/runnerup/relven/migration/LegacyRelvenMigrationStore.java app/src/main/org/runnerup/workout/WorkoutSerializer.java app/test/java/org/runnerup/relven/migration/LegacyRelvenMigrationStoreTest.java
git commit -m "feat: import legacy Relven workouts safely"
```

### Task 3: Import eligible completed running activities

**Files:**
- Create: `app/src/main/org/runnerup/relven/migration/LegacyRelvenActivityImporter.java`
- Modify: `app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java`
- Use: `app/src/main/org/runnerup/db/entities/ActivityEntity.java`
- Use: `app/src/main/org/runnerup/db/entities/LocationEntity.java`
- Use: `app/src/main/org/runnerup/db/DBHelper.java`
- Test: `app/test/java/org/runnerup/relven/migration/LegacyRelvenActivityImporterTest.java`

**Interfaces:**
- Consumes validated `LegacyRelvenActivityDocument` values and owner/origin/server/client identifiers from `cached_activity` and `activity_outbox` archive rows.
- Produces `LegacyRelvenActivityImporter.importActivities(Context) -> ActivityImportResult`, with imported, already imported, unsupported, and failed counts. Each created RunnerUp activity is committed with its route points and a migration source-identity ledger entry in one `runnerup.db` transaction.
- The source key is `clientActivityId` when present, otherwise `(serverOrigin, ownerUserId, serverActivityId)`. Client ID remains stable as outbox rows later receive server IDs or optional owner metadata, so cached/outbox copies deduplicate correctly. Retain origin, owner, and server ID as auxiliary provenance in the ledger. The original activity JSON remains only in the immutable archive.
- Map only completed running documents with valid ISO instants, active duration, and at least two finite coordinate samples in strictly increasing elapsed-time order. Reject routes containing a segment faster than 15 m/s to avoid implausible GPS spikes. Use `activeDurationSeconds` for RunnerUp activity duration, derive distance by summing geodesic route segments, and set route-point timestamps to `startedAt + elapsedMilliseconds`. Insert a route start and end point. Translate valid `technical_pause` intervals to ordered pause/resume points at the nearest route coordinates. Do not claim that extra-recovery intervals or workout steps are represented in RunnerUp history.

- [x] **Step 1: Write failing conversion and persistence tests**

Use synthetic schema-v18 archive fixtures only. Add tests named `activityImportMapsRunningSummaryAndRoute`, `activityImportMapsTechnicalPauseMarkers`, `activityImportRejectsUnsupportedDocumentsAndIsIdempotent`, and `activityImportStoresRouteAndPauseMarkersTransactionally`. Cover start/duration/sport/name/distance, timestamp offsets, altitude/speed/accuracy, pause/resume event ordering, cached/outbox deduplication, route-less and malformed documents, aborted outcomes, invalid coordinates, non-monotonic sample times, >15 m/s route spikes, transaction rollback on location or ledger insertion failure, and repeat import. Assert the legacy archive remains readable and the source database is never opened for writes.

- [x] **Step 2: Run the tests and verify the expected failures**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenActivityImporterTest`

Expected: tests fail because the activity importer and transactional source ledger do not exist.

- [x] **Step 3: Implement validated mapping and idempotent transaction**

Consume only `LegacyRelvenActivityDocument` instances accepted by the shared parser from Task 1. Compute distance with Android's geodesic distance function, rejecting non-finite or implausible segments rather than silently corrupting totals. Create `ActivityEntity` with the supported running sport, active duration, route-derived distance, and a safe workout label. Create required `LocationEntity` rows with ordered start/GPS/pause/resume/end types, epoch-millisecond timestamps, lap 0, and available altitude/speed/accuracy values. Do not fabricate heart rate or cadence. Add a private migration-ledger table to `runnerup.db` keyed by the composite source identity and insert the activity, location rows, and ledger entry in one transaction. On duplicate ledger identity, report already imported; roll back everything on any insert error.

- [x] **Step 4: Run the focused importer tests**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.relven.migration.LegacyRelvenActivityImporterTest`

Expected: route mapping, pause markers, unsupported classification, duplicate detection, and rollback tests pass.

- [x] **Step 5: Commit Task 4**

```bash
git add app/src/main/org/runnerup/relven/migration/LegacyRelvenActivityImporter.java app/src/main/org/runnerup/relven/migration/LegacyRelvenMigration.java app/test/java/org/runnerup/relven/migration/LegacyRelvenActivityImporterTest.java
git commit -m "feat: import eligible legacy Relven activities"
```

### Task 4: Add the review/import flow and document limits

**Files:**
- Modify: `app/src/main/org/runnerup/view/SettingsMaintenanceFragment.java`
- Modify: `app/res/xml/settings_maintenance.xml`
- Modify: `app/res/values/pref_keys.xml`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Test: `app/test/java/org/runnerup/view/SettingsMaintenanceFragmentTest.java`
- Modify: `docs/migration.md`
- Modify: `docs/implementation-status.md`

**Interfaces:**
- Consumes: `LegacyRelvenMigration.preview(Context) -> Preview`, `LegacyRelvenMigration.importWorkouts(Context) -> WorkoutImportResult`, `LegacyRelvenActivityImporter.importActivities(Context) -> ActivityImportResult`, and the existing legacy archive export action.
- Produces: a Maintenance preference visible when legacy data is available; a review dialog with workout and activity eligibility counts and explicit Import/Cancel actions; background import with progress; and a completion/error message with the complete archive export still available.

- [x] **Step 1: Write failing UI tests**

Add Robolectric tests named `legacyReviewCancelLeavesWorkoutLibraryUnchanged`, `legacyReviewConfirmImportsAndShowsCounts`, and `reviewReportsImportFailureWithoutSuccess`. Assert the review explains that workouts are copied locally, eligible activities appear in history, plans are not activated, Cancel writes no workout/activity/ledger entry, confirm reports exact result counts, failure is visible and offers retry, and the archive export preference remains available.

- [x] **Step 2: Run the tests and verify the expected failures**

Run: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.view.SettingsMaintenanceFragmentTest`

Expected: tests fail because the review/import preference and flow do not exist.

- [x] **Step 3: Implement the Maintenance flow and copy**

Add the review/import preference beside the existing archive export preference and show it when `LegacyRelvenVault.isAvailable(Context)` is true. Run preview and both importers sequentially on a single background executor; keep dialogs and feedback on the main thread. Show separate supported/unsupported workouts and activities, plans, active sessions, schedule changes, and pending operations. Require the explicit Import action. Localize new copy in English and Swedish. Keep export available after import and retain the archive on all error paths. Update migration and implementation status docs to state exactly which data is imported and which remains archived, and replace the stale stacked-PR status at the top of `implementation-status.md` with the current merged milestone state.

- [x] **Step 4: Run UI tests and the full local verification suite**

Run focused UI test: `.\gradlew.bat :app:testLatestDebugUnitTest --tests org.runnerup.view.SettingsMaintenanceFragmentTest`

Run full verification: `.\gradlew.bat spotlessApply :app:assembleLatestDebug :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck`

Expected: all three UI tests and the full suite pass; lint reports no new issues; Spotless passes; phone debug APK assembles. Confirm the diff contains no package ID, release key, or old-database write/delete changes. Do not run ADB or use a physical device.

- [x] **Step 5: Commit Task 4**

```bash
git add app/src/main/org/runnerup/view/SettingsMaintenanceFragment.java app/res/xml/settings_maintenance.xml app/res/values/pref_keys.xml common/src/main/res/values/strings.xml common/src/main/res/values-sv/strings.xml app/test/java/org/runnerup/view/SettingsMaintenanceFragmentTest.java docs/migration.md docs/implementation-status.md
git commit -m "feat: review and migrate legacy Relven workouts"
```

## Final integration gate

After all tasks pass, obtain one independent review of the complete branch, resolve actionable findings, verify the branch contains only this migration milestone, and open one PR against `master`. Wait for the complete repository CI workflow to pass, merge that PR, and verify there is still no more than one open PR before starting another milestone. Do not use the existing upload key or test backup for the eventual upgrade test until the remaining product phases are complete and the app is ready for manual testing.
