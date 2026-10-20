# Offline Exercise Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline, searchable, localized, bodyweight exercise catalog with stable IDs to the production Relven app.

**Architecture:** Bundle a versioned catalog JSON document and schema in the app, parse and validate it into immutable Kotlin models, and show it through a Compose destination in the existing Relven home navigation. Keep search and category filtering as pure functions and preserve the RunnerUp workout engine and existing plan schema.

**Tech Stack:** Kotlin, Android assets/resources, JSON Schema, Jetpack Compose Material 3, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-offline-exercise-catalog-design.md`

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- Keep the catalog readable without network access, login, or a paid service.
- Give every core exercise a stable UUID and explicit library revision.
- Keep all initial exercise equipment lists empty.
- Do not enable strength plan import or alter RunnerUp execution/history in this increment.
- Do not use the owner's physical Android device until all product phases are complete.

## Review Focus

- Malformed, unknown-version, duplicate-ID, and incomplete catalog input must fail visibly without partial display — parser tests.
- Missing locale translations must fall back to English without blank names or instructions — localization tests.
- Combined query and category filters must not leak nonmatching entries; clearing filters must restore the full catalog — filter tests.
- The seeded catalog must keep stable unique UUIDs and contain no equipment requirements — asset integrity tests.
- Narrow screen width and accessibility semantics must keep navigation/search/filter/detail controls usable — layout and semantics inspection plus lint.

---

### Task 1: Catalog model and strict parser

**Files:**
- Create `schemas/exercise-library-v1.schema.json`
- Create `app/src/main/kotlin/org/runnerup/relven/exercise/ExerciseCatalog.kt`
- Create `app/test/java/org/runnerup/relven/exercise/ExerciseCatalogTest.kt`

**Interfaces:** `ExerciseCatalog.parse(json, languageTag): ExerciseCatalog`; `catalog.filter(query, category): List<ExerciseEntry>`.

- [x] Write parser tests for valid localized documents, fallback language, wrong schema version, malformed/duplicate UUID, empty translation, unknown category/muscle group, and bounded instruction lists.
- [x] Run `:app:testLatestDebugUnitTest --tests org.runnerup.relven.exercise.ExerciseCatalogTest`; the tests first failed to compile because the parser was absent.
- [x] Implement immutable catalog and entry models, strict parsing, stable categories, and pure case-insensitive search/category filtering.
- [x] Run the focused tests and confirm they pass.

### Task 2: Curated offline catalog asset

**Files:**
- Create `app/src/main/assets/relven/exercise-library-v1.json`
- Modify `app/test/java/org/runnerup/relven/exercise/ExerciseCatalogTest.kt`
- Create or update `docs/exercise-catalog.md`

**Interfaces:** Asset conforms to `schemas/exercise-library-v1.schema.json` and parses through the same `ExerciseCatalog.parse` API as external test fixtures.

- [x] Add tests that load the shipped asset, confirm IDs are unique UUIDs, verify all entries have an empty equipment list, and check required English/Swedish labels and instructions.
- [x] Run the focused tests; asset validation required the correct module-relative working path and then passed.
- [x] Add the curated no-equipment exercises with original concise instruction steps, localized names/instructions, stable IDs, revisions, and recognized metadata keys.
- [x] Document the format, revision policy, curated content scope, and explicit absence of workout execution/media in this phase.
- [x] Run focused parser and asset tests; confirm they pass.

### Task 3: Catalog screen and detail state

**Files:**
- Create `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseCatalogScreen.kt`
- Create `app/test/java/org/runnerup/relven/ui/ExerciseCatalogStateTest.kt`
- Modify `common/src/main/res/values/strings.xml`
- Modify `common/src/main/res/values-sv/strings.xml`

**Interfaces:** `ExerciseCatalogScreen(state, onRetry)` renders loading, loaded list, selected detail, and recoverable error states using the established `RelvenTheme` tokens.

- [x] Write tests for query/category composition, no-result state, clearing filters, and selected-exercise detail lookup.
- [x] Run the focused tests; they first failed to compile because the view-state model was absent.
- [x] Implement localized search, category chips, exercise list, and text-only detail view with equipment, target areas, and ordered instruction steps.
- [x] Give interactive controls accessible labels/selected state, maintain focusable back navigation, and ensure scrollable content avoids the persistent navigation bar.
- [x] Run focused state tests and inspect Compose code for narrow-width behavior, loading/error/empty states, and keyboard/D-pad access. Runtime UI remains device-unverified by instruction.

### Task 4: Production home navigation and asset loading

**Files:**
- Modify `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify `app/test/java/org/runnerup/relven/ui/HomeDestinationTest.kt`
- Modify `docs/feature-parity.md`
- Modify `docs/implementation-status.md`

**Interfaces:** Add `HomeDestination.Exercises`, persist it through existing saved-instance state, load the local asset off the main thread, and pass explicit load state plus retry callback to `ExerciseCatalogScreen`.

- [x] Write tests for destination serialization/restoration and catalog loading-state mapping.
- [x] Add restoration coverage for existing and new destinations; the helper was introduced in the same integration edit and covered by the focused test.
- [x] Add the localized fifth navigation item with an existing Material icon; retain Today, Plan, Start, and History actions.
- [x] Load and validate the asset once on IO; map expected read/validation failures to a retryable unavailable state.
- [x] Update parity/status to distinguish the available local catalog from pending strength logging, plan integration, and media.
- [x] Run focused state tests and inspect bottom navigation and screen-reader semantics at narrow widths. Runtime UI remains device-unverified by instruction.

### Task 5: Verify and commit

**Files:** All files above.

- [x] Run `spotlessApply`.
- [x] Run `:app:assembleLatestDebug :app:assembleLatestRelease :app:testLatestDebugUnitTest :app:lintLatestDebug spotlessCheck`.
- [x] Run `git diff --check` and confirm the app ID remains `se.hampusandersson.relven`.
- [ ] Commit the complete catalog increment on `codex/p13-exercise-catalog`; push and open one PR only after confirming there is no other open PR.
