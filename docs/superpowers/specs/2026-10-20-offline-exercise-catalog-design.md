# Offline Exercise Catalog Design

## Intent

Relven is one training app for running, strength, and prehab. This increment adds a useful phone-based exercise library that remains available offline and gives each exercise a stable identity that later workout and plan work can reference. Catalog entries are bodyweight only, matching the current no-equipment requirement.

## Scope

- Add an `Exercises` destination to the production Relven home navigation.
- Provide local search and filters for lower body, upper body, core, and warm-up/mobility.
- Provide an exercise detail view with localized name and instructions, target muscle groups, movement category, and equipment requirements.
- Ship a small curated English/Swedish catalog in app assets, with stable UUIDs and a library revision.
- Validate the bundled catalog against a versioned JSON Schema and fail closed on malformed or duplicate entries.
- Keep all reading local. No network, account, API key, or third-party exercise/media dataset is required.

## User experience

The catalog opens from the existing bottom navigation. It shows a search field, category chips, and a scrollable list of exercises. Selecting a row opens details with a clear back action. Search and category filters compose; clearing either restores matching results. Empty search results explain that no exercises matched and allow the query to be edited. Loading or malformed local catalog states are visible and recoverable.

The screen follows `RelvenTheme`, existing dark surfaces and orange accent, Compose conventions, and localized English/Swedish strings. Search, filters, exercise rows, and back navigation must have clear screen-reader names and keyboard/D-pad focus behavior. The five bottom-navigation items must remain readable at narrow phone widths.

## Data model

Add a `relven.exercise-library` document with `schemaVersion: 1`, a positive `libraryRevision`, and an exercises array. Every entry has a stable UUID `exerciseId`, a positive entry revision, English and Swedish names, localized instruction steps, a category, stable muscle-group keys, an equipment list, and a movement-pattern key. The initial curated set has an empty equipment list for every item. Text is authored for this project; no third-party dataset or image/media is bundled.

Parsing validates the complete document before it reaches UI state. IDs must be valid and unique; versions and strings must be valid; required localized content must be nonblank; arrays have finite bounds; categories and keys must be recognized. English is the fallback when the device language has no catalog translation. Unknown schema versions fail with a recoverable catalog error.

The stable ID and revision are compatible with the exercise identity model in Workout v3, but this increment does not change Workout v3, enable strength-plan import, or start/log strength workouts. Historical workout exercise snapshots and media remain for later phases.

## Boundaries

- Catalog data is immutable in this increment. User-created exercises and editing are future work.
- No sets, repetitions, workout tracking, personal records, or watch interactions are added.
- No external exercise data, image/video source, CDN, or network dependency is introduced.
- The existing RunnerUp workout/session engine and application ID `se.hampusandersson.relven` remain authoritative and unchanged.

## Verification

Unit tests cover strict schema/version validation, malformed and duplicate IDs, locale fallback, search, category filtering, empty results, and the shipped asset's UUID uniqueness and no-equipment constraint. Run app debug and release builds, the full app unit-test suite, debug lint with no new findings, and Spotless. Do not use the owner's physical ADB device before all product phases are complete.
