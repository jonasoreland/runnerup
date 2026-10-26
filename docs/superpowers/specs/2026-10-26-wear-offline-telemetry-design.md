# Wear OS Offline Workout Telemetry Design

## Intent

When the phone and watch temporarily lose their Data Layer connection during a shared running session, the watch must retain real sensor telemetry and session results so the same logical workout can be reconciled later. The existing watch-authoritative `WorkoutSessionProtocol` remains the only workout state machine and clock. The phone RunnerUp `Tracker` remains the primary route/activity engine while connected.

## Goals

- Capture watch GPS and heart-rate samples during an authoritative Wear session, including while the phone is unreachable.
- Persist telemetry locally before attempting delivery; acknowledge only contiguous, committed sequences.
- Resume transfer after reconnect, with stable session/sample identifiers and duplicate-safe acceptance.
- Merge telemetry into the existing phone workout without creating a second activity or duplicating route points.
- Keep workout state, countdown, pause/resume, and completion owned by the existing session reducer.
- Keep recording when a watch UI is backgrounded, using the Android-required foreground service and user-visible notification.
- Degrade honestly when permissions, sensors, GPS, or Health Services are unavailable; never fabricate samples.

## Non-goals

- Add another workout timer, interval engine, plan model, or activity database.
- Replace RunnerUp phone GPS tracking during a healthy paired session.
- Treat transport delivery or a requested watch launch as proof of user-visible presentation.
- Require a cloud service or use the phone's GPS as watch telemetry.

## Constraints

- Preserve `se.hampusandersson.relven` and existing workout/history formats.
- Maintain one PR at a time in the existing checkout; merge it before starting the next and rebuild both debug APKs from master after each merge.
- Do not use physical phone/watch ADB until every phase is complete and ready for manual testing.
- Use a bounded, versioned protocol. Wearable MessageClient delivery is best-effort; the durable local outbox is authoritative until phone commit is acknowledged.
- Use `ExerciseClient` only for GPS/heart-rate telemetry and sensor readiness. Session phase and start instant continue to come from the existing watch reducer and monotonic deadline.

## Approaches

1. **Health Services on Wear with durable telemetry outbox (chosen).** Use `ExerciseClient` for watch-origin GPS and heart-rate samples. Persist bounded sample batches locally and replay them idempotently to the phone. This follows the platform's sensor lifecycle, batching, and foreground-service requirements while keeping workout state in the existing reducer.
2. **Direct LocationManager plus sensor listeners.** This would duplicate provider selection, sensor availability, batching, and power handling already provided by Health Services, increasing lifecycle and battery risk.
3. **Keep recording only on the phone and reject all offline watch actions.** This preserves the current fail-safe but does not satisfy the explicit requirement to keep watch telemetry through temporary disconnection.

The chosen approach uses the official Wear OS Health Services `ExerciseClient`; its delivered samples remain telemetry, never a second session clock. While both devices record, the phone route stays primary; phone reconciliation uses the watch stream only to fill verified gaps and must deduplicate by stable session/time identity.

## Protocol and storage

- A telemetry sample carries the stable session ID, monotonically increasing per-session sequence, watch monotonic timestamp, wall-clock timestamp, optional location (latitude, longitude, horizontal accuracy, and only measured altitude/speed), and optional heart rate.
- A bounded batch carries one session, contiguous sequences, and a strict serialized byte/sample ceiling suitable for a Wearable MessageClient payload.
- A phone acknowledgement names the committed highest contiguous sequence. The watch deletes or compacts only acknowledged samples; lost acknowledgements cause safe redelivery.
- The watch outbox is a bounded append-only local store with checksummed records. Startup ignores an incomplete final record, rejects corrupt headers, and retains all complete unacknowledged records.
- The phone inbox commits each batch before acknowledging it. It must reject session mismatch, conflicting duplicate sequence, gaps, malformed measurements, and oversized payloads.

## Lifecycle

- Preparation checks permissions/provider availability and may warm GPS/heart-rate sensors through Health Services without attaching pre-start samples to the workout.
- At the authoritative watch start instant, the telemetry recorder starts and tags all later samples with that session.
- The existing session reducer remains responsible for start, pause, resume, lap/skip, and finish. Recorder actions mirror accepted reducer events and are idempotent.
- A foreground service owns active telemetry capture and survives the watch activity moving to ambient/background.
- Offline samples remain local until phone reconnection and phone commit. A stale or missing watch must never silently create a new phone session.

## Delivery sequence

1. **Telemetry contract and durable outbox (this increment):** versioned sample/batch/ack codec, bounded validation, append/recovery/ack storage, unit tests.
2. **Wear sensor recorder:** Health Services capability/permission readiness, foreground recording service, start/pause/resume/finish wired to accepted reducer events, durable GPS/heart-rate writes.
3. **Phone inbox and reconciliation:** durable idempotent batch acceptance, conversion to RunnerUp route samples, coverage-aware merge with phone route, distance/summary recomputation, and Health Connect export only after final phone persistence.
4. **Integration QA:** process death, screen-off batching, phone disconnect/reconnect, duplicate/lost ACK, route gap fill, no duplicate history, and paired emulator verification where the supported tooling permits it.

## Verification evidence

Health Services documentation states that `ExerciseClient` LOCATION data comes from the watch GPS, data is batched while the application processor is off, and location capture requires fine location permission plus a foreground service with location type. The permission model differs before and after Wear OS 6/API 36, so the recorder increment must keep legacy and modern heart-rate permissions aligned with the manifest. References: [Record an exercise with ExerciseClient](https://developer.android.com/health-and-fitness/health-services/active-data), [Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions), and [Health Services compatibility](https://developer.android.com/health-and-fitness/health-services/compatibility).
