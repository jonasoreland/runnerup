# Reliable Phone ↔ Wear OS Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one verified, recoverable phone/watch workout session flow while preserving the RunnerUp tracker and phone-only workouts.

**Architecture:** Add a versioned Wear Data Layer request/ack protocol, then use it for workout preparation, coordinated execution, recovery, GPS readiness, and performance synchronization. In connected mode the watch owns live session transitions while phone RunnerUp `Tracker` records the accepted workout route/activity as a projection; phone-only mode uses the same reducer with phone authority. Persist authority and events before enabling safe recovery or takeover.

**Tech Stack:** Android Kotlin/Java, Jetpack Compose/Wear Compose, Google Play Services Wearable Data Layer, existing Room/SharedPreferences/workout serializer and Gradle test infrastructure.

**Spec:** `docs/superpowers/specs/2026-10-24-reliable-wear-session-design.md`; user task is `C:\Users\hampe\.codex\attachments\3e9d326f-aa21-409f-8ace-5b523cba96a4\Inklistrad text.txt`.

## Global Constraints

- Retain Play application ID `se.hampusandersson.relven` and existing user data.
- Keep only one Relven PR open at a time and merge it before beginning the next.
- After each merge, build phone `:app:assembleLatestDebug` and Wear `:wear:assembleDebug` from `master`.
- Never use the user's physical phone or watch through ADB; emulator use is allowed.
- Do not add a required cloud dependency or a second workout engine.
- Connected-node presence and message-delivery tasks alone are not proof of companion handshake.
- Preserve legacy Wear HR and current activity transfer compatibility.

## Review Focus

- A connected node with no Relven companion must time out honestly rather than show a successful handshake. Test ping timeout and source/request correlation.
- Duplicate, delayed, or mismatched acknowledgements must not satisfy a different request. Test unique request IDs and protocol version validation.
- Starting or preparing must not activate GPS route recording. Test start-state and GPS boundary when implementing lifecycle phases.
- A phone/watch partition must not produce dual authorities or duplicate history. Test retries, ownership, and outbox reconciliation.
- A connected phone button must not mutate Tracker state until the watch accepts its request. Test each phone-origin control against the watch-authoritative sequence.
- Background activity launch is restricted. Verify a notification/user-visible continuation and ensure the app does not claim the watch UI opened on message delivery alone.

## Files / responsibilities

- `common/.../Constants.java`: stable Wear paths for new versioned protocol messages.
- New common protocol codec/model: bounded, versioned request/response fields and validation.
- `app/src/wear/.../WearConnectionProvider.java`: connected-node detection and correlated app-level ping/ack test.
- `app/src/nonwear/.../WearConnectionProvider.java`: explicit unsupported result for builds without Data Layer.
- `app/src/main/kotlin/.../RelvenSettingsScreens.kt` and `RelvenHomeActivity.kt`: truthful connection stages and test action.
- `wear/.../ListenerService.java` and `wear/src/main/AndroidManifest.xml`: background-capable ping response listener.
- New tests in common/app/wear for protocol codec, status mapping, and listener response behavior.
- Later PRs own preparation UI/transfer, coordinated session lifecycle, GPS/sensor readiness, outbox/recovery, and paired emulator verification.

## Tasks

### Task 1 — Companion handshake and truthful diagnostics (current PR)

- [x] Write failing protocol serialization/version/request-correlation tests and Settings status mapping tests.
- [x] Add bounded versioned ping/pong paths and codec; prove tests fail before implementation.
- [x] Implement watch listener pong response and phone test with timeout, matching source node and request ID.
- [x] Show transport presence separately from app handshake in Settings; add explicit connection-test action and RTT/status.
- [x] Advertise and query a Relven-specific Wear capability before pinging; distinguish connected-without-Relven from handshake timeout (capability-discovery PR).
- [x] Verify build variants, package IDs, and listener manifest merge; run focused then full app/Wear tests and lint/build checks.
- [x] Apply review fixes: test source/path/request/version filtering, single terminal completion, timeout/late response handling, unique request IDs, and stale Settings callback invalidation.
- [x] Review, create exactly one PR, wait for checks, merge, then rebuild both debug APKs from updated master.

### Task 2 — Workout preparation and transfer

- [x] Define canonical bounded workout/session envelope using current plan/workout contracts and stable session ID.
- [x] Implement prepare/ready/cancel messages, idempotent transfer acknowledgement, receiver validation, and phone-only continuation.
- [x] Change selected-workout Start to preparation only; do not call `Tracker.start()` until an accepted start transition.
- [x] Render watch preparation state with selected workout and truthful sensor/connection readiness; use supported visible continuation.
- [x] Add transfer/lifecycle tests and merge as the next single PR after Task 1.
- [ ] After a correlated `PREPARED` acknowledgement, request opening the watch UI with `RemoteActivityHelper` and a browsable Relven URI; preserve the prepared-workout notification as fallback, and treat request delivery as distinct from confirmed visible presentation (current PR).

