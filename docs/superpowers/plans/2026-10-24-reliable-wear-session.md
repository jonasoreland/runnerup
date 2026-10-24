# Reliable Phone ↔ Wear OS Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one verified, recoverable phone/watch workout session flow while preserving the RunnerUp tracker and phone-only workouts.

**Architecture:** Add a versioned Wear Data Layer request/ack protocol, then use it for workout transfer and state transitions. Preserve RunnerUp `Tracker` as the sole phone execution engine and introduce stable shared session identity, explicit authority, persistence/outbox, and a phone projection rather than a parallel tracker.

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
- [x] Verify build variants, package IDs, and listener manifest merge; run focused then full app/Wear tests and lint/build checks.
- [x] Apply review fixes: test source/path/request/version filtering, single terminal completion, timeout/late response handling, unique request IDs, and stale Settings callback invalidation.
- [ ] Review, create exactly one PR, wait for checks, merge, then rebuild both debug APKs from updated master.

### Task 2 — Workout preparation and transfer

- [ ] Define canonical bounded workout/session envelope using current plan/workout contracts and stable session ID.
- [ ] Implement prepare/ready/cancel messages, idempotent transfer acknowledgement, receiver validation, and phone-only continuation.
- [ ] Change selected-workout Start to preparation only; do not call `Tracker.start()` until an accepted start transition.
- [ ] Render watch preparation state with selected workout and truthful sensor/connection readiness; use supported visible continuation.
- [ ] Add transfer/lifecycle tests and merge as the next single PR after Task 1.

### Task 3 — Shared start, countdown and controls

- [ ] Model authority, session state, command IDs, monotonic planned start and duplicate/out-of-order rejection.
- [ ] Support start from either device, 3-second aligned countdown, pause/resume/lap/skip/finish and phone projection.
- [ ] Adapt existing RunnerUp Tracker to the authoritative commands without duplicate clocks or trackers.
- [ ] Test simultaneous/repeated start, latency, interruption during countdown and phone-only flow; merge serially.

### Task 4 — Automatic sensor/GPS preparation

- [ ] Start location acquisition during preparation after permission checks, expose permission/provider/search/fix/accuracy states, and continue without a fix.
- [ ] Ensure preparatory fixes do not enter the recorded session; begin route acceptance at the authoritative start instant.
- [ ] Prepare Wear HR using the existing sensor service and report genuine readiness.
- [ ] Remove the nonfunctional manual Start GPS controls and test permission/lifecycle cases; merge serially.

### Task 5 — Durable disconnect recovery and results

- [ ] Persist accepted session events locally with stable session/event IDs, sequence numbers and authority.
- [ ] Buffer watch events offline, replay idempotently, reject stale/conflicting commands, and provide deliberate ownership transfer only after acknowledgement.
- [ ] Sync set/repetition results through the existing strength-performance system, preserving left/right and confirmation semantics.
- [ ] Persist completion before adding one Health Connect outbox record; test no duplicate exports/history sessions.
- [ ] Verify process restart/disconnect/reconnect and merge as the next single PR.

### Task 6 — End-to-end emulator verification

- [ ] Configure or discover a paired Wear OS + phone emulator without touching physical ADB targets.
- [ ] Exercise companion handshake, transfer, prepare, both-device start, active commands, disconnect, recovery, final persistence and Health Connect queue.
- [ ] Capture actual screenshots and compare each state to Relven's existing design system.
- [ ] Run final test/build/lint matrix, review incomplete hardware-only limitations, merge, and rebuild APKs from master.

## Verification

For each PR, run the smallest relevant tests first, then complete `:app:testLatestDebugUnitTest`, `:wear:testDebugUnitTest`, the requested debug APK assemblies, and applicable lint. For final integration, use explicit emulator serials only and confirm `ro.kernel.qemu=1` before interaction. Read every command's exit status and output before reporting.
