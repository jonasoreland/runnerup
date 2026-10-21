# Third-Party Activity Import Design

## Goal

Add an offline parser for one recorded activity in a GPX or TCX file, producing a validated normalized summary and route-point list that later PRs can persist and expose from History.

## Constraints

- Keep the existing RunnerUp activity database and execution engine authoritative.
- Support GPX 1.0/1.1 track points and TCX 2.0 activity/lap/track points.
- Import one activity per file. Reject ambiguous multi-activity inputs instead of silently dropping data.
- Preserve available timestamps, coordinates, altitude, distance, heart rate, and cadence. GPX distance may be derived from consecutive valid coordinates within each track segment; do not add distance across segment boundaries. TCX cumulative track-point distance takes precedence when present.
- Parse XML directly from a byte-counted stream with DTD and external entity processing disabled; do not buffer the whole file before parsing.
- Cap an input file at 10 MiB and a route at 100,000 points. Reject malformed XML, unsupported roots, invalid coordinates, missing timestamps, and limit violations with a typed parse error.
- This increment only defines and tests parsing. It does not write the database or launch an Android picker.
- Preserve `se.hampusandersson.relven`; do not use the physical ADB device.

## Data contract

`ActivityFileImporter.read(String displayName, InputStream input)` identifies GPX or TCX from XML root/namespace (the display name is only a hint), and returns an `ImportedActivity` containing `Format`, `Sport`, start epoch seconds, duration seconds, distance meters, nullable average/max heart rate, and an immutable ordered `List<ActivityTrackPoint>`. Each point contains epoch seconds, latitude, longitude, nullable altitude meters, nullable cumulative distance meters, nullable heart rate, and nullable cadence. Numeric measurements must be finite and within domain bounds. A required coordinate must be latitude in [-90,90] and longitude in [-180,180]; positive duration/distance and supported sport values are required.

`ActivityImportException.Reason` distinguishes `UNSUPPORTED_FORMAT`, `MALFORMED_XML`, `INPUT_TOO_LARGE`, `TOO_MANY_POINTS`, `MULTIPLE_ACTIVITIES`, `MISSING_ROUTE`, and `INVALID_DATA`.

Unsupported laps, device metadata, sensor streams, and vendor extensions are ignored. Invalid required structures fail the whole file; malformed optional metric values are treated as absent. An activity without route points is rejected in this first slice because the immediate Phase Four need is route/activity interchange.

## Verification

Tests use original minimal GPX and TCX fixtures (no third-party copyrighted sample files). They verify both supported namespaces, timestamps and route ordering, TCX summary values, GPX derived distance without crossing track-segment gaps, HR/cadence/altitude preservation, malformed and hostile XML rejection, multiple activity rejection, and size/point limits.

## Follow-up slices

1. Add transaction-safe persistence to RunnerUp activity/lap/location tables with rollback tests.
2. Add a History document-picker route, import review/confirmation, result feedback, and duplicate protection.
3. Validate actual third-party files and the complete import-to-export round trip during final manual testing.
