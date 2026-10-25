# Wear GPS Readiness Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Project the phone Tracker's current GPS preparation state onto the watch for the matching prepared workout session.

**Architecture:** Keep GPS acquisition and recording on the phone. Encode the readiness state in a bounded common protocol and publish it as a per-session Wear Data Layer latest-value item. Persist and render the latest matching item on the watch, clearing it when preparation ends.

**Tech Stack:** Java common protocol, Kotlin Android/Compose, Google Play Services Wearable DataClient, JUnit/Gradle.

**Spec:** `docs/superpowers/specs/2026-10-25-wear-gps-readiness-design.md`

## Global Constraints

- Preserve package identity `se.hampusandersson.relven`.
- Do not create a second Tracker or persist GPS samples before active workout state.
- Poor, unavailable, or not-required GPS must not block starting a workout.
- Do not use physical Android devices through ADB.
- Work in the current checkout and keep at most one Relven PR open.

## Review Focus

- A delayed readiness value for a previous session must not replace the current session's state; test session correlation.
- A duplicate DataItem callback must not produce a false readiness transition; test latest-state idempotence.
- Null/invalid accuracy must remain unknown rather than become zero; test protocol value validation.
- Cancellation during asynchronous delivery must not restore stale UI; test store clear and session matching.
- GPS-free strength sessions must display “GPS behövs inte”; test rendering model mapping.

---

### Task 1: Define a versioned readiness value and Data Layer identity

**Files:**
- Create: `common/src/main/java/org/runnerup/common/wear/GpsPreparationProtocol.java`
- Test: `common/src/test/java/org/runnerup/common/wear/GpsPreparationProtocolTest.java`
- Modify: `common/src/main/java/org/runnerup/common/util/Constants.java`

**Interfaces:**
- Produces `GpsPreparationProtocol.Status`, immutable `Readiness(sessionId, revision, status, accuracyMeters)`, encode/decode helpers, and a stable path builder for a session.
- The wire payload carries protocol version, canonical UUID session ID, positive monotonically increasing revision, status enum, and optional finite positive accuracy bounded to a sensible maximum.

- [ ] Write tests for every status round-trip, optional accuracy, bad UUID/status/revision/accuracy, malformed payload, and per-session path uniqueness.
- [ ] Run `.\gradlew :common:testDebugUnitTest --tests org.runnerup.common.wear.GpsPreparationProtocolTest` and verify the new tests fail before implementation.
- [ ] Implement the bounded codec and add `MSG_GPS_READINESS` Data Layer path constant.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: Publish phone Tracker readiness as latest-value state

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/WorkoutPreparationActivity.kt`
- Modify: `app/src/wear/org/runnerup/relven/ui/WearConnectionProvider.java`
- Test: `app/test/kotlin/org/runnerup/relven/ui/GpsPreparationPresentationTest.kt`

**Interfaces:**
- `WearConnectionProvider.publishGpsReadiness(context, sessionId, revision, status, accuracyMeters)` writes the per-session DataItem and returns no workout/session mutation.
- `WearConnectionProvider.clearGpsReadiness(context, sessionId)` removes that DataItem.
- Activity publishes only when the presentation tuple changes and after session preparation is initiated; it clears on cancellation and completion.

- [ ] Add an app test for readiness presentation de-duplication, ensuring identical status/accuracy does not create another publication revision.
- [ ] Run the focused app unit test and verify it fails before implementation.
- [ ] Add an Activity-owned session publisher that maps current `GpsPreparationUiState` to the common protocol and increments revisions only for changes.
- [ ] Publish and clear through DataClient with bounded error logging; keep failures non-blocking to workout preparation.
- [ ] Re-run focused tests and inspect cancellation/recreation paths.

### Task 3: Receive, persist, and render readiness on Wear

**Files:**
- Create: `wear/src/main/java/org/runnerup/service/PreparedGpsReadinessStore.java`
- Modify: `wear/src/main/java/org/runnerup/service/ListenerService.java`
- Modify: `wear/src/main/java/org/runnerup/view/StartFragment.java`
- Modify: `wear/src/main/kotlin/org/runnerup/wear/ui/WearControlsScreen.kt`
- Test: `wear/src/test/java/org/runnerup/service/PreparedGpsReadinessStoreTest.java`
- Test: `wear/src/test/kotlin/org/runnerup/wear/ui/GpsReadinessPresentationTest.kt`

**Interfaces:**
- Store accepts a readiness payload only if its session ID matches the currently prepared workout and its revision is newer; exact duplicates are idempotent.
- Store exposes the latest readiness for a session and clears it on matching cancellation/replacement.
- Prepared workout Compose content receives a GPS label and presents it next to HR readiness without disabling Start.

- [ ] Test matching-session acceptance, stale revision rejection, old-session rejection, duplicate handling, and clear semantics.
- [ ] Test mapping of all common statuses to concise Swedish watch labels and safe unknown fallback.
- [ ] Run the two focused Wear tests and verify they fail before implementation.
- [ ] Handle DataItem updates/removals in ListenerService, validate the companion source and prepared session, persist accepted state, and refresh StartFragment.
- [ ] Update prepared workout UI and wiring to show actual GPS readiness while retaining the current HR status and start action.
- [ ] Re-run focused tests and verify clear/replacement behavior.

### Task 4: Verify the phone↔Wear GPS readiness increment

**Files:**
- Review all files above and inspect `docs/superpowers/specs/2026-10-25-wear-gps-readiness-design.md`.

- [ ] Run `.\gradlew :common:testDebugUnitTest :app:testLatestDebugUnitTest :wear:testDebugUnitTest :app:lintLatestDebug :wear:lintDebug :app:assembleLatestDebug :wear:assembleDebug spotlessCheck`.
- [ ] Inspect the diff for protocol scope, session filtering, lifecycle cleanup, UI readiness truthfulness, and physical-device ADB use.
- [ ] If paired Wear emulators are operational, verify latest-value synchronization and reconnect recovery; otherwise record the emulator blocker without claiming runtime delivery.
- [ ] Commit a single coherent PR, wait for it to merge, fast-forward `master`, and rebuild both debug APKs from that exact master commit before starting another PR.

## Self-review coverage

This increment covers GPS state projection and stale/cancelled session handling. It intentionally does not implement the separate larger-task requirements for GPS provider acquisition itself, workout ownership transfer, strength result synchronization, Health Connect completion gating, process restoration, or runtime pair verification. Those remain later serial PRs and must not be claimed complete here.
