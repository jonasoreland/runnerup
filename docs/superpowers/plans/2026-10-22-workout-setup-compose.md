# Workout Setup Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy Basic, Interval, and Advanced workout setup panels with Relven-styled Compose screens while preserving RunnerUp preferences, workout builders, and start behavior.

**Architecture:** Keep StartFragment as the integration boundary. It reads and writes RunnerUp preferences, loads workouts and adapters, and passes immutable display state plus callbacks to Compose. Compose owns only presentation and direct editing inputs; RunnerUp remains the workout and session authority.

**Tech Stack:** Android Views interop, Jetpack Compose Material 3, Kotlin, Java SharedPreferences and workout APIs, JUnit/Robolectric.

**Spec:** User's Relven 2.0 brief, setup screenshot as behavior/content reference, and `design/stitch/README.md` as visual reference.

## Global Constraints

- Preserve Play application ID `se.hampusandersson.relven`.
- Keep RunnerUp's `WorkoutBuilder`, `WorkoutSerializer`, Tracker, and session flow authoritative.
- Do not use the physical Android device or ADB before all product phases are complete.
- Submit and merge one PR before starting another.
- Use dark navy `#0A0F18`, orange `#FF5A1F`, muted text `#8E9BAE`, and existing Relven Compose theme.

## Review Focus

- Saved setup values survive tab switches and returning from audio/workout management; cover through preference mapping tests.
- Invalid numeric/time edits must not crash composition or session start; cover editor parsing/commit behavior.
- Empty/unavailable Advanced workout lists must provide a clear manage action and must not start a null workout; cover state mapping and existing readiness guard.
- HR zone targeting is offered only when zones are configured; cover presentation state.
- Rest distance/time and active distance/time edits affect RunnerUp's builder preferences; cover preference key/type mapping.

---

### Task 1: Compose setup state and callback boundary

**Files:**
- Create `app/src/main/kotlin/org/runnerup/relven/ui/WorkoutSetupScreen.kt`
- Create `app/src/main/org/runnerup/relven/ui/WorkoutSetupListener.java`
- Test `app/test/java/org/runnerup/relven/ui/WorkoutSetupStateTest.kt`
- Modify `app/src/main/org/runnerup/view/StartFragment.java`
- Modify `app/res/layout/start.xml`
- Modify `app/src/main/res/values/strings.xml` and Swedish translations

**Interfaces:** Compose receives a setup snapshot (`WorkoutSetupState`) and `WorkoutSetupListener`; listener actions select tabs, persist existing preference keys, select/manage workouts, and manage audio schemes.

- [ ] Write state mapping tests for configured/unconfigured HR zones, empty advanced workouts, default values, and stored interval values.
- [ ] Run focused tests and verify RED for the new state API.
- [ ] Implement immutable state and the listener boundary.
- [ ] Verify focused tests pass.

### Task 2: Replace the three legacy setup panels

**Files:**
- Modify `app/src/main/kotlin/org/runnerup/relven/ui/WorkoutSetupScreen.kt`
- Modify `app/src/main/org/runnerup/view/StartFragment.java`
- Modify `app/res/layout/start.xml`

- [ ] Add tests for preference key parsing and valid interval/basic inputs.
- [ ] Run focused tests and verify RED.
- [ ] Implement scrollable Basic, Interval, and Advanced Compose panels with audio scheme selection, target selection, interval inputs, warmup/cooldown indicators, advanced workout selection, ordered step preview, and Manage actions.
- [ ] Remove legacy setup panel includes and obsolete panel view references while retaining StartFragment's RunnerUp start path.
- [ ] Verify focused tests pass and build the debug APK.

### Task 3: Verify and ship one PR

- [ ] Run app/Wear unit tests, debug/release builds, relevant lint, Spotless, and application ID verification.
- [ ] Inspect the diff and request independent read-only review.
- [ ] Open exactly one PR against `master`; wait for CI/review and merge it before further product work.
- [ ] Pull merged `master`, build `:app:assembleLatestDebug`, verify APK metadata/hash, and report the local APK path without using ADB.
