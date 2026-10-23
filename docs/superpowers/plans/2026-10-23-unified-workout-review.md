# Unified Workout Review and History Details Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace the legacy stopped-workout result UI and make saved running details use the same evidence-based Relven review presentation.

**Architecture:** Keep `RunActivity`, `Tracker`, database rows, upload service, and result codes authoritative. Share Compose review content between the stopped-but-unsaved `DetailActivity` path and saved Relven History details; load summaries and route samples from the existing RunnerUp database. Reuse the existing MapLibre host, route metric logic, activity edit flow, and upload callbacks.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, SQLite/RunnerUp `DBHelper`, existing MapLibre/OpenFreeMap host, Robolectric and Compose unit tests.

**Spec:** `docs/superpowers/specs/2026-10-23-running-experience-redesign.md` — Phase 3.

## Global Constraints

- Keep one Tracker session, foreground service, database, and activity completion callback.
- Use only persisted and supported workout data; keep absent sensor values unavailable.
- Only unfinished review exposes Resume, Save, or Discard; saved history never exposes Resume.
- Preserve the `se.hampusandersson.relven` application ID and existing database schema.
- Keep route recording independent of map availability and support no-map builds.
- Derive only complete kilometer splits within one continuous accepted route segment; after pause/resume or a missing-route gap, restart split distance from that segment boundary so no split bridges the gap or represents a short remainder.
- The unfinished review's selected sport is the source for route/map visibility, even while editing before save.
- Use the active Stitch project `15389433782836702529` and Kinetic Obsidian tokens; the saved activity detail is a current-system reference and the review arrangement is an extrapolation.
- Work in this existing checkout, with one PR open at a time, and never use the physical Android device.

## Approaches

1. Restyle the legacy XML tabs in `DetailActivity`. This preserves old presentation ownership and leaves a separate Home history implementation.
2. **Chosen:** Share a Compose review body across unfinished `DetailActivity` review and finalized Relven History details, while keeping lifecycle/result adapters in their existing Activities. This avoids duplicate metric logic and retains Tracker/save behavior.
3. Add a second review Activity/service. This would duplicate lifecycle and state handling without improving the existing callback contract.

## Files and responsibilities

- `ActivityDetailState.kt`: extend persisted detail/route/split values without schema changes.
- `ActivityRouteData.kt`: validate route metric ranges and expose eligible metric series.
- `ActivityReviewState.kt`: lifecycle-specific actions and kilometer split calculations, all pure and testable.
- `ActivityReviewScreen.kt`: shared Compose summary, route map, chart, split/interval, note, and mode-specific actions.
- `ActivityDetailScreen.kt`: saved History wrapper and edit/share/upload integration using shared review content.
- `ActivityReviewCompose.kt`: small Java-to-Compose state/callback bridge for `DetailActivity` review mode.
- `DetailActivity.java`: host the shared Compose review for unfinished activity while preserving existing save/resume/discard result semantics and uploader.
- `RelvenHomeActivity.kt`: load saved route/detail data and provide the review map host lifecycle.
- `app/test/java/org/runnerup/relven/ui/ActivityReviewStateTest.kt`: review mode, outlier-safe chart metrics, kilometer splits, and edge cases.
- `ActivityDetailStateTest.kt` and screen tests: database ordering, notes/laps/target values, loading/empty states, and no Resume for saved history.

## Review Focus

- Saved mode accidentally exposes Resume or Discard; test action availability by mode.
- A retry, stale load completion, or deleted activity replaces the current activity; test identity checks and NotFound state.
- GPS outliers dominate chart/map scales; test invalid/implausible readings are omitted from display ranges while stored points remain intact.
- A pause/lap boundary creates a false kilometer split; test splits interpolate only across valid same-segment samples.
- Save is tapped repeatedly or the Activity is recreated during completion; verify one result and unchanged Tracker result semantics.
- Route/map data is missing or map tiles fail; verify summary, charts, notes, and save controls remain available.

---

### Task 1: Review state and evidence-derived analysis

**Files:** `ActivityReviewState.kt`, `ActivityRouteData.kt`, focused test classes.

- [ ] Add tests for mode-specific actions, plausible metric ranges, robust route color ranges, and chronological kilometer splits across segment boundaries.
- [ ] Run focused tests and confirm they fail for the missing review state/unsafe range/split behavior.
- [ ] Implement pure state and calculation helpers; do not mutate stored route data.
- [ ] Run focused and existing activity route/state tests.

### Task 2: Shared review composition

**Files:** `ActivityReviewScreen.kt`, `ActivityDetailScreen.kt`, Compose screen tests.

- [ ] Add screen tests for metric evidence, missing route/sensor state, notes, ordered laps/targets, and action visibility in each mode.
- [ ] Run tests red before implementing.
- [ ] Compose the Kinetic Obsidian summary, embedded route map, eligible charts, manual/km/structured splits, notes, and compact review actions.
- [ ] Reuse the same body from saved details and unfinished review.

### Task 3: Activity and lifecycle adapters

**Files:** `ActivityReviewCompose.kt`, `DetailActivity.java`, `RelvenHomeActivity.kt`, detail state tests.

- [ ] Verify Java-to-Compose callbacks in a focused host test or Activity result test before changing production wiring.
- [ ] Load activity/route data off the main thread and keep database cursors closed before rendering.
- [ ] Replace the `MODE_SAVE` legacy tab presentation with shared Compose UI while preserving Resume/Save/Discard result codes and upload behavior.
- [ ] Load route data for saved History through the same shared screen; keep saved activities finalized.
- [ ] Guard save completion against repeated taps and make notes part of the existing database update before reporting success.

### Task 4: Verification and integration

- [ ] Run focused state/screen tests, full `:app:testLatestDebugUnitTest`, app lint, Spotless, debug assembly, no-map assembly, and mapped assembly.
- [ ] Exercise a simulated emulator route through stop/review/resume, save, history reopen, note persistence, discard confirmation, and saved-history no-Resume.
- [ ] Capture review/history emulator screenshots and inspect standard and compact viewports.
- [ ] Run hosted CI, independent code review, merge this one PR, fast-forward `master`, and build the latest-master debug APK before beginning the next PR.
