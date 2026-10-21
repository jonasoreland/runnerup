# Custom Exercise Collection Portability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users export and merge-import their custom exercises without losing identity, revisions, or archive state.

**Architecture:** Extend `CustomExerciseStore` with strict versioned collection encoding and all-or-nothing merge. `ExerciseCatalogRepository` supplies bundled IDs and delegates to the store. The existing catalog screen launches document pickers through activity callbacks and displays localized transfer results.

**Tech Stack:** Kotlin, Android `AtomicFile`, `org.json`, Compose, Activity Result APIs, Robolectric/JUnit.

**Spec:** [custom exercise collection portability](../specs/2026-10-21-exercise-collection-portability.md)

## Global Constraints

- Retain package ID `se.hampusandersson.relven`.
- Do not use the owner's physical ADB device until every product phase is complete.
- Keep custom exercise persistence offline and atomic.
- Never modify or export bundled catalog records.
- Keep the existing 1 MiB file and 200-entry limits.
- Work on one PR at a time; PR #70 is merged and there are no open PRs.

## Review Focus

- Duplicate IDs within an import must reject before writes.
- Imported text with surrounding whitespace must reject rather than being silently trimmed during storage.
- A conflict late in the input must leave the complete pre-import file unchanged.
- IDs matching bundled exercises must reject.
- Oversized payloads and capacity overflow must reject without truncation.
- Canceling either picker must leave the catalog unchanged and show no false success.

---

### Task 1: Strict collection codec and atomic merge

**Files:** `CustomExerciseStore.kt`, `CustomExerciseStoreTest.kt`, `ExerciseCatalogRepository.kt`

**Interfaces:** Add store APIs `exportCollection(): ByteArray` and `importCollection(bytes: ByteArray, bundledExerciseIds: Set<String>): CollectionImportResult`. The result reports `addedCount` and `unchangedCount`.

- [x] Write failing tests for exact round-trip of every field, idempotent re-import, conflict atomicity, bundled-ID collision, duplicate input IDs, malformed and oversized input, and catalog capacity.
- [x] Run the focused Robolectric test and confirm it fails because the API is absent.
- [x] Implement strict schema parsing and validate all input and merged records before calling the existing atomic writer once.
- [x] Run the focused tests and confirm they pass.
- [x] Add a regression test for padded imported names, equipment, and instructions; reject those values before writing.

### Task 2: Repository and document-picker UI

**Files:** `ExerciseCatalogRepository.kt`, `RelvenHomeActivity.kt`, `ExerciseCatalogScreen.kt`, English and Swedish string resources, UI/Robolectric tests where practical.

- [x] Add repository forwarding APIs and obtain only bundled IDs from the bundled catalog.
- [x] Add OpenDocument and CreateDocument flows, bounded reads, stream cleanup, catalog reload after a successful import, and feedback for added/no-change/error/export success.
- [x] Add localized controls, verify repository result counts in tests, and inspect the screen feedback wiring.
- [x] Verify picker cancellation is a no-op by inspection of nullable Activity Result callbacks (cancel closes the operation without invoking import/export or changing the catalog).

### Task 3: Documentation and verification

**Files:** `docs/feature-parity.md`, `docs/implementation-status.md`, `docs/exercise-catalog.md`.

- [x] Document the schema, merge behavior, limits, and remaining device checks.
- [x] Run `:app:testLatestDebugUnitTest`, `:app:lintLatestDebug`, `:app:assembleLatestDebug`, `spotlessCheck`, and `git diff --check`.
- [x] Inspect the final diff and ensure package identity and unrelated files are unchanged.
- [ ] Open this single PR, wait for hosted CI, and merge it before starting another phase.
