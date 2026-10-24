# Health Connect export outbox

Relven publishes only its own saved activities to Health Connect. The local RunnerUp activity database remains authoritative; this outbox is a delivery journal and never imports external records.

## Delivery and ownership

- Each local activity is identified as `activity-<database id>` and each Health Connect record type receives a deterministic `clientRecordId` under the `relven-` prefix. If deletion arrives while an upsert is pending, its tombstone includes the stable record kinds derivable from that pending payload, so an in-flight write is cleaned up as well.
- The private per-activity outbox stores an immutable workout snapshot, monotonically increasing update version, operation, status, retry details, and the record kinds Relven successfully wrote.
- WorkManager appends unique requests per activity with exponential backoff, keeping edits and deletes ordered behind an in-flight provider call. A per-session coroutine mutex also serializes worker execution within the app process. Repeating an update uses the same record identifiers and version, so retries are idempotent.
- Saving or editing queues an upsert only while export is opted in. Deleting a local activity queues deletion only for record kinds recorded as owned by Relven. It never deletes by time range or affects records from another app.
- If optional metric permission is missing, the available records are still written and the omitted permission is recorded. Required exercise-session permission waits for the user. Provider and transient failures remain retryable; permanent invalid payloads are marked failed.
- Health Connect absence or failure does not block local save, edit, or deletion. On Android versions below the supported Health Connect API gate, the bridge is not loaded.

## Source data and limitations

Only final saved activities with a positive recorded duration are eligible. GPS points are retained from the local recording, ordered by timestamp, and passed through the existing mapper's validation; missing distance, heart rate, speed, cadence, route, or lap values remain absent. Laps are exported only when the local lap and timestamped GPS markers provide valid bounds. Relven does not currently persist a trustworthy total elevation-gain measurement or active calories, so neither is synthesized for export. Strength workout details remain in Relven because Health Connect session records do not represent the app's full per-exercise performance log.

The outbox uses schema version 1, private app storage, a 50 MiB entry limit, fsync, and atomic file replacement. Export opt-in is off by default. Permission requesting, user-visible status, explicit historical backfill, and retry controls are owned by the centralized Settings integration.
