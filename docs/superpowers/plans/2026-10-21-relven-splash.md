---
name: relven-splash
description: Add a Relven-branded system splash to the production launcher.
---

# Relven splash implementation plan

## Design

The production `RelvenHomeActivity` gets an AndroidX SplashScreen compat starting theme. Use the existing launcher foreground vector as the splash mark and the established Relven background color `#0A0F18`. The splash transitions to `AppTheme.NoActionBar`; install the compat splash before Activity creation. Debug/free/release variants share the same design. Keep the normal system timing and launcher intent, and do not add a custom delay or new splash Activity.

## Tasks

1. Add a manifest regression test proving the launcher uses the splash starting theme; run it and confirm it fails against the current manifest.
2. Add `androidx.core:core-splashscreen`, define the starting theme and color, assign it to `RelvenHomeActivity`, and install the compat API before `super.onCreate`.
3. Run the launcher test, app/Wear tests, debug/release builds and lint, F-Droid build, bundle and Spotless; verify the Play application ID and version remain unchanged.
4. Review the diff, update migration/status docs, commit and submit one PR after confirming the open queue is empty.
