# Wear OS Offline Workout Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve watch-collected GPS and heart-rate telemetry across phone disconnection and reconcile it into the existing single workout session.

**Architecture:** Keep `WorkoutSessionProtocol` as the only workout state machine and clock. Use Wear OS Health Services `ExerciseClient` for sensor samples, a foreground service for lifecycle-safe capture, a bounded local watch outbox for offline persistence, and an idempotent phone inbox that reconciles gaps into RunnerUp's existing activity.

**Tech Stack:** Java/Kotlin Android Wear OS, `androidx.health:health-services-client`, existing `common` Wear session protocol, SharedPreferences journal patterns, RunnerUp tracker and SQLite persistence.

**Spec:** `docs/superpowers/specs/2026-10-26-wear-offline-telemetry-design.md`; parent user requirement: `C:\Users\hampe\.codex\attachments\3e9d326f-aa21-409f-8ace-5b523cba96a4\Inklistrad text.txt`.

## Global Constraints

- Preserve Play application ID `se.hampusandersson.relven` and existing workout history.
- Do not introduce a second workout state machine, timer, or activity identity.
- Keep one PR at a time, merge before starting the next, and rebuild phone and Wear debug APKs from updated master after each merge.
- Never use physical-device ADB until all project phases are complete and the app is ready for manual testing.
- Persist telemetry before send; only a contiguous phone-commit acknowledgement may advance the watch outbox.
- Respect Wear OS health permission and foreground-service differences before/after API 36.

## Review Focus

- A duplicate or delayed batch must not create duplicate route points or activity records.
- A missing middle sequence must not be acknowledged as committed.
- A partial final outbox write must not discard earlier complete samples.
- A stale monotonic sample or invalid accuracy must not create a plausible-looking route or pace.
- Missing permission, GPS, Health Services, or heart-rate sensor must remain an honest unavailable state while the workout lifecycle stays usable.

## Task 1 — Versioned telemetry contract and durable watch outbox (this PR)

**Files:**
- Create `common/src/main/java/org/runnerup/common/wear/WorkoutTelemetryProtocol.java`.
- Create `common/src/main/java/org/runnerup/common/wear/WorkoutTelemetryOutbox.java` and `WorkoutTelemetryOutboxStore.java`.
- Create `common/src/test/java/org/runnerup/common/wear/WorkoutTelemetryProtocolTest.java` and `WorkoutTelemetryOutboxTest.java`.
- Add Android-backed `Storage` adapter under `wear/src/main/java/org/runnerup/service/`.

**Interfaces:**
- `Sample(sessionId, sequence, watchElapsedRealtimeNanos, wallTimeMillis, latitude?, longitude?, horizontalAccuracyMeters?, altitudeMeters?, speedMetersPerSecond?, heartRateBpm?)`.
- `Batch(sessionId, firstSequence, samples)` encodes/decodes protocol version 1 with a strict 64-sample and 32 KiB payload ceiling.
- `Ack(sessionId, committedThroughSequence)` identifies the highest contiguous committed sample.
- `Outbox.append(sample)`, `pendingBatch(maxSamples, maxBytes)`, and `acknowledge(ack)` preserve complete, unacknowledged samples and reject gaps/conflicts.
- `Store.Storage` provides append/read/truncate primitives for deterministic tests and a private app-files adapter in the Wear app.

- [x] **Step 1: Write failing codec tests** for GPS-only, HR-only, combined, optional measurements, malformed bounds, unknown protocol version, oversized payload, empty batch, and stable sequence identity.
- [x] **Step 2: Run focused tests and confirm each failure is caused by missing behavior.** `:common:testDebugUnitTest` first failed to compile because the protocol was absent.
- [x] **Step 3: Implement minimal sample/batch/ack encoding and validation.**
- [x] **Step 4: Write failing outbox tests** for ordered appends, bounded batching, duplicate/conflicting sequences, gap rejection, contiguous acknowledgement, lost-ACK replay, corrupt records, and partial-tail recovery.
- [x] **Step 5: Run focused tests and confirm expected failures.** Outbox tests exposed torn-tail recovery and storage-test isolation defects before the fixes.
- [x] **Step 6: Implement bounded append-only outbox and Wear file-backed storage.** Uses Android `AtomicFile` in app-private files; recovered complete records survive an incomplete final write.
- [x] **Step 7: Run common/Wear tests and inspect storage recovery and size limits.** `:common:testDebugUnitTest` passed (107 tests at that point).
- [x] **Step 8: Run app/Wear unit tests, APK assemblies, lint, formatting, and `git diff --check`.** `:app:testLatestDebugUnitTest`, `:wear:testDebugUnitTest`, `:common:lintDebug`, both debug APK assemblies, and `git diff --check` passed. PR review/merge remains outstanding.

## Task 2 — Health Services recorder and foreground lifecycle (next PR after Task 1 merges)

**Files:** `wear/build.gradle`, `wear/src/main/AndroidManifest.xml`, new `WearExerciseRecorderService`, Health Services adapter, permission/readiness state, and tests.

- Request only Health Services permissions needed for running LOCATION and HEART_RATE_BPM, respecting legacy API ≤35 and API ≥36 permission names.
- Prepare sensors without recording workout samples before the authoritative start instant.
- Start/pause/resume/end `ExerciseClient` only from accepted `WorkoutSessionProtocol` transitions.
- Persist every usable sample through Task 1 outbox before notifying the UI or sending it.
- Run as a foreground service with `health|location` types where required and a persistent localized notification.
- Cover process recreation, duplicate commands, missing permissions/provider, sensor absence, and Health Services termination.

## Task 3 — Durable phone inbox and route reconciliation (serial PR after Task 2)

**Files:** `app/src/wear` telemetry receiver, phone inbox storage, RunnerUp route merge and tests.

- Commit a telemetry batch durably before sending ACK.
- Reject session mismatch, duplicate conflicts, sequence gaps, stale/future timestamps, invalid location accuracy, and invalid heart rate.
- Preserve current phone Tracker samples; use watch samples only to fill confirmed coverage gaps, in chronological order.
- Recompute derived distance and pace from the merged accepted route; keep unknown values unknown and do not let low-quality points dominate metrics.
- Persist stable session IDs and deduplicate reconnect/replay so final history and Health Connect contain one activity.

## Task 4 — End-to-end QA

- Exercise phone/watch prepare, start, pause, reconnect, finish, and process recreation on supported paired emulators.
- Verify outbox redelivery after dropped ACK and exact one-time phone history persistence.
- Capture screenshots for active, disconnected, and recovered states.
- Rebuild both debug APKs after every serial merge; leave the physical phone/watch untouched until manual-testing readiness.
