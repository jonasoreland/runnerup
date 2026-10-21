# Plan Calendar Period Views Implementation Plan

> **For agentic workers:** execute inline, task by task; use TDD for each behavior change.

**Goal:** Extend the active plan calendar from month-only navigation to day, week, and month views, while keeping selected-date operations attached to the same immutable plan schedule.

**Architecture:** The pure calendar resolver will build a date range for the selected view and timezone. Compose will own the selected view and date, render the existing month grid or a compact day/week agenda, and move the selection by one day, seven days, or one clamped month. Plan entries, revisions, and RunnerUp execution remain unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Calendar/SimpleDateFormat, JUnit.

**Spec:** Master task section “Unified plan calendar”; current behavior and authority boundaries in `docs/superpowers/specs/2026-10-20-active-plan-calendar-design.md`.

## Global Constraints

- Preserve application ID `se.hampusandersson.relven`.
- Keep schedule occurrence changes as existing immutable revisions; view selection never edits plan data.
- RunnerUp remains the authoritative running engine and history owner.
- Use plan timezone for date boundaries and Monday-first week ranges.
- Do not use physical ADB until every product phase is complete and ready for manual testing.
- Maintain one open PR at a time and merge it before beginning the next PR.

## Review Focus

- Sunday/Monday boundaries and year rollover in Monday-first week navigation.
- Month navigation from dates such as the 29th–31st clamps safely in shorter months.
- Switching views preserves the selected date and its persisted entries.
- Day/week ranges show stored entries only and do not infer completion or estimates.
- Narrow phone widths and long Swedish labels remain readable and accessible.

---

### Task 1: Period-aware calendar state and navigation

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarState.kt`
- Test: `app/test/java/org/runnerup/relven/ui/PlanCalendarStateTest.kt`

Add a `PlanCalendarViewMode` (`Day`, `Week`, `Month`) and period-aware date-range resolver. Add a pure shift helper that advances selected date by 1/7 days or advances month with existing end-of-month clamping. Test localized labels, entries, Monday-first weeks, DST/timezone boundaries, leap month navigation and year rollover.

### Task 2: Compose selector, navigation, and agenda surfaces

**Files:**
- Modify: `app/src/main/kotlin/org/runnerup/relven/ui/PlanCalendarScreen.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Modify: `common/src/main/res/values-sv/strings.xml`

Add an accessible Day/Week/Month segmented selector. Preserve the month grid. Day view shows the selected date; week view shows seven Monday-first date rows with workout/rest markers and selection. Previous/next controls use the current view's date increment. Selecting any date keeps the existing selected-day details and editing callbacks unchanged.

**Execution record:** State tests were written first and failed on the missing view model and date-shift helper. The focused suite passed after implementation, including leap/month clamping, timezone-local date stepping, Swedish labels, and week labels crossing month/year boundaries. The Compose selector and agenda compile and pass app lint. The full local verification gate is recorded in `docs/implementation-status.md`; no emulator or ADB device was used. Independent review and hosted CI remain before merge.

### Task 3: Documentation and verification

**Files:**
- Modify: `docs/implementation-status.md`
- Modify: `docs/feature-parity.md`
- Modify: `docs/superpowers/plans/2026-10-21-plan-calendar-period-views.md`

Run focused calendar tests, all app unit tests, app debug build/lint, Spotless, package-ID checks and `git diff --check`. No emulator or physical device is used in this increment. Open one PR only after independent review and local gates pass; wait for hosted CI and merge before the next increment.
