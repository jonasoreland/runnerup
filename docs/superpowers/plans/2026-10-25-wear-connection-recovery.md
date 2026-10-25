# Wear connection recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover the Wear Data Layer connection after initial failures and let the user retry without losing a prepared workout.

**Architecture:** Keep `StateService` as the sole owner of Wear Data Layer connection lifecycle and expose a small connection state plus retry method. The waiting fragment observes that state and routes the explicit retry action to the service. Existing phone handshake, preparation ACK, prepared workout persistence, and RunnerUp recording authority remain unchanged.

**Tech Stack:** Java, Kotlin, Wearable Google Play Services Data Layer, Compose for Wear, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-25-wear-connection-recovery.md`

## Global Constraints

- Preserve phone and Wear release application ID `se.hampusandersson.relven`.
- Preserve existing durable prepared workout, session journal, and strength outbox data.
- Do not report transport connection as successful app-level handshake.
- Do not use physical Android-device ADB.
- Keep one Relven PR open at a time and work in the current checkout.

## Review Focus

- Repeated connection failures must not create unbounded or duplicate retry callbacks.
- Reconnecting must not erase a prepared workout or session event journal.
- Retry after connected must refresh state without disconnecting a healthy peer.
- The UI must distinguish transport from app handshake and GPS readiness.
- Destroying the service must prevent callbacks from reconnecting it later.

---

### Task 1: Retry policy

**Files:**
- Create: `wear/src/main/java/org/runnerup/service/WearConnectionRetryPolicy.java`
- Test: `wear/src/test/java/org/runnerup/service/WearConnectionRetryPolicyTest.java`

**Interfaces:**
- Produces `delayMillis(attempt: Int): long`, capped at 16 seconds, and `shouldRetry(isConnected: boolean, isConnecting: boolean): boolean`.

- [ ] Write tests for 1/2/4/8/16/16-second progression and connected/connecting stop conditions.
- [ ] Run the focused test and confirm it fails because the policy does not exist.
- [ ] Implement the pure policy.
- [ ] Run focused test and confirm it passes.

### Task 2: StateService lifecycle and retry action

**Files:**
- Modify: `wear/src/main/java/org/runnerup/service/StateService.java`
- Test: `wear/src/test/java/org/runnerup/service/WearConnectionRetryStateTest.java`

**Interfaces:**
- `StateService.getConnectionState()` returns a `ValueModel<WearConnectionState>`.
- `StateService.retryConnection()` retries immediately or forces a read if connected.

- [ ] Add pure state-transition tests for initial connect, suspension/failure, reconnection, explicit retry, and service stop.
- [ ] Run focused tests and verify red.
- [ ] Publish connection state in `onCreate`, callbacks, reconnect, and `onDestroy`.
- [ ] Schedule one bounded retry callback; cancel it on success and service destruction.
- [ ] On success, refresh the existing Data Layer snapshots and durable outboxes.
- [ ] Verify focused tests pass.

### Task 3: Wear waiting and prepared UI

**Files:**
- Modify: `wear/src/main/java/org/runnerup/view/ConnectToPhoneFragment.java`
- Modify: `wear/src/main/java/org/runnerup/view/MainActivity.java`
- Modify: `wear/src/main/kotlin/org/runnerup/wear/ui/WearConnectWaitingScreen.kt`
- Test: `wear/src/test/kotlin/org/runnerup/wear/ui/WearConnectWaitingLayoutTest.kt`

**Interfaces:**
- Waiting screen accepts actual connection state and an `onRetry` callback.

- [ ] Add presentation tests for connecting, disconnected, and connected copy, confirming it does not claim handshake verification.
- [ ] Show a touch-safe “Försök igen” action only when retry is useful.
- [ ] Preserve workout name/step and show transport state as a separate line.
- [ ] Verify on compact and normal Wear layouts with existing layout-test conventions.

### Task 4: Verification and integration

- [ ] Run focused retry and UI tests.
- [ ] Run `:common:testDebugUnitTest :app:testLatestDebugUnitTest :wear:testDebugUnitTest`.
- [ ] Run app/Wear lint, both APK assemblies, Spotless, and `git diff --check`.
- [ ] Review manifest/package identity and changes for session-state/data deletion.
- [ ] Open one PR, wait for CI, merge, fast-forward the same checkout to `master`, and build phone and Wear debug APKs from that master.
- [ ] Report runtime emulator result without claiming bidirectional messaging if no compatible paired emulator is available.
