# Gym custom workout composition

## Intent

Selecting Gym in Start should replace run targets, run tabs, audio cues and GPS startup with an offline strength workout composer. The user can name a workout, add catalog exercises in order, change set/repetition/rest prescriptions, remove exercises, then start the resulting guided workout.

## Architecture

- Keep `StartFragment` as the sport selector and adapter between Start UI, catalog, readiness, and host activity.
- Extend existing Compose setup state and listener with a Gym mode and typed composer data. Hide run tabs for Gym and place setup directly below the sport header.
- Load the existing bundled plus custom exercise catalog offline, asynchronously; persist the unfinished composer draft locally so sport changes and navigation do not discard it.
- Build a Workout v3 `strength` document from canonical catalog references, preserving IDs, library and exercise revisions, English snapshot name/instructions, equipment, and empty images. Parse the document through `RelvenStrengthWorkoutParser` before starting.
- Route the document into `RelvenHomeActivity` and its existing `StrengthSession` / `StrengthSessionStore` flow. Do not start RunnerUp GPS tracking for Gym or create a second strength engine.
- If another strength session is in progress, retain the existing conflict dialog and never overwrite it.

## UI

- Use the existing Relven dark surfaces, orange primary action, typography, and spacing tokens.
- Gym shows workout name, exercise search/add picker, an ordered exercise list with sets/reps/rest controls, remove actions, empty/loading/error states, and a start CTA disabled until a valid nonempty draft and catalog are ready.
- Run sports retain their existing Basic/Interval/Advanced setup and GPS/audio behavior.
- Localize all added controls in English and Swedish.

## Boundaries

This is a one-off workout builder that starts a guided session. It does not add saved reusable templates, plan scheduling/calendar changes, workout tracking in RunnerUp, new equipment, network dependencies, or a new persistence schema. The active session remains durably managed by the current strength store.

## Acceptance

- Gym selection hides run-only goals/tabs/audio and GPS actions, and offers exercise composition with sets/reps/rest.
- Exercise search works from the offline catalog; prescription changes and workout name survive navigation/recreation.
- Starting produces a validated canonical Workout v3 snapshot and opens the existing guided strength session.
- Active-session conflicts do not overwrite current session data.
- Running setup behavior remains unchanged.
- Targeted JVM tests, app lint/build, and isolated emulator visual/functional verification pass. Do not use a physical Android device.
