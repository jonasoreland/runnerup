# Health Connect export for strength sessions

## Intent

Publish completed Relven strength and prehab sessions through the existing one-way Health Connect opt-in and durable export outbox. Keep Relven's strength history as the source of truth and never convert planned prescriptions into performed results.

## Approaches

- Add a parallel strength-specific Health Connect queue: rejected because it would duplicate permissions, retry, status, and deletion behavior.
- Adapt completed strength sessions into the existing outbox's recorded-workout payload: selected because it preserves the existing idempotent worker and stable record ownership.
- Export only aggregate session duration and omit all set details: rejected because Health Connect 1.1 supports exercise segments with repetitions, and Relven already stores confirmed set results.

## Design

- Give newly started sessions a persisted wall-clock start timestamp while keeping timer authority on monotonic elapsed time. Bump the strength-session codec version and continue reading older versions.
- Convert completed sessions to `ExerciseSessionRecord` with stable identity `strength-<executionId>`, actual start/end, workout title, and a concise notes summary built from confirmed result records only.
- Map a set to `ExerciseSegment` only when recorded timestamps are valid and contained in the session. Preserve explicit repetitions, bilateral/per-side values in the summary, and recorded RPE. Omit segments when boundaries are absent or overlap; never infer them from prescription, duration, or neighboring sets.
- Queue new completions only when export is opted in. Include eligible historical strength sessions in the existing explicit backfill action and status count. Reconcile after restart using the existing local history store.
- Reuse the current Health Connect `ExerciseSessionRecord` kind, worker, permission, record IDs, and update versioning. Phone↔Wear duplicate completion remains idempotent by the stable execution ID.

## Constraints and limitations

- Keep the Play application ID `se.hampusandersson.relven`; do not add read permissions or cloud services.
- Existing legacy strength sessions without a valid start timestamp are not eligible for Health Connect, because the store has no defensible start time. They remain available in Relven history and its own JSON export.
- Health Connect's segment type taxonomy has no custom exercise name. The session notes retain the exact recorded exercise names and clean result values; segment types use a known standard mapping or `UNKNOWN`.
- API 24–25 continue to skip Health Connect bridge loading.

## Verification

Test backward codec reads, wall-clock session start persistence, actual-only notes/segments, interval validation, outbox idempotency, completion enqueue, restart reconciliation, explicit history backfill, and existing running exports. Run app/common/Wear tests, lint, Spotless, and both debug APK builds before opening the one serial PR.
