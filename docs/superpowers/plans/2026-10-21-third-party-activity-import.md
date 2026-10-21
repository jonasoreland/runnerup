# Third-Party Activity Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Parse one validated GPX or TCX running activity into an offline normalized summary and ordered route points.

**Architecture:** A pure parser boundary uses Android's pull XML parser with namespace-aware streaming and external entity processing disabled. It returns immutable parsed data only; database writes and UI remain for later PRs.

**Tech Stack:** Java, Android `XmlPullParser`, JUnit/Robolectric unit tests, checked-in minimal XML fixtures.

**Spec:** [2026-10-21-third-party-activity-import-design.md](../specs/2026-10-21-third-party-activity-import-design.md)

## Global Constraints

- Do not alter RunnerUp database schema or recording authority.
- Input limit: 10 MiB; route limit: 100,000 points.
- Reject ambiguous multi-activity documents and malformed required fields.
- Do not use the physical ADB device.
- Preserve the Play application ID `se.hampusandersson.relven`.

## Review Focus

- External entity/DTD input: must fail without resolving local or network resources.
- Oversized input and route: must fail before unbounded allocation.
- TCX with multiple activities: must fail rather than import only one silently.
- Missing optional metrics: preserve valid route points and represent absent metrics as null.
- Repeated timestamps/segments: preserve source order and reject invalid ordering only if required by the normalized contract.

---

### Task 1: Define normalized activity values and parser errors

**Files:**
- Create `app/src/main/org/runnerup/importer/ImportedActivity.java`
- Create `app/src/main/org/runnerup/importer/ActivityTrackPoint.java`
- Create `app/src/main/org/runnerup/importer/ActivityImportException.java`
- Test in `app/test/java/org/runnerup/importer/ActivityFileImporterTest.java`

**Interfaces:**
- `ActivityTrackPoint`: Java getters for epoch seconds, latitude/longitude, nullable altitude/distance/heart rate/cadence.
- `ImportedActivity`: Java getters for format (`GPX`, `TCX`), `Sport`, start epoch seconds, duration seconds, distance meters, nullable avg/max HR, and immutable ordered point list.
- `ActivityImportException.Reason`: `UNSUPPORTED_FORMAT`, `MALFORMED_XML`, `INPUT_TOO_LARGE`, `TOO_MANY_POINTS`, `MULTIPLE_ACTIVITIES`, `MISSING_ROUTE`, `INVALID_DATA`.

- [x] Write failing parser tests first; initial focused run failed because the production parser/value classes did not exist.
- [x] Implement immutable normalized value classes and typed parse errors.
- [x] Re-run focused tests and confirm parser values retain immutable point order and nullable metrics.

### Task 2: Parse GPX routes securely and within limits

**Files:**
- Create `app/src/main/org/runnerup/importer/ActivityFileImporter.java`
- Create `app/test/resources/activity-import/minimal.gpx`
- Extend `ActivityFileImporterTest.java`

**Interface:** `ActivityFileImporter.read(String displayName, InputStream source) throws ActivityImportException`.

- [x] Add GPX 1.0 and 1.1 fixtures/tests for route order, time, altitude, HR/cadence extensions, and derived distance.
- [x] Verify RED: focused parser test initially failed because parser types were missing.
- [x] Implement byte-counted streaming, secure XML parsing, and spherical derived distance.
- [x] Add and pass hostile XML, malformed coordinate, missing time, oversized input, and point-limit rejection tests.

### Task 3: Parse TCX activities securely and within limits

**Files:**
- Extend `ActivityFileImporter.java`
- Create `app/test/resources/activity-import/minimal.tcx`
- Extend `ActivityFileImporterTest.java`

- [x] Add TCX fixture coverage for activity sport, summary duration/distance, ordered timestamps/positions, altitude, HR, and cadence.
- [x] Verify RED during initial parser test run; TCX fixture exposed and helped fix the root-consumption handoff.
- [x] Implement TCX 2 activity/lap/trackpoint mapping and preserve cumulative distance and route order.
- [x] Add and pass multiple-activity, missing-route, and invalid/negative/non-finite summary rejection tests.

### Task 4: Verify full test suite and document the parser boundary

**Files:**
- Update `docs/implementation-status.md`
- Update `docs/feature-parity.md`

- [x] Run app, Wear, HR and common unit tests, latest debug/release builds, app/Wear release lint, and Spotless.
- [x] Confirm docs say parser support only; persistence, History picker, third-party corpus, and device validation remain pending.
- [x] Run `git diff --check` and inspect the final diff before review.
