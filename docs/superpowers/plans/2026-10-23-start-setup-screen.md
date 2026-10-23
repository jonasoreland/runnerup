# Relven Workout Setup Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the production Start/setup screen feel native to Relven while preserving RunnerUp's workout configuration, GPS, Tracker, permission, Wear, and recording pathways.

**Architecture:** Keep `StartFragment` authoritative for preferences, Tracker state, permission flow, workout construction, and launching `RunActivity`. Keep the current setup state holder and listener interface; refine the Compose setup controls and move the visible workout start action into the Relven readiness panel. Delete the old blue floating action button from the visible layout.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Android Fragment, RunnerUp Tracker, Robolectric unit tests, Android emulator.

**Design reference:** In-chat screen concept generated 2026-10-23 from the current Start screenshot and master Today screen; project tokens and component direction in `design/stitch/README.md`. Use the verified Relven palette, typography, and bottom navigation; do not use the unapproved Stitch plan/detail exports.

## Global Constraints

- Preserve `se.hampusandersson.relven` for release builds.
- Keep RunnerUp as the authoritative workout/session engine and preserve its permissions, GPS, audio cues, Wear, and recording behavior.
- Do not use the owner's physical ADB device until all phases are complete and manual testing is ready; use isolated emulators only.
- Keep the existing app navigation, Relven theme, localization, and supported setup behaviors.
- Do not add paid services, mandatory network dependencies, or new runtime dependencies.

## Review Focus

- Workout start must remain unavailable until GPS/session readiness and advanced-workout selection requirements are met.
- GPS-free activities must still allow start without GPS logging/fix.
- Wear broadcast start must reach the same authoritative Tracker pathway as a user tap.
- Returning from workout/audio management must retain the selected setup tab and preference state.
- Long localized labels and narrow widths must not clip or create horizontal scrolling.

---

### Task 1: Testable workout-start readiness

**Files:**
- Create: `app/test/java/org/runnerup/relven/ui/StartWorkoutAvailabilityTest.kt`
- Create or modify: `app/src/main/kotlin/org/runnerup/relven/ui/StartReadinessPresentation.kt`
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`

**Interfaces:**
- Produce `StartWorkoutAvailability.isAvailable(gpsStarted, sportWithoutGps, gpsLogging, gpsFixed, trackerConnected, advancedSelected, advancedWorkoutAvailable): Boolean`.

- [ ] Write tests proving required GPS/fix/logging, Tracker connection, and advanced workout selection gate start; GPS-free activities bypass GPS fix/logging only.
- [ ] Run `:app:testLatestDebugUnitTest --tests org.runnerup.relven.ui.StartWorkoutAvailabilityTest` and confirm the missing API fails compilation.
- [ ] Implement the pure readiness decision and wire `StartFragment` to the computed state without changing `startWorkout()` or Tracker ownership.
- [ ] Re-run the targeted tests and confirm all readiness cases pass.

### Task 2: Relven setup controls

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/StartWorkoutTabsScreen.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/WorkoutSetupScreen.kt`
- Modify: `app/test/java/org/runnerup/relven/ui/WorkoutSetupPresentationTest.kt` as needed for any changed presentation rules.

**Interfaces:**
- Preserve `WorkoutSetupStateHolder`, `WorkoutSetupState`, and `WorkoutSetupListener` signatures and callback keys.

- [ ] Restyle Basic, Interval, and Advanced tabs as a single compact Relven segmented control with selected-state accessibility semantics.
- [ ] Restyle target/audio dropdowns and numeric fields as consistent large touch controls, using the existing localized labels and values.
- [ ] Keep interval warm-up/cool-down meaning, advanced workout management, step editing, and audio management actions intact.
- [ ] Add/adjust focused presentation tests only where behavior or accessibility state changes; run the targeted setup tests.

### Task 3: Relven readiness panel and workout CTA

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/StartScreenChromeCompose.kt`
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`
- Modify: `app/res/layout/start.xml`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`

**Interfaces:**
- Extend the readiness renderer with `canStartWorkout`, localized start label, and a start callback that calls the existing RunnerUp start action.

- [ ] Add localized "Start workout" action text.
- [ ] Render readiness details and GPS action in the Relven surface, and show a full-width orange workout button only when `StartWorkoutAvailability` is true.
- [ ] Remove the visible blue legacy FAB and update Wear start broadcasts to use the same guarded action callback.
- [ ] Remove FAB-specific margin updates while preserving readiness expand/collapse behavior.
- [ ] Build and run focused readiness/setup tests.

### Task 4: Verification and review

**Files:**
- Modify: `docs/implementation-status.md`

- [ ] Run the complete app/Wear test, debug APK, phone/Wear release bundle, lint, and Spotless matrix.
- [ ] Install the Latest debug APK on isolated `emulator-5586` through ADB port 5039; verify Basic, Interval, Advanced, GPS-off, GPS-ready CTA, and Swedish at narrow width where available.
- [ ] Confirm the CTA launches RunnerUp `RunActivity` through the existing Tracker state pathway; do not start an outdoor/location session on physical hardware.
- [ ] Record emulator and physical-device status in `docs/implementation-status.md`.
- [ ] Review the diff, commit, push one PR, wait for hosted CI/review, and merge before starting another PR.
