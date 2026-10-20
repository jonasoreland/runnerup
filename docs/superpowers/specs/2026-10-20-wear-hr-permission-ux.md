# Wear heart-rate permission UX

## Goal

Give the user an explicit, watch-owned way to grant heart-rate permission before a phone workout asks the watch sensor to start.

## Scope

- Add a heart-rate readiness page to the existing Wear pager, reachable while idle and during a run.
- Show whether the watch has a heart-rate sensor and whether the version-appropriate runtime permission is granted.
- Launch the existing permission Activity only after the user presses the page action.
- Keep the watch screen as the source for permission state; refresh it when returning from the system prompt.
- Remove Activity launch from `HeartRateService`. If permission is missing when the phone requests monitoring, stop the request without opening UI from a Service.
- Keep phone recording, Tracker, and the existing Wearable Message protocol authoritative.

## Out of scope

- Foreground service notification and sensor collection lifecycle. Those depend on the visible permission flow and will be a separate increment after this PR merges.
- Requesting background body-sensor permission or directing the user into Settings.
- Changing phone provider retry/timeout behavior, adding another watch workout controller, or using ADB.

## Permission mapping

- API 35 and lower: `android.permission.BODY_SENSORS`.
- API 36 and higher: `android.permission.health.READ_HEART_RATE`.
- The permission page presents sensor unavailable, permission needed, or ready states. A denied request remains retryable from the same visible action.

## Validation

- Unit-test API permission mapping and page state.
- Build Wear debug and release variants and run Wear/app unit tests, lint, and Spotless.
- Check manifest permission caps and retain `se.hampusandersson.relven` for release.
- No emulator, physical device, or ADB use in this increment.
