# Wear GPS Readiness Projection Design

## Intent

Show the watch the current GPS preparation state for the workout selected on the phone. GPS acquisition and route recording remain owned by the phone's existing RunnerUp Tracker. This closes the preparation-screen gap without introducing a second tracker or starting route recording before the workout starts.

## Goals

- Publish the phone's honest GPS readiness for the active prepared workout.
- Keep updates scoped to a stable preparation session ID and verified phone/watch companion.
- Show meaningful states including searching, ready, weak accuracy, permission required, disabled, unavailable, and not required.
- Recover the latest state when Wear connectivity returns.
- Remove readiness state when that preparation is cancelled or replaced.
- Preserve start availability when GPS is poor or unavailable.

## Non-goals

- Moving GPS acquisition or route recording to the watch.
- Creating another workout/session authority or GPS engine.
- Blocking start until GPS is ready.
- Claiming bidirectional runtime verification without a paired-emulator/device test.

## Approaches considered

1. **MessageClient event updates.** Simple, but transient messages can be missed during disconnection and do not provide a latest-state recovery mechanism.
2. **Wear Data Layer latest-value item (recommended).** Store one readiness value per session. It naturally coalesces updates, survives temporary disconnects, and the watch can read the current value after reopening. Its limitation is that it reports synchronized preparation state, not sensor data or a guaranteed live connection.
3. **Make the watch acquire GPS.** This would add a second tracker, complicate authority and permissions, and conflict with the phone's existing GPS recording path.

## Design

The phone computes GPS state using the existing `GpsPreparationPresentation` and Tracker readiness signals. It publishes only when the presentation changes, using a bounded, versioned payload keyed by the current session ID through Wear DataClient. The watch accepts updates only for the session it has durably prepared, stores the latest payload locally, and re-renders its prepared workout screen. A newly opened watch screen reads the latest synchronized value. Cancellation removes the session's readiness item and local cache. Missing, stale-session, or malformed data displays an honest unknown/searching fallback; it never reports GPS ready by default.

The prepared watch UI displays GPS readiness beside existing heart-rate readiness. GPS warnings do not disable Start. Strength/prehab sessions mark GPS as not required. Route samples continue to be persisted only after Tracker enters its active state.

## Constraints and risks

- Keep the existing app ID `se.hampusandersson.relven` and existing session authority.
- Use the shared Relven design language and existing Wear Compose surface.
- Do not add a paid service, network dependency, or duplicate tracker.
- Keep payload size bounded and validate protocol version, session UUID, status enum, and optional accuracy.
- Unit tests can prove encoding, session correlation, latest-state selection, and UI presentation. A paired Wear emulator is required to prove end-to-end Data Layer delivery; the known Play companion emulator restriction may prevent that here.

## Acceptance for this increment

- The same session's watch screen reflects the phone's latest actual GPS preparation state.
- A different or cancelled session cannot display an old readiness value.
- No GPS readiness message can start or record a workout.
- Phone-only start remains possible for every GPS state.
- Automated common/app/Wear tests and both debug APK builds pass.

## Future work remaining in the larger task

The broader task still requires paired runtime messaging verification, disconnect/reconnect and lifecycle coverage, full phone/watch workout event reconciliation, watch sensor behavior, and Health Connect completion-path validation. This increment does not claim those phases complete.
