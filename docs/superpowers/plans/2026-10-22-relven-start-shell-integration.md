# Relven Start Shell Integration Implementation Plan

**Goal:** Keep workout setup inside Relven Home's navigation shell.

**Architecture:** Add `HomeDestination.Start`, embed `StartFragment` through a stable `FragmentContainerView`, and retain the RunnerUp tracker/setup logic. Remove the fragment when leaving Start so GPS and service cleanup occurs.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Fragments, existing RunnerUp StartFragment.

**Spec:** `docs/superpowers/specs/2026-10-22-relven-start-shell-integration.md`

## Global Constraints
- Preserve Play package `se.hampusandersson.relven` and RunnerUp tracking authority.
- Do not use physical ADB.
- Work in the single existing worktree and one PR at a time.

## Review Focus
- Start destination restores after Activity recreation.
- Both Start tab and Today CTA remain in the Relven Activity.
- Leaving Start removes its fragment and releases GPS lifecycle work.
- The Relven bottom navigation remains visible and marks Start selected.
- Returning to Start creates one fragment, not duplicates.

### Task 1: Host workout setup in the Relven shell

**Files:**
- Modify `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Create `app/src/main/kotlin/org/runnerup/relven/ui/StartDestinationScreen.kt`
- Create `app/res/values/ids.xml`
- Test `app/test/java/org/runnerup/relven/ui/HomeDestinationTest.kt`

- [x] Add a persisted Start destination and route the Start tab and Home card to it.
- [x] Host existing StartFragment in a stable FragmentContainerView under Relven navigation.
- [x] Remove the fragment when leaving Start.
- [x] Verify the destination test, full unit suite, APK build, formatting, and diff.
