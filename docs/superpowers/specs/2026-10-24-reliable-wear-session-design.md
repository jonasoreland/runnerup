# Reliable Phone ↔ Wear OS Session Design

## Objective

Replace inferred Wear status and fire-and-forget workout control with an application-verified companion protocol, then build workout preparation, coordinated execution, recovery, GPS readiness, and performance synchronization on that protocol.

## Evidence from the current implementation

- Phone `TrackerWear` and watch `StateService` use deprecated `GoogleApiClient` and legacy Wearable APIs.
- Watch `StateService.sendStart()` silently returns unless its client and the cached phone node are available. Start/pause/lap messages have no request ID or app-level acknowledgement.
- Phone `TrackerWear` relays a start command through a broadcast to the phone tracker; the watch button reflects the phone tracker state, so it is not a watch GPS action.
- `WORKOUT_PLAN` is declared and cleared but no current code writes or reads a selected workout on it.
- Phone Settings reports only a connected Wear Data Layer node. That does not prove that the Relven companion is installed or handling messages.
- Phone `StartFragment.startWorkout()` sets the workout on RunnerUp's tracker and starts it immediately.

## Target architecture

Keep RunnerUp's `Tracker` as the execution engine. Add a small versioned app-level protocol over Wear `MessageClient`: request IDs, protocol version, stable workout session IDs, command sequence/revision, acknowledgement messages, retry-safe idempotency, and event reconciliation. Use durable Data Layer items or the existing local persistence/outbox for state that must survive disconnection; use messages only for connected RPC/control. Never infer a successful handshake from node presence or message-send task completion.

The phone owns workout selection, preparation, persistence, and Health Connect outbox. When paired, a single logical workout has a stable session ID and the watch is the execution authority. The phone renders a projection and can issue validated commands. Without a compatible watch, the same session state model runs phone-only. Do not silently transfer authority during a partition.

Workout lifecycle is `IDLE → PREPARING → CONNECTING_WATCH → PREPARING_SENSORS → READY → COUNTDOWN → ACTIVE ↔ PAUSED → COMPLETING → COMPLETED`, with explicit `CANCELLED`, `FAILED`, `DISCONNECTED`, and `RECOVERING` handling. Preparation never records route distance or starts the active clock. Countdown starts at a shared planned instant calculated from a monotonic clock offset; start presses are idempotent. GPS and sensors report real states; poor GPS does not block starting.

## Delivery sequence

Each numbered delivery is a separate PR, merged before the next begins. After every merge, build phone `:app:assembleLatestDebug` and Wear `:wear:assembleDebug` from updated `master`. Use emulator-only validation and never invoke physical-device ADB.

1. **Companion handshake and diagnostics:** verified ping/ack with request correlation, protocol version, timeout and truthful Settings distinctions. This makes app reachability verifiable before using it for handoff.
2. **Preparation and workout transfer:** stable session ID, bounded/validated workout envelope, prepare/ready/cancel states, watch preparation UI and supported user-visible watch continuation. Phone Start prepares without activating RunnerUp recording; phone-only continuation remains available.
3. **Coordinated start and controls:** authority-validated start, shared countdown, idempotent pause/resume/lap/skip/finish and UI projection, using RunnerUp tracker integration.
4. **Sensors and GPS readiness:** automatic pre-acquisition with permission/provider states, no route samples before actual start, truthful watch HR/GPS readiness, no manual Start GPS control.
5. **Recovery and performance reconciliation:** stable ordered event IDs/sequences, local durable outbox, reconnect reconciliation, duplicate suppression, explicit ownership, strength repetition synchronization and final Health Connect enqueue only after authoritative persistence.
6. **Integration QA:** paired phone/watch emulator lifecycle, process/background/disconnect cases, app identity/signature compatibility checks and final build/test matrix. Physical-device testing stays deferred until the product is ready for manual testing.

## First PR contract

- Phone can discover connected Wear nodes and report those separately from companion reachability.
- A user-invoked connection test sends a unique versioned ping to a connected node and reports success only after receiving a matching pong from that same node before timeout.
- Missing app/protocol, transport failure, timeout and incompatible version are distinct outcomes where evidence allows; timeout is not mislabeled as definite app absence.
- Watch listener responds when its app process is not already foregrounded.
- Existing Tracker state, heart-rate paths, app package IDs, and workout controls remain compatible.

## Explicit limitations

- Wear `MessageClient` is best-effort and has no persistence/retry; state requiring offline durability must use a persisted outbox or DataClient.
- Android background-activity-start restrictions prohibit claiming the watch activity opened merely because a message arrived. Use an explicit user-visible continuation when the platform does not allow opening it.
- Emulator transport success does not establish Galaxy Watch hardware behavior, permission prompts, sensor accuracy, or Play signing compatibility.
- No new external cloud service is allowed.
