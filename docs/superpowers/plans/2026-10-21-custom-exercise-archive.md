# Custom Exercise Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reversible archiving for personal exercises without invalidating references.

**Architecture:** Store an optional archive flag in each private custom record, preserve every record in catalog resolution, and filter the Compose browsing view by active/archived mode. Archive metadata does not change exercise content revisions.

**Tech Stack:** Kotlin, Android AtomicFile, Jetpack Compose, Robolectric/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-21-custom-exercise-archive.md`

## Global Constraints

- Stable UUIDs continue to identify custom exercises.
- Existing custom JSON without the flag remains valid.
- No physical device or ADB is used.

## Review Focus

- Existing stored records without archive metadata remain active — persistence regression test.
- Archived custom records remain available by ID — store/repository resolution path retains all entries.
- Archiving/restoring does not increment content revision — persistence test.
- Bundled entries cannot show archive controls — UI gates callbacks on custom identity.
- Failed writes show recoverable feedback — detail UI error state.

---

### Task 1: Persist reversible archive metadata

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/exercise/CustomExerciseStore.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/exercise/ExerciseCatalog.kt`
- Test: `app/test/java/org/runnerup/relven/exercise/CustomExerciseStoreTest.kt`

**Interfaces:** `CustomExerciseStore.setArchived(exerciseId: String, archived: Boolean)` updates archive metadata without changing ID, content, or revision. `ExerciseEntry.isArchived` defaults false.

- [x] Write failing tests for archive/restore identity and revision preservation, and legacy records without the flag.
- [x] Run focused tests and confirm compilation fails because the archive API/model is missing.
- [x] Implement optional archive persistence and legacy default behavior.
- [x] Run focused persistence tests.

### Task 2: Add catalog browsing and restore controls

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseCatalogState.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/ExerciseCatalogScreen.kt`
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Test: `app/test/java/org/runnerup/relven/ui/ExerciseCatalogStateTest.kt`

- [x] Add a state test proving active and archived entries are shown in their respective views.
- [x] Implement active/archived filtering, archive and restore actions, and localized failure feedback.
- [ ] Run app/Wear tests, builds, lint, Spotless, and release checks.
- [ ] Update catalog and parity documentation, then request independent review.
