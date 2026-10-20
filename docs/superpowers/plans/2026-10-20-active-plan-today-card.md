# Active Plan Today Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the active plan’s entry for the current day on the production Today screen without changing RunnerUp workout execution.

**Architecture:** Resolve the active saved plan on the IO dispatcher, compare the current instant against the plan’s timezone, and map the result to a small UI state. Extend the validated plan model with workout display names so the card can identify the prescribed workout without reparsing its source JSON in the UI.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Java RelvenPlanImporter/RelvenPlanStore, JUnit/Robolectric.

**Spec:** Approved continuation scope from the Relven 2.0 master task and the current production Today/plan package implementation. Show today’s active-plan item and explicit empty states; keep recording and workout controls in RunnerUp.

## Global Constraints

- Keep the Play application ID `se.hampusandersson.relven`.
- RunnerUp remains the authoritative workout/session engine.
- Do not access the user’s physical ADB device until every phase is complete and the app is ready for manual testing.
- Plan dates are one-time calendar dates interpreted in the plan’s declared timezone ID.
- Keep the Today screen read-only; do not mutate plans, activities, or Tracker state.
- Follow existing Relven Compose tokens and English/Swedish resource conventions.

## Review Focus

- Midnight and UTC-offset boundary: resolve the item in the plan timezone using a fixed instant test.
- Rest day: show a rest state without a workout name.
- No matching date: show a distinct no-item-today state.
- No active plan and unreadable active package: show separate recoverable states.
- Long workout or plan names and notes: allow Compose text to wrap within the existing scrollable layout.

---

### Task 1: Preserve plan workout display names

**Files:**
- Modify: `app/src/main/org/runnerup/workout/RelvenPlanImporter.java`
- Test: `app/test/java/org/runnerup/workout/RelvenPlanImporterTest.java`

**Interfaces:**
- Produces `Plan.workoutNames(): Map<String, String>` keyed by each validated workout ID.

- [x] Add a parser test asserting that the imported workout ID maps to its declared name.
- [x] Run the focused parser test and verify it fails because the accessor is absent.
- [x] Collect each validated workout document’s `name` during parsing and return an immutable map on `Plan`; update direct `Plan` construction sites.
- [x] Run `:app:testLatestDebugUnitTest --tests org.runnerup.workout.RelvenPlanImporterTest` and verify it passes.

### Task 2: Resolve the active plan entry for a fixed instant

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/TodayPlanState.kt`
- Test: `app/test/java/org/runnerup/relven/ui/TodayPlanStateTest.kt`

**Interfaces:**
- Produces `todayPlanState(plan: RelvenPlanImporter.Plan?, nowMillis: Long): TodayPlanState`.
- `TodayPlanState` represents no active plan, no item today, rest day, planned workout, and unavailable data.

- [x] Add tests for a workout day, rest day, missing date, absent plan, and an instant whose local date differs between UTC and the plan timezone.
- [x] Run the focused test and verify it fails because the resolver is absent.
- [x] Implement date resolution with `SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)` and `TimeZone.getTimeZone(plan.timezone())`; preserve plan and workout names plus optional notes in the result.
- [x] Run the focused state tests and verify they pass.

### Task 3: Display read-only plan state on Today

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`
- Modify: `docs/implementation-status.md`
- Modify: `docs/feature-parity.md`

**Interfaces:**
- `RelvenHome` consumes a `TodayPlanState` and renders one passive plan card.
- `RelvenHomeActivity` reloads active-plan state from `RelvenPlanStore` on resume using `Dispatchers.IO`.

- [x] Render distinct loading, no active plan, no scheduled item today, rest day, workout, and unavailable states with English and Swedish strings.
- [x] Keep the card non-clickable and preserve existing RunnerUp Start, History, and active-session actions.
- [x] Run app unit tests, debug/release builds, lint, and Spotless; inspect the diff and check that no device was used.

### Task 4: Review and integrate

- [ ] Run `git diff --check` and request an independent code review.
- [ ] Address actionable findings, rerun affected tests, and commit the increment.
- [ ] Open only PR #26, wait for hosted CI, merge it, and confirm no other Relven PR is open before beginning the next item.
