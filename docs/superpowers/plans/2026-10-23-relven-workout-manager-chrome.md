# Relven Workout Manager Chrome Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Steps use checkbox syntax.

**Goal:** Remove the remaining RunnerUp blue ActionBar from workout management so its existing Relven Compose library appears as one consistent Relven screen.

**Architecture:** Keep `ManageWorkoutsActivity` and all existing callbacks, file pickers, persistence, and synchronization behavior. Give only that activity a no-ActionBar Relven theme; its Compose screen already supplies the back action, title, actions, search, and saved workout list.

**Tech Stack:** Android XML themes and manifest, Kotlin/Java, Robolectric, Android emulator.

**Spec:** User feedback that RunnerUp screens should be redone to fit the Relven app. Project tokens: `RelvenColors.Background` `#0A0F18`, `Surface` `#172131`, and `Orange` `#FF5A1F`.

## Global Constraints

- Preserve `se.hampusandersson.relven` for release builds.
- Preserve the existing workout manager callbacks and RunnerUp engine behavior.
- Use one checkout and one PR at a time; merge before beginning another PR.
- Do not use the owner's physical ADB device before all phases are complete; use emulator `emulator-5586` at ADB port 5039.

## Review Focus

- The blue native ActionBar must disappear while Compose retains its own back-to-setup action.
- The status/navigation bars must remain legible with edge-to-edge layout.
- FIT import preview and confirmation dialogs must use the same Relven dark/orange treatment.
- Workout selection, import, plan actions, search, and edit/share/delete callbacks must be unaffected.
- The app ID and production launcher theme must not change.

---

### Task 1: Relven theme for workout manager

**Files:**
- Modify: `app/test/java/org/runnerup/relven/ui/RelvenLauncherManifestTest.java`
- Modify: `app/AndroidManifest.xml`
- Modify: `app/res/values/styles.xml`

**Interfaces:**
- `ManageWorkoutsActivity` must use `R.style.RelvenScreenTheme`, a no-ActionBar theme with Relven system-bar and accent colors.

- [x] Add a manifest regression test asserting `ManageWorkoutsActivity` uses `RelvenScreenTheme`.
- [x] Run the focused test and confirm it fails because the activity still uses `AppTheme`.
- [x] Define `RelvenScreenTheme` from `AppTheme.NoActionBar`, with Relven dark system-bar colors and orange accent; apply it only to `ManageWorkoutsActivity`.
- [x] Add a regression assertion that the activity uses the existing Relven dialog overlay; run the focused tests RED then GREEN.
- [x] Install the Latest debug APK on the isolated phone emulator, open Start > Advanced > Manage workouts, and verify the Relven background starts below the system status bar, the blue ActionBar is gone, and the Compose back/title/actions remain.
- [x] Select the checked-in FIT workout through Android's document picker and verify the Relven-styled import preview and successful handoff back to Advanced setup.
- [x] Run the app/Wear/HR/common test and lint matrix, Latest debug assembly, release bundles, and Spotless; verify phone and Wear release package IDs are `se.hampusandersson.relven`.
- [ ] Review and commit this PR, wait for hosted CI, then merge before starting another PR.
