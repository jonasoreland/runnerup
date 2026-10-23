# Gym custom workout builder implementation plan

## Goal

When Gym is selected in Start, compose and launch an offline, named strength workout through Relven's existing durable guided strength session engine.

## Constraints

- Preserve the Run/Interval/Advanced path and `se.hampusandersson.relven` application id.
- No physical device ADB; use tests and an isolated emulator only.
- One PR at a time; this is the only in-progress PR.
- Canonical Workout v3 exercise snapshots must come from the catalog.

## Tasks

1. **Builder model and document generation**
   - Add bounded draft/prescription models and pure validation.
   - Generate a strength Workout v3 document with stable unique IDs, exact canonical snapshot fields, and parser validation.
   - Add focused tests for empty/invalid input, duplicate/unknown exercise IDs, bounds, and canonical snapshot output.

2. **Gym setup state and UI**
   - Extend setup state and listener with sport mode, catalog/loading/error, search, name, ordered selected exercises, and add/remove/prescription callbacks.
   - Add Relven-themed Gym composer with offline exercise picker, sets/reps/rest fields, empty/loading/error states, and start CTA.
   - Keep run setup composition unchanged; localize labels in English and Swedish.

3. **Fragment and durable draft integration**
   - Detect Gym selection, hide run tabs, re-layout setup, asynchronously load the catalog, and persist/restore the draft locally.
   - Suppress Gym GPS-start actions and gate the Gym CTA on draft validity rather than Tracker state.
   - Preserve legacy setup and GPS logic for other sports.

4. **Existing strength-session integration**
   - Add a narrow host entry point from `StartFragment` to `RelvenHomeActivity` for a validated custom strength workout.
   - Reuse `StrengthSession.start`, `StrengthSessionStore`, recovery, completion, and existing-session conflict handling.
   - Do not overwrite an in-progress session or create a parallel engine.

5. **Verification and integration**
   - Run focused Kotlin/Java unit tests, relevant broader app tests, lint, and debug build.
   - Verify Gym and Run setup on an isolated emulator if present; never use physical ADB.
   - Review diff, commit, open one PR, wait for CI, merge, then build latest master APK and report its full path.