### Task 3 — Shared start, countdown and controls

- [x] Initialize connected sessions with watch authority; initialize phone-only sessions with phone authority.
- [x] Make phone Start and active controls requests that stay pending until a matching watch-authoritative event arrives; retry idempotently while the request is in flight.
- [x] Make watch Start and active controls commit once on the watch reducer, then project the accepted event to phone.
- [x] Add two-way monotonic clock sampling; map the watch deadline to phone elapsed time before starting RunnerUp Tracker exactly once.
- [x] Expose distinct lap and skip actions where the workout supports interval advancement; route both to the correct existing RunnerUp operation.
- [x] Preserve/recover countdown presentation after Activity recreation from an authoritative session projection; never start on an unaccepted or expired command.
- [x] Test simultaneous/repeated starts, action projection, interruption, timing conversion, phone-only fallback and invalid/out-of-order requests; verify unit tests, both debug APK assemblies, lint and formatting before opening the serial PR.

### Task 4 — Automatic sensor/GPS preparation

- [x] Start location acquisition during preparation after permission checks, expose permission/provider/search/fix/accuracy states, and continue without a fix (current serial PR).
- [x] Ensure preparatory fixes do not enter the recorded session; begin route acceptance at the authoritative start instant (current serial PR).
- [x] Prepare Wear HR using the existing sensor service and report genuine readiness.
- [x] Remove the nonfunctional manual Start GPS controls and test readiness, accuracy, and route-persistence policies (current serial PR).

### Task 5 — Durable disconnect recovery and results (serial sub-PRs)

#### Task 5A — Durable authority event journal (merged PR #167)

- [x] Add a bounded, versioned journal keyed by stable session ID; validate authority, contiguous sequence, command ID and exact duplicate payload; persist events synchronously before projecting them.
- [x] Keep acknowledged events as the replayable state history, with an ordered unacknowledged suffix; ACK only the matching head and retry after reconnect without dropping events.
- [x] Restore the phone projection from the durable event history without changing WATCH authority; write the phone projection before acknowledging it.
- [x] Keep the current fail-safe guard against accepting watch controls while disconnected because RunnerUp recording remains phone-owned. This is an interim safety behavior and does not satisfy the requested watch-offline session recovery; replace it before the overall goal is complete.
- [x] Add tests for process recreation including pending-start deadline recovery, Tracker control reconciliation, persisted idempotency markers for LAP/SKIP, replay order, duplicate/conflicting/stale/gapped events, ACK ordering/idempotency, invalid storage and phone/watch authority mismatch.

#### Task 5B — Strength result synchronization

- [x] Define a bounded, stable-ID result message that preserves confirmed repetition/duration, left/right, timestamps and optional notes without treating prescription as performance.
- [x] Persist the watch and phone result outboxes before delivery; use exact result IDs to reject duplicates/conflicts and ACK only after the phone's existing `StrengthSessionStore` commits the result.
- [x] Reconcile after reconnect and preserve confirmed partial results when the session ends early; add protocol/store and existing strength history/export regression tests.
- [x] Add the Wear active strength-set UI and a no-keyboard quick logger that produces these result events; synchronize the watch execution position and rest timer with phone projection (PR #172).

#### Task 5C — Terminal persistence and Health Connect handoff

- [x] Ensure authoritative completion is durably represented before final phone activity/result persistence; retain retryable terminal state when phone persistence is unavailable (PR #173).
- [x] Queue Health Connect only from the finalized phone record through the existing idempotent outbox; test repeat completion, retry and restart do not create duplicate history or exports (PR #173).
- [x] Run combined phone/Wear tests, APK assemblies and lint; review and merge each sub-PR before starting the next, building both APKs from master after every merge (PRs #173–#176).

### Task 6 — End-to-end emulator verification

- [ ] Configure or discover a paired Wear OS + phone emulator without touching physical ADB targets.
- [ ] Exercise companion handshake, transfer, prepare, both-device start, active commands, disconnect, recovery, final persistence and Health Connect queue.
- [ ] Capture actual screenshots and compare each state to Relven's existing design system.
- [ ] Run final test/build/lint matrix, review incomplete hardware-only limitations, merge, and rebuild APKs from master.

## Verification

For each PR, run the smallest relevant tests first, then complete `:app:testLatestDebugUnitTest`, `:wear:testDebugUnitTest`, the requested debug APK assemblies, and applicable lint. For final integration, use explicit emulator serials only and confirm `ro.kernel.qemu=1` before interaction. Read every command's exit status and output before reporting.
