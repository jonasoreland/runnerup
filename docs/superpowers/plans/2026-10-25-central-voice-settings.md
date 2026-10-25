# Central Voice Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Host Relven's existing voice-coaching controls inside the centralized Settings Voice page.

**Architecture:** Reuse `AudioCueSettingsFragment` through a stable-ID `FragmentContainerView` and the existing Activity FragmentManager. Render the Voice destination as a bounded page rather than placing the legacy fragment inside the Settings overview's vertical scroll.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Fragments/Preferences, Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-25-central-voice-settings-design.md`

## Global Constraints

- Preserve existing audio preference names, defaults, storage, and workout runtime behavior.
- Keep the legacy `AudioCueSettingsActivity` usable for existing entry points.
- Do not use a physical Android device via ADB.
- Keep this as one PR and do not start another PR until this one is merged.

## Review Focus

- Re-entering the Voice destination must attach exactly one fragment view to the current Compose container.
- Fragment attachment while FragmentManager state is saved must not commit or crash, and it must retry after the host resumes.
- The embedded preference list must have bounded height and remain independently scrollable above bottom navigation.
- Voice controls must continue to write the same preferences consumed by workout audio.

---

### Task 1: Add a lifecycle-safe audio settings fragment destination

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/AudioCueSettingsDestination.kt`
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/AudioCueSettingsFragmentController.kt`
- Modify: `app/res/values/ids.xml` (or existing ID resource file)
- Test: `app/test/java/org/runnerup/relven/ui/AudioCueSettingsFragmentControllerTest.kt`

**Interfaces:**
- `AudioCueSettingsDestination(fragmentManager: FragmentManager)` hosts `AudioCueSettingsFragment` in `R.id.relven_audio_cue_settings_container`.
- Controller attaches only while the view is attached and manager state is not saved; it retries after lifecycle resume and replaces stale fragments bound to an old Compose container.

- [x] Add tests for deferred attach, one-time attach, state-saved behavior, resume retry, and container replacement.
- [x] Run the focused controller test and confirm it fails before implementation.
- [x] Implement stable-ID container and attach-aware controller, replacing a stale view after Compose creates a new container.
- [x] Run the focused test and confirm it passes.

### Task 2: Put the fragment in the centralized Voice page

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenSettingsOverview.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenSettingsScreens.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/SettingsDestination.kt`
- Modify: `app/src/main/org/runnerup/view/StartFragment.java`
- Test: `app/test/java/org/runnerup/relven/ui/SettingsNavigationTest.kt`
- Test: `app/test/java/org/runnerup/relven/ui/SettingsDestinationTest.kt`

**Interfaces:**
- `RelvenSettingsOverview` receives the current Activity `FragmentManager` for the embedded destination.
- The Voice route renders a fixed page header and a remaining-height fragment container, not the shared vertical-scroll column.
- The Start screen audio-settings shortcut selects `HomeDestination.Settings` and `SettingsPage.Voice` when hosted by `RelvenHomeActivity`.

- [x] Update navigation coverage to assert central Voice no longer offers the separate-Activity action.
- [x] Run the focused Settings navigation test and confirm the updated assertion passes.
- [x] Add a route test for the Start screen shortcut; confirm the missing central route helper fails compilation before implementation.
- [x] Route Start screen's audio shortcut through the centralized voice settings destination; retain its legacy Activity fallback for other hosts.
- [x] Add the Voice-specific bounded layout and route the Activity's existing FragmentManager to it.
- [x] Remove the separate-Activity launch button/import from the central page; retain `AudioCueSettingsActivity` class and callers.
- [x] Run the Settings tests and confirm they pass.

### Task 3: Verify app integration

**Files:**
- Review: Voice page, fragment lifecycle/controller, existing audio fragment and preference resource.

- [x] Run `:common:testDebugUnitTest :app:testLatestDebugUnitTest :wear:testDebugUnitTest` (858 tests passed).
- [x] Run `:app:lintLatestDebug :wear:lintDebug`, both debug APK assemblies, and `spotlessCheck`.
- [x] Confirm Gradle output and inspect `git diff`; preserve unrelated untracked user files.
- [ ] Commit and open exactly one PR in `HampusAndersson01/Relven-2.0`.
