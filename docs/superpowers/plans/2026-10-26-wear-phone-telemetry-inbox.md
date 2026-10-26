# Wear Phone Telemetry Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reliably transfer watch GPS/heart-rate samples into a durable, duplicate-safe phone inbox and acknowledge only samples committed for the active RunnerUp activity.

**Architecture:** Keep the Wear `WorkoutTelemetryOutboxStore` authoritative until the phone commits a contiguous sequence. Associate the prepared session ID with the phone Tracker activity when recording starts, persist sample payloads and the receipt cursor in RunnerUp's existing SQLite database, and use the existing verified Wear peer and message listener to receive batches and return ACKs. This PR stops at the durable inbox; chronological route reconciliation is the next serial PR so it can separately review and test the existing activity-route consumers.

**Tech Stack:** Kotlin/Java Android, existing Google Wearable MessageClient, `WorkoutTelemetryProtocol`, `WorkoutTelemetryOutboxStore`, RunnerUp `TrackerWear`, and the existing SQLite `DBHelper`.

**Spec:** `docs/superpowers/specs/2026-10-26-wear-offline-telemetry-design.md` (delivery sequence 3); original user requirements are in `C:\Users\hampe\.codex\attachments\3e9d326f-aa21-409f-8ace-5b523cba96a4\Inklistrad text.txt`.

## Global Constraints

- Preserve Play application ID `se.hampusandersson.relven` and existing workout/history records.
- Keep one PR open at a time, merge it before starting the next, then rebuild phone and Wear debug APKs from updated `master`.
- Do not use physical phone/watch ADB before all phases are complete and ready for manual testing.
- Keep `WorkoutSessionProtocol` as the sole workout state machine and `Tracker` as the phone activity authority.
- Commit every accepted batch before ACK; accept only contiguous per-session sequences; never ACK unknown or uncommitted samples.
- Keep the outbox bounded to 64 samples and 32 KiB per message.

## Review Focus

- A lost ACK replays the same samples without advancing or duplicating phone receipt.
- A same-sequence conflicting payload is rejected without ACK.
- A batch with a gap is rejected without advancing the contiguous cursor.
- An unknown session cannot create an orphan session/activity or receive an ACK.
- Old offline samples within the activity window remain acceptable after reconnection; pre-session and implausibly future samples are rejected.
- An unverified or disconnected node cannot submit data or receive an ACK.

### Task 1: Persist session-to-activity association

**Files:**
- Modify `app/src/main/org/runnerup/db/DBHelper.java` to ensure the compatible inbox schema on database open and create.
- Create `app/src/main/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStore.kt` for session association and committed-sample storage.
- Modify `app/src/wear/org/runnerup/tracker/component/TrackerWear.java` to register the current `requestedSessionId` against `tracker.getActivityId()` only after the phone Tracker enters STARTED or PAUSED.
- Test `app/test/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStoreTest.kt`.

**Interfaces:**
- `WorkoutTelemetryInboxStore.associateActivity(database, sessionId, activityId, activityStartTimeMillis): AssociationResult` is idempotent for the same mapping and rejects conflicting mappings.
- The session table stores session ID, activity ID, start time, and highest contiguous committed sequence. The sample table uses `(session_id, sequence)` as its primary key and stores the exact one-sample protocol encoding for duplicate comparison.

- [x] Write tests for first association, same mapping replay, conflicting activity mapping, invalid IDs, and process reopen.
- [x] Run the focused test and confirm it fails because the store/schema behavior is missing.
- [x] Implement the schema with `CREATE TABLE IF NOT EXISTS` through the existing `DBHelper.onCreate`/`onOpen` compatibility pattern; do not bump the RunnerUp DB version for additive tables.
- [x] Run the focused test and verify existing DB rows are unaffected.

### Task 2: Transactional phone batch acceptance

**Files:**
- Modify `app/src/wear/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStore.kt`.
- Test `app/test/kotlin/org/runnerup/relven/telemetry/WorkoutTelemetryInboxStoreTest.kt`.

**Interfaces:**
- `commitBatch(database, batch, nowMillis): CommitResult` returns `COMMITTED`, `DUPLICATE`, `UNKNOWN_SESSION`, `GAP`, `CONFLICT`, `INVALID_TIME`, or `STORAGE_ERROR`, plus the contiguous committed-through sequence only for committed/duplicate outcomes.
- Accept a sample only when its wall time is no earlier than the associated activity start minus 120 seconds and no later than `nowMillis + 60 seconds`. Do not reject merely because an offline batch arrives hours after its recorded time.
- Compare stored one-sample encodings for sequences at or below the cursor. A conflicting duplicate or missing stored sequence returns an error without changing data or cursor.
- Insert all new samples and advance the cursor in one SQLite transaction. New samples must begin exactly at `committedThrough + 1`.

