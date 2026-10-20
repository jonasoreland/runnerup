# Wear heart-rate permission UX implementation plan

**Goal:** Add a visible watch permission page and remove permission UI launches from the sensor service.

**Base:** `master` at the merge of PR #36. Keep one PR open at a time.

## Steps

1. [x] Add a small tested permission/state model for supported sensor availability and the API 35/36 permission boundary.
2. [x] Add a Wear pager page reachable from idle/connected and active/paused states. It displays current sensor and permission status and requests permission only after a button press.
3. [x] Remove `RequestPermissionActivity` launch from `HeartRateService`; stop the service cleanly when permission is unavailable.
4. [x] Add English and Swedish strings and update Wear audit/status documentation.
5. Run Wear debug/release builds, Wear/app tests, lint, Spotless, `git diff --check`, and verify package IDs. Open and merge exactly one PR before starting the next increment.
