# Stitch Phone UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inherited phone shell with native Compose screens derived from verified Relven Stitch references while preserving RunnerUp's session engine.

**Architecture:** Keep `Tracker`, `Workout` and `DBHelper` as the authoritative Java engine. New Kotlin Compose UI observes them through narrow adapters and invokes existing actions. Native plan and import work gets its own tested boundary before screens depend on it.

**Tech Stack:** AGP 9.4.1 built-in Kotlin, Kotlin/Compose compiler 2.4.10, Compose BOM 2026.09.00, Material 3, AndroidX Lifecycle, existing Java/SQLite engine.

**Spec:** User-supplied Relven 2.0 master task; verified Stitch exports in `design/stitch/manifest.json` and `design/stitch/README.md`.

## Global Constraints

- Release application ID remains `se.hampusandersson.relven`; debug uses `.debug`.
- Original Relven repository stays read-only.
- UI copy is Swedish; original generated Stitch dashboard governs colors, typography and shell.
- No fake activity or sensor values. GPS/workout recording remains owned by phone `Tracker`.
- Keep the app buildable at each milestone; do not expose nonfunctional controls.

## Review Focus

- First install with no workout history: show a useful empty state.
- Location or Bluetooth denied: preserve navigation and explain recovery.
- Workout process/background: Compose observation must reflect the real `Tracker` state after recreation.
- Long Swedish labels and small screens: keep actions readable and reachable.
- Old Relven upgrade: do not overwrite `relven-workouts.db` before migration is verified.

---

### Task 1: Buildable Compose foundation

**Files:** Modify root `build.gradle`, `app/build.gradle`; create `app/src/main/kotlin/org/runnerup/relven/ui/RelvenTheme.kt` and a minimal preview activity; update manifest only after the activity is functional.

**Interfaces:** `RelvenTheme(content: @Composable () -> Unit)` supplies verified colors and type. The activity hosts Compose, and can open legacy screens through explicit intents.

- [ ] Add Kotlin/Compose build configuration and dependencies.
- [ ] Add theme tokens from the original dashboard export and a simple native screen with Swedish text.
- [ ] Build `:app:assembleLatestDebug` and run the screen on a phone/emulator.
- [ ] Check accessibility labels, text scale and a narrow viewport.
- [ ] Commit.

### Task 2: Home and navigation

**Files:** Create Compose home/nav components and a Java bridge for live session/recording state; update launcher wiring only when actions work.

**Interfaces:** Home reads actual activities and current `Tracker` state; Start opens the inherited workout setup, History opens inherited activity list until migrated.

- [ ] Write tests for adapter empty/populated/session states.
- [ ] Implement three-destination floating nav, profile header and meaningful empty state.
- [ ] Install and test navigation, permissions, recreation and process return.
- [ ] Compare screenshot against Stitch Daily Activity Dashboard.
- [ ] Commit.

### Task 3: Plan and details

**Files:** Add versioned plan storage/read models, Compose calendar, running detail and strength detail components.

**Interfaces:** Plan list exposes scheduled sessions; running detail maps to executable `Workout`; strength detail resolves stable exercise IDs.

- [ ] Test date selection, multiple sessions, missing exercise IDs and unsupported workout steps.
- [ ] Implement from corrected local visual contract; refresh Stitch exports when the service edit succeeds.
- [ ] Verify on device with empty and populated data, and compare screenshots.
- [ ] Commit.

### Task 4: Active workout and Wear UI

**Files:** Add read-only Tracker state adapter and Compose active session screen; add Wear Compose display after phone bridge is proven.

- [ ] Test state transitions, pause/resume/stop, interval skip and stale HR display.
- [ ] Keep phone service authoritative; preserve existing Wear command protocol.
- [ ] Device-test screen off, reconnection and permission revocation.
- [ ] Commit.

### Task 5: Remaining screens and audit

**Files:** Migrate library, importer, history, activity details/maps, exercise catalog and settings incrementally.

- [ ] Verify each screen's real actions and empty/error/success states.
- [ ] Compare screenshots against the verified reference exports.
- [ ] Run lint, typecheck, unit tests, phone/Wear builds and UI guideline audit.
- [ ] Update feature parity and implementation status.