- [x] Add tests for ordered commit, all-duplicate replay, identical partial overlap, conflicting duplicate, missing expected sequence, unknown session, pre-start/future time, storage rollback, and reopen persistence. Wire-format batches themselves are contiguous, so the missing-sequence case starts at the wrong expected sequence.
- [x] Run the focused test and confirm it fails while the inbox API is missing.
- [x] Implement transactional validation and storage.
- [x] Run the focused suite and assert rejected batches leave both cursor and rows unchanged.

### Task 3: Wear send/retry and verified phone receive/ACK

**Files:**
- Modify `common/src/main/java/org/runnerup/common/util/Constants.java` for telemetry batch and ACK paths plus an internal telemetry-available action.
- Modify `wear/src/main/java/org/runnerup/service/StateService.java` to send the next pending bounded outbox batch, retry after reconnect/lost ACK, and compact only after a matching phone ACK.
- Modify `common/src/main/java/org/runnerup/common/wear/WorkoutTelemetryOutboxStore.java` and its tests to durably enumerate sessions with pending samples, so reconnect after a later workout still sends older offline telemetry.
- Modify `wear/src/main/java/org/runnerup/service/WearExerciseRecorderService.kt` to notify `StateService` after a sample is durably appended.
- Modify `app/src/wear/org/runnerup/relven/strength/StrengthResultListenerService.kt` to receive batches on its existing single-thread executor, require a verified connected peer, commit before ACK, and dispatch ACKs back to Wear.
- Modify `app/AndroidManifest.xml` to route the telemetry and ACK Wear Data Layer paths to the verified phone listener.
- Modify `app/AndroidManifest.xml` to route the telemetry and ACK Wear Data Layer paths to the verified phone listener.
- Test pure delivery/ACK policy in `common/src/test/java/org/runnerup/common/wear/WorkoutTelemetryDeliveryTest.java` and phone inbox receiver policy in app unit tests.

**Interfaces:**
- Wear sends `WorkoutTelemetryProtocol.encodeBatch(batch)` at `MSG_WORKOUT_TELEMETRY` and receives `encodeAck(ack)` at `MSG_WORKOUT_TELEMETRY_ACK`.
- `WorkoutTelemetryOutboxStore.pendingSessionIds()` enumerates durable session IDs; `pendingBatch(sessionId, MAX_BATCH_SAMPLES, MAX_PAYLOAD_BYTES)` is the only source for each transport payload.
- Phone ACK is sent only after `commitBatch` returns `COMMITTED` or `DUPLICATE`; all other outcomes remain unacknowledged and are logged without leaking payload contents.
- A matching ACK advances the outbox, then immediately sends the next bounded batch. Reconnection and local sample-available notifications restart delivery.

- [x] Test delivery selection, matching ACK compaction, wrong-session/wrong-cursor ACK rejection, duplicate ACK, and retry after a lost ACK.
- [x] Run focused tests; outbox recovery and phone inbox APIs were initially absent.
- [x] Implement Wear send/retry and phone commit-before-ACK using the existing peer verification gate. Session indexing migrates older pending outbox files by reading their embedded session IDs.
- [x] Run common, Wear, and app unit tests; no physical ADB was used.

### Task 4: Integration verification and PR readiness

- [x] Run `:common:testDebugUnitTest`, `:wear:testDebugUnitTest`, `:app:testLatestDebugUnitTest`, `:wear:lintDebug`, `:app:lintLatestDebug`, both debug APK assemblies, `spotlessCheck`, and `git diff --check` after final edits.
- [x] Inspect the final diff, confirm no additional Relven PR is open, and self-review transport trust, transactional commit, and sequence recovery against Review Focus.
- [ ] Commit and open exactly one PR; wait for hosted CI and merge before beginning route-reconciliation work.
- [ ] After merge, fast-forward `master` and rebuild both APKs from that commit.

## Next serial PR

Implement chronological reconciliation of the now-durable phone inbox into the existing RunnerUp activity route. Audit every route/summary/export consumer and make GPS gap-fill idempotent. Recompute summaries and Health Connect export safely for late batches. This scope is intentionally deferred from this inbox PR so a data-ordering failure cannot be hidden inside transport work.
