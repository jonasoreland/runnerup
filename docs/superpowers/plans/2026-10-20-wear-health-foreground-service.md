# Wear heart-rate foreground service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the existing watch heart-rate capture in an Android health foreground service only when the version-appropriate foreground and background permissions are granted.

**Architecture:** Keep the phone command protocol and direct SensorManager reader. Put permission mapping in a small pure helper, gate service starts before launch, promote valid capture to a foreground service with an ongoing notification, and stop foreground state with the existing remote stop command.

**Tech Stack:** Java, Android foreground services, AndroidX Core, Wearable Data Layer, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-wear-health-foreground-service.md`

## Global Constraints

- Preserve `se.hampusandersson.relven` for release.
- Keep RunnerUp as the phone-side workout engine.
- Do not access the user’s phone or watch with ADB during development.
- Background HR must remain disabled when required permissions are missing.
- Keep permission prompts/settings navigation behind a visible user action.

## Review Focus

- API 32/33 and 35/36 permission boundaries: unit-test each boundary.
- Foreground permission granted but background permission denied: service start is refused and the page explains how to enable access.
- Heart-rate permission revoked while app is stopped: service start is refused without crash.
- Service start with no sensor or no source node: no foreground notification or sensor listener remains active.
- Stop message during capture: unregister sensor and dismiss ongoing notification.

### Task 1: Versioned sensor access model

**Files:** Create `wear/src/main/java/org/runnerup/hr/HeartRateAccess.java`; create `wear/src/test/java/org/runnerup/hr/HeartRateAccessTest.java`; modify `wear/build.gradle` only if JUnit is not already configured.

- [x] Write tests for sensor and foreground permission names, API 32/33/35/36 background permission names, and readiness combinations.
- [x] Run focused tests and confirm expected failures for missing background-permission APIs/states.
- [x] Extend the pure SDK/permission readiness model.
- [x] Run the focused tests and confirm they pass.

### Task 2: User-visible background permission readiness

**Files:** Modify `wear/src/main/AndroidManifest.xml`, `HeartRatePermissionFragment.java`, `HeartRatePermissionState.java`, `heart_rate_permission.xml`; add English/Swedish strings.

- [x] Add manifest permissions with `maxSdkVersion=35` for legacy background access and the API 36 health background permission.
- [x] Extend tests for page state with background permission missing/granted.
- [x] Run focused tests and confirm the missing-background case fails before implementation.
- [x] Add a visible action: request API 36 background health permission; for API 33–35 open the app permission settings page and refresh on return.
- [x] Show readiness only when sensor, foreground, and background access are available; keep recording unaffected when the user declines.
- [x] Run Wear build, focused tests, and lint.

### Task 3: Health foreground service lifecycle

**Files:** Modify `wear/src/main/AndroidManifest.xml`, `HeartRateService.java`, and `HeartRateListenerService.java`; add notification resources/strings as needed.

- [x] Extend pure eligibility tests for missing permissions and supported API combinations.
- [x] Run focused tests and confirm expected failures.
- [x] Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH`, set service `foregroundServiceType="health"`, and create a low-priority notification channel.
- [x] Promote the service promptly before registering the sensor. On missing permissions, sensor, source node, or registration failure, stop cleanly.
- [x] Start via `ContextCompat.startForegroundService`; catch `IllegalStateException` (including background-start rejection) and `SecurityException` without crashing the listener.
- [x] Stop sensor, foreground notification, and service on stop command and destruction.
- [x] Run Wear debug/release builds, focused tests, and lint.

### Task 4: Status docs and phase gates

**Files:** Modify `docs/wear-hr-audit.md`, `docs/implementation-status.md`, and this plan.

- [x] Document supported permission flows, notification, and remaining physical-watch validation.
- [x] Run `:wear:assembleDebug :wear:assembleRelease :wear:testDebugUnitTest :app:testLatestDebugUnitTest :wear:lintDebug :app:lintLatestDebug spotlessCheck` and `git diff --check`.
- [x] Verify the Wear release artifact package is `se.hampusandersson.relven`.
- [ ] Open exactly one PR, wait for hosted checks, merge it, and verify the PR queue is empty before starting another increment.
