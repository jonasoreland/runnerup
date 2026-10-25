# Wear peer readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Report a connected Wear phone peer only after NodeClient confirms it and keep the retry surface localized and on-brand.

**Architecture:** `StateService` continues owning Wear Data Layer lifecycle. A pure peer-matching policy gates the public connection status, while Compose maps status to localized resources and uses the existing Relven blue accent.

**Tech Stack:** Java, Kotlin, Google Play Services Wearable `NodeClient`, Compose Material 3.

**Spec:** `docs/superpowers/specs/2026-10-25-wear-peer-readiness.md`

## Global Constraints

- Preserve release application ID `se.hampusandersson.relven`.
- Do not clear prepared workouts or durable session/results when the phone is unavailable.
- Do not equate NodeClient peer discovery with the Relven app-level handshake.
- Do not use physical-device ADB.
- Keep one Relven PR at a time and work in the current checkout.

## Review Focus

- A stale expected phone ID must not be accepted when that peer is absent.
- A nearby peer with no expected ID may be detected, but cannot be reported as handshake-verified.
- An empty peer list must stay disconnected despite a connected local Google API client.
- Repeated discovery failure must retain one capped retry callback.
- English and Swedish layouts must keep the retry button readable on a small round screen.

---

### Task 1: Peer matching policy

**Files:**
- Create: `wear/src/main/java/org/runnerup/service/WearPeerAvailability.java`
- Test: `wear/src/test/java/org/runnerup/service/WearPeerAvailabilityTest.java`

- [x] Write matching tests for no peers, null expected peer with one connected node, expected peer present, and expected peer absent while another node is present.
- [x] Run the tests and confirm they fail because the policy is missing.
- [x] Implement the pure ID matching policy.
- [x] Run the tests and confirm they pass.

### Task 2: Gate service status on NodeClient peer discovery

**Files:**
- Modify: `wear/src/main/java/org/runnerup/service/StateService.java`
- Test: `wear/src/test/java/org/runnerup/service/WearPeerAvailabilityTest.java`

- [x] Add tests proving a connected local API with no phone peer maps to disconnected and a verified node match maps to connected.
- [x] Query `NodeClient.getConnectedNodes()` on service connection, phone-node changes, and manual retry.
- [x] Keep status connecting while the asynchronous peer query is pending.
- [x] Cancel retry on a matching peer; retry on missing/failed peer lookup with the existing capped backoff.
- [x] Preserve durable prepared-session and result data during all retry paths.

### Task 3: Localized and branded waiting UI

**Files:**
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Modify: `wear/src/main/kotlin/org/runnerup/wear/ui/WearConnectWaitingScreen.kt`
- Modify: `wear/src/test/kotlin/org/runnerup/wear/ui/WearConnectWaitingLayoutTest.kt`

- [x] Map each transport state to a localized resource ID and test that mapping.
- [x] Use the same localized resource for the retry button.
- [x] Style the button with the screen's existing blue accent and dark foreground.
- [x] Capture the small round AVD screen and verify spacing and contrast.

### Task 4: Verification

- [x] Run full module tests, lint, app/Wear debug assemblies, Spotless, and `git diff --check`.
- [x] Review package IDs, data preservation, and honest handshake wording.
- [ ] Open one PR, wait for CI, merge, fast-forward the same checkout to `master`, then rebuild phone and Wear debug APKs.
