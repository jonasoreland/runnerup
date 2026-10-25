# Wear connection recovery — design

## Context

The phone↔Wear Data Layer handshake and workout preparation protocols already exist. The Wear `StateService` creates a `GoogleApiClient`, but its `onConnectionSuspended` and `onConnectionFailed` callbacks are empty. The watch waiting screen therefore has no explicit way to retry a failed initial Data Layer connection and can keep presenting a stale waiting state. Prepared workout data is stored independently and must survive reconnection attempts.

## Scope

This increment makes watch-side Data Layer connection recovery observable and retryable:

- Track `CONNECTING`, `CONNECTED`, and `DISCONNECTED` at the Wear service boundary.
- Retry a suspended/failed Data Layer connection with bounded exponential backoff while the service is alive.
- Stop retry callbacks when the service is destroyed or connected.
- Expose an explicit retry action from the Wear waiting/prepared screen.
- On reconnect, reread phone node, tracker state, headers, clock synchronization, and pending durable workout/strength projections.
- Keep a prepared workout intact during transport failure. Do not start or discard it as a recovery side effect.
- Show an honest offline/reconnecting status; transport reconnect alone must not be called a verified Relven handshake.

## Non-goals for this increment

- Does not claim a paired emulator has verified phone↔watch messaging.
- Does not move GPS recording off the phone RunnerUp tracker or implement watch-only recording after phone loss.
- Does not add ownership takeover, duplicate session creation, a new workout engine, or cloud dependency.
- Does not replace the existing application-level handshake and workout preparation ACK.

## State and recovery behavior

`StateService` owns Data Layer connection state. Initial creation reports `CONNECTING`. Successful connection reports `CONNECTED`, cancels retry callbacks, restores current data, then resumes existing pending projections. Suspension/failure reports `DISCONNECTED` and schedules attempts after 1, 2, 4, 8, then 16 seconds; the interval remains capped at 16 seconds while bound/alive. A user retry cancels any scheduled delay and attempts immediately unless already connected/connecting. A connected retry performs a forced data refresh.

The watch UI may state that transport is unavailable or reconnecting. It must not use the word “verified” for Data Layer connectivity; only phone ping/pong provides application-level verification.

## Validation

- Unit-test retry delay progression, cap, stop conditions, and user retry behavior.
- Unit-test connection-state wording separately from workout-preparation or sensor readiness.
- Build and test common, phone, and Wear modules; run Wear/app lint and Spotless.
- Do not use physical-device ADB. Emulator runtime is attempted only if a clean paired emulator environment is available; otherwise report that limitation.
