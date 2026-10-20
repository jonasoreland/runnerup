# Progress Overview Design

**Status:** Approved by the project owner's standing instruction to continue the master task without further confirmation.

## Goal

Add an offline Progress destination to the Relven home shell that summarizes recorded activity over rolling 7-day and 28-day windows.

## User experience

- The Progress screen is Swedish-first and uses the existing Relven colors, typography, spacing, and bottom navigation.
- Show recorded session count and recorded duration for the last 7 and 28 days. Show distance only when actual finite positive distances exist.
- Show loading, no-activity, and unavailable states. A retry action recovers from a database read failure.
- State that training load is unavailable until a validated model exists; do not estimate it.
- Keep existing Tracker, workout execution, and RunnerUp history ownership unchanged.

## Data rules

- Read non-deleted rows from RunnerUp's local activity database on `Dispatchers.IO`.
- Count a row only when its recorded start is valid and falls in the inclusive rolling window, its ID has not already been counted, and its recorded duration is non-negative.
- Sum distance only from finite, positive recorded distance values. Zero, negative, and non-finite values do not count as distance coverage. If a finite total overflows, retain the count of rows with recorded distance and show that the total is unavailable. Never substitute sync time or inferred distance.
- Use rolling 7×24-hour and 28×24-hour cutoffs ending at the captured current time.
- Keep aggregation pure and deterministic for tests.

## Scope

Add a `Progress` home destination, a data projection and load state, localized labels and retry, and unit tests. Do not add database schema, network access, recommendations, goals, scores, charts, or a training-load ratio.

## Verification

Test exact boundaries, duplicate IDs, invalid timestamps/durations/distances, empty data, and separate 7/28-day totals. Run app unit tests, latest debug/release builds, app debug/release lint, and Spotless. Do not use ADB or a physical device.
