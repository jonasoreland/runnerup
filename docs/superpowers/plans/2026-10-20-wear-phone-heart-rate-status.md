# Phone-side Wear heart-rate status and recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Wear heart-rate connection state visible on the phone and restart sensor capture once when the stream becomes stale.

**Architecture:** A tested state object owns provider status and freshness deadlines. The Wear provider performs one bounded restart after stale data and clears callbacks on disconnect. Existing settings/start/workout status surfaces render the provider state, and repeated watch start commands safely re-register the sensor.

**Tech Stack:** Java, Android Handler, Wearable Data Layer, Android XML layouts, JUnit/Mockito.

**Spec:** `docs/superpowers/specs/2026-10-20-wear-phone-heart-rate-status.md`

## Global Constraints

- Preserve `se.hampusandersson.relven` for release.
- Keep RunnerUp as the phone-side workout engine.
- Do not use the user's phone or watch with ADB.
- Do not make heart rate mandatory for workout recording.
- Preserve 15-second sample freshness and the 30-second initial-sample timeout.
- Allow one automatic restart per stale period and expose manual retry after failure.

## Review Focus

- First-sample wait: retain the 30-second timeout and show connecting/unavailable states.
- Stale data boundary: do not restart before 15 seconds; restart once after expiry.
- Recovery timeout: stop the watch service and permit manual retry after 30 seconds without a valid sample.
- Delayed/out-of-order samples: valid samples restore connected state; invalid payloads do not.
- Disconnect/close during a pending retry: cancel callbacks and remain disconnected.

### Task 1: Connection-state model

**Files:** Create `hrdevice/src/main/org/runnerup/hr/WearHRConnectionState.java`; create `hrdevice/src/test/java/org/runnerup/hr/WearHRConnectionStateTest.java`.

- [x] Test initial, connect, sample, stale, retry timeout, recovery, and disconnect transitions.
- [x] Run the focused unit tests and confirm they fail because the state model is missing.
- [x] Implement the state model with an injected elapsed-time argument and a 15-second stale threshold.
- [x] Run the focused tests and confirm they pass.

### Task 2: Provider recovery and watch re-registration

**Files:** Modify `hrdevice/src/wear/org/runnerup/hr/WearHRProvider.java`, `wear/src/main/java/org/runnerup/hr/HeartRateService.java`; modify/add provider unit tests.

- [x] Write provider tests for stale restart, 30-second recovery timeout, valid sample recovery, and disconnect cancellation.
- [x] Run the focused tests and confirm expected failures.
- [x] Use the state model as the provider's status source; schedule freshness and timeout checks on the provider Handler.
- [x] Send one new HR start command when an established sample becomes stale. On recovery timeout send stop, remove the listener and expose `UNAVAILABLE`.
- [x] Cancel pending callbacks and send stop on disconnect/close.
- [x] Make a repeated watch start command unregister and re-register the existing sensor listener once.
- [x] Run provider/Wear unit tests and module builds.

### Task 3: Phone status surfaces

**Files:** Modify `app/res/layout/hr_settings.xml`, `app/res/layout/start.xml`, `app/res/layout/run.xml`, `app/src/main/org/runnerup/view/HRSettingsActivity.java`, `StartFragment.java`, and `RunActivity.java`; add English/Swedish strings.

- [x] Add tests for localized status resource mapping if mapping is extracted into a pure helper.
- [x] Show provider state in HR settings and existing pre-run/workout status areas. Preserve normal HR values when connected and existing non-Wear sensor UI.
- [x] Keep `Connect` usable for manual retry after `UNAVAILABLE`; disable it during `CONNECTING`/`RECONNECTING`.
- [x] Verify loading, connected, recovering, unavailable, and disconnected UI states by code inspection, localized mapping tests, local builds and lint. Visual device checks remain pending.

### Task 4: Documentation and PR gate

**Files:** Modify `docs/wear-hr-audit.md`, `docs/implementation-status.md`, and this plan.

- [ ] Document bounded automatic recovery and remaining physical-watch validation.
- [x] Run `:wear:assembleDebug :wear:assembleRelease :wear:testDebugUnitTest :hrdevice:testDebugUnitTest :app:testLatestDebugUnitTest :wear:lintDebug :hrdevice:lintDebug :app:lintLatestDebug spotlessCheck` and `git diff --check`.
- [x] Verify the Wear release artifact package is `se.hampusandersson.relven`.
- [ ] Open exactly one PR, wait for hosted checks, merge it, and verify the PR queue is empty before starting another increment.
