# Saved Plan Library Management Implementation Plan

**Goal:** Make saved plans manageable from Relven's Plan destination without losing immutable package or revision history.

**Architecture:** Keep schema documents immutable. Persist archived root plan IDs in the existing private plan-state preferences. Group library rows by `rootPlanId`; surface the active or latest revision and let users inspect/activate saved history.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Java `RelvenPlanStore`, Android SharedPreferences.

**Spec:** `docs/superpowers/specs/2026-10-22-saved-plan-library-management.md`

## Constraints
- Preserve Play application ID `se.hampusandersson.relven` and RunnerUp as the workout engine.
- Keep archive/restore local, reversible, and separate from plan JSON/schema revisions.
- Active plans cannot be archived; require confirmation before moving inactive groups.
- Use the same worktree and one PR at a time. No physical ADB.

## Tasks

### Group plan history

- [x] Add tests for grouping revisions by root, active-revision selection, latest-revision fallback, and archived state.
- [x] Group the saved library by logical plan and expose version history.

### Archive and restore

- [x] Add store tests for archive/restore, preserving saved JSON, and rejecting the active or unknown plan.
- [x] Persist archive state by root ID and add a localized archive/restore library flow.
- [ ] Verify the full app, APK build, lint, style, documentation and hosted Android/F-Droid CI.

### Integration

- [ ] Open one PR, merge it, then build the APK from master and copy it to the requested output path.
