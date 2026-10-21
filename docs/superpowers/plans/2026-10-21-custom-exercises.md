# Custom Exercises Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline creation and editing of personal exercises while preserving stable references and bundled catalog content.

**Architecture:** Keep the bundled catalog read-only and merge it with a strict custom-entry file stored in private app data. Use `AtomicFile` for writes, preserve UUIDs on edit, and expose create/edit through the existing Compose exercise catalog.

**Tech Stack:** Kotlin, Android `AtomicFile`, `org.json`, Jetpack Compose, Robolectric/JUnit.

**Spec:** [2026-10-21-custom-exercises.md](../specs/2026-10-21-custom-exercises.md)

## Global Constraints

- Keep package ID `se.hampusandersson.relven` and existing resource keys/data formats stable.
- Keep all custom data local and offline.
- Do not edit the RunnerUp workout engine or use physical ADB.
- Keep the PR queue serial: merge this PR before starting another.

## Review Focus

- A rejected edit must not damage an existing custom record.
- A custom ID collision with the bundled catalog must not shadow the bundled exercise.
- Language switching must preserve both English and Swedish values.
- Corrupt or oversized custom data must not be silently rewritten or dropped.
- Saved plan snapshots and history references must remain stable across edits.

---

### Task 1: Custom exercise domain and persistence

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/exercise/ExerciseCatalog.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/exercise/ExerciseCatalogRepository.kt`
- Create: `app/src/main/kotlin/org/runnerup/relven/exercise/CustomExerciseStore.kt`
- Test: `app/test/java/org/runnerup/relven/exercise/CustomExerciseStoreTest.kt`

**Interfaces:**
- Store supports bounded `load()`, `create(input)`, and `update(id, input)` operations; creation returns a stable UUID, and update preserves it while increasing revision.
- Repository merges validated custom entries with the immutable bundled catalog and rejects ID collisions.

- [x] Write tests for create/reload, edit ID/revision preservation, invalid input, duplicate IDs, corrupt file handling, interrupted recovery, and failed replacement.
- [x] Run the focused tests and confirm expected failures during TDD.
- [x] Implement the bounded codec and atomic persistence, then merge custom records in the repository.
- [x] Run the focused tests and confirm they pass.

### Task 2: Catalog state and create/edit UI

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseCatalogState.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseCatalogScreen.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Test: `app/test/java/org/runnerup/relven/ui/ExerciseCatalogStateTest.kt`

**Interfaces:**
- State exposes create/edit validation and preserves form values on validation or save errors.
- Catalog screen accepts create/update callbacks and exposes edit only for custom entries.

- [x] Write state tests for required bilingual names/instructions, valid save input, and validation limits.
- [x] Run focused tests and confirm expected failures during TDD.
- [x] Add localized form, category and muscle-group controls; wire save/reload/detail return in the Activity.
- [x] Run state and existing catalog tests, then inspect loading/error/empty and narrow layouts by code.

### Task 3: Documentation and verification

**Files:**
- Modify: `docs/exercise-catalog.md`
- Modify: `docs/feature-parity.md`
- Modify: `docs/implementation-status.md`

- [x] Document create/edit scope and explicitly retain deferred media, favorites, deletion, and collection import/export work.
- [x] Run app/Wear tests, debug/release builds, app/Wear lint, Spotless, free/F-Droid build, and verify Play package ID/version.
- [x] Run `git diff --check`; obtain independent read-only review; confirm the PR queue is empty.
- [ ] Open the single PR, wait for hosted CI, merge only when green, verify the merge and empty queue, and record results.
- [ ] Wait for hosted CI, merge only when green, verify the merge and empty queue, and record results.
