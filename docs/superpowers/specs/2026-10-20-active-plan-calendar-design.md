# Active Plan Calendar Design

## Outcome

Add a native Android calendar view for the explicitly active saved plan. A user can move between months, choose a date, and inspect the scheduled running workout or rest-day notes for that date. The view is local and read-only; RunnerUp remains responsible for opening workout tools and recording sessions.

## Context and product constraints

- The canonical package is `relven.plan` schema version 1. Dates are one-time local dates interpreted in the package's IANA timezone.
- Entries contain running workout references or a rest day. Multiple workouts can share a date; workout names and optional notes come from the already validated package.
- A missing entry means no workout is scheduled. Do not infer completion, adherence, duration, distance, readiness, or other data that the package does not contain.
- Only the selected active plan is shown. Saved inactive plans remain unchanged.
- Keep the existing dark Relven Compose tokens, Plus Jakarta Sans typography, and RunnerUp engine integration.
- Preserve Today, Start, and History navigation; add Plan as a first-class destination. Start and History continue opening RunnerUp's existing screens.
- Do not use the user's physical Android device until every product phase is complete and the app is ready for manual testing.

## User experience

- Add a Plan destination to the current phone navigation.
- The Plan screen shows a locale-aware month title and date labels, previous/next month buttons, Monday-first localized weekday headings, and a month grid.
- Workout days have an orange marker; rest days have a muted marker; dates without a plan entry have no marker. The selected date uses the orange selection treatment, and today is indicated separately.
- Selecting any date updates the selected-day detail below the grid. Adjacent-month dates in the grid navigate to that month and select that date.
- Previous/next month navigation preserves the selected day-of-month, clamped to the final day when the destination month is shorter.
- The detail area shows every scheduled workout name or rest label, plan name, and notes when present. It does not claim that the workout is complete or start an unrelated RunnerUp workout.
- Empty states distinguish no active plan, a month with no plan items, and unavailable saved-plan data. The no-plan action opens RunnerUp's existing workout manager where plans can be imported or activated.

## State and date rules

- Build the month grid using the active plan's timezone and Gregorian calendar, with weeks beginning Monday and only the 4–6 rows needed for the month.
- Use stable `yyyy-MM-dd` keys for selected dates and plan lookup. Format visible dates in Swedish.
- Initialize the selected date to today in the active plan's timezone.
- Months may be browsed outside the explicit plan date range; those dates remain unscheduled.
- Calendar state is derived from the immutable saved package. No new persistence, schedule mutation, revision, or completion state is introduced.

## Accessibility and verification

- Month arrows and date cells are touch-sized and have localized content descriptions that include the full date and plan item when present.
- Selected state and today state are conveyed independently of marker color.
- Verify month boundaries, leap February, Monday-first alignment, adjacent-month selection, timezone-derived today, rest/workout/empty dates, and long/absent notes with unit tests.
- Run local debug/release builds, unit tests, lint, Spotless, and `git diff --check`. Do not use ADB during this increment.
