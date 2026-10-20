# Active Plan Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an accessible month calendar for the active saved plan, with selected-day details, while preserving RunnerUp as the workout engine.

**Architecture:** A pure Kotlin calendar-state resolver maps a validated plan and date selection to month cells and one selected-day detail. Compose renders the resolver output and owns transient selected-date/month state; `RelvenHomeActivity` loads the active plan on resume. The existing Today, Start, and History routes stay intact, with Plan added as a fourth destination.

**Tech Stack:** Kotlin, Jetpack Compose, Android `Calendar`/`SimpleDateFormat`, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-20-active-plan-calendar-design.md`

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- RunnerUp remains the authoritative workout/session engine.
- Plan package dates are one-time local dates in the package's IANA timezone.
- Do not use the physical Android device until all product phases are complete and the app is ready for manual testing.
- Keep one PR open at a time; merge it before beginning another milestone.

## Review Focus

- Timezone boundaries: determine today and initial selection in the plan timezone.
- Month math: Monday-first offsets, year rollover, variable row count, leap February, and short-month clamping.
- Calendar honesty: show only stored workout/rest data; empty dates and completion are not inferred.
- Navigation: Today, Start, and History retain their existing destinations after Plan is added.
- Accessibility: each calendar date and month navigation control has an accurate localized spoken label and usable touch target.

---

### Task 1: Pure plan calendar state

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarState.kt`
- Test: `app/test/java/org/runnerup/relven/ui/PlanCalendarStateTest.kt`

**Interfaces:**
- Consumes: `RelvenPlanImporter.Plan` and an explicit year-month, selected date, and current instant.
- Produces: `PlanCalendarState` containing a locale-aware month label, selected date, today date, 4–6 weeks of `PlanCalendarDay` cells, and a selected-day entry list (`Workout`, `Rest`, or empty).
- Produces: `shiftPlanCalendarMonth(month: String, selectedDate: String, monthDelta: Int, timezone: String): Pair<String, String>`.

- [x] **Step 1: Write tests** for Monday-first month cells, workout/rest/no-item mapping, multiple workouts on one date, timezone-derived current date, Swedish/English labels, month rollover, leap day, and clamping Jan 31 to the final day of February.
- [x] **Step 2: Run `:app:testLatestDebugUnitTest --tests org.runnerup.relven.ui.PlanCalendarStateTest` and verify expected missing-type/function failures.**
- [x] **Step 3: Implement the immutable calendar model and date resolver** using `Calendar` with the plan timezone and stable ISO date keys.
- [x] **Step 4: Rerun the focused test and verify it passes.**

### Task 2: Calendar Compose UI and localization

**Files:**
- Create: `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarScreen.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`

**Interfaces:**
- Consumes: `PlanCalendarState`, month navigation callbacks, date-selection callback, and existing app navigation callbacks.
- Produces: `PlanCalendarScreen` showing month controls, weekday headings, date markers, selected-day details, loading, unavailable, no-plan, and empty-month states.

- [x] **Step 1: Add resource and UI-level string expectations to the test plan/state test where deterministic; add any pure accessibility-label formatter tests before its implementation.** The state test covers both Swedish and English month/date labels; resource IDs and plural-day content descriptions are compiled by the app build.
- [x] **Step 2: Verify those tests fail because the new formatter/strings are absent.** The initial focused test failed on the missing calendar model/resolver; it passed after implementation.
- [x] **Step 3: Implement the localized Compose calendar** using existing Relven colors and typography, touch-sized controls, independent today/selected treatments, and accurate spoken labels.
- [x] **Step 4: Run calendar-state tests, `spotlessCheck`, and the full debug/release/lint gate.** Static accessibility/layout audit is complete. Device rendering was unavailable without touching the protected physical device; the previously attempted shared emulator is unauthorized, and no shared ADB changes were made.

### Task 3: Plan navigation and active-plan loading

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/RelvenHomeActivity.kt`
- Modify: `docs/plan-format.md`
- Modify: `docs/feature-parity.md`
- Modify: `docs/implementation-status.md`

**Interfaces:**
- `RelvenHomeActivity` loads the active plan once on resume and supplies it to Today and Plan destinations.
- Plan navigation is first-class; Today, Start, and History keep their current route behavior.

- [x] **Step 1: Inspect the existing RunnerUp navigation routes and test seam.** Start and History are direct Activity callbacks; adding framework-heavy navigation scaffolding would not improve behavior verification.
- [x] **Step 2: Verify the existing Start/History callbacks remain unchanged during the new Plan destination work.** Confirmed by diff inspection and full compilation.
- [x] **Step 3: Add Plan navigation and active-plan propagation, preserve RunnerUp Start/History routes, and localize the monthly view.**
- [x] **Step 4: Update docs to describe the active plan calendar as read-only and date-bounded, with revisions/rescheduling still pending.**

### Task 4: Review and integrate

- [x] Run full local debug/release build, app unit tests, lint, Spotless, and `git diff --check`; inspect resulting code and responsive layouts where practical.
- [x] Request one independent review of the complete branch; fix actionable findings with test-first changes. Review caught missing visible treatment for today; added state mapping and today/selection combination tests, then confirmed the outlined states in follow-up review.
- [ ] Commit and open only one PR, attach it to this task, wait for hosted CI, merge, and verify no Relven PR remains open before starting another milestone.
- [ ] Do not use the physical Android device or shared ADB server during this increment.
