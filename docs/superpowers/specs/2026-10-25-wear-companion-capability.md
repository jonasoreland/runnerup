# Relven Wear Companion Capability Discovery

## Intent

When a phone detects a connected Wear OS device, it must distinguish a watch that advertises the Relven companion app from one that does not. This makes connection diagnostics and workout fallback truthful while retaining the existing application-level handshake as the final proof that Relven responds.

## Goals

- Advertise a stable Relven workout-companion capability from the Wear app.
- Query reachable nodes advertising that capability and intersect them with currently connected nodes.
- Return `COMPANION_UNAVAILABLE` when watches are connected but none advertise Relven.
- Send the existing request-correlated ping only to advertising nodes.
- Preserve the existing `NO_WATCH`, `TIMEOUT`, `PROTOCOL_MISMATCH`, and transport-error distinctions.
- Keep the RunnerUp heart-rate capability and discovery behavior unchanged.
- Explain missing or old companion installs in Settings and workout preparation.

## Non-goals

- This capability is not a substitute for the correlated application handshake.
- This change does not auto-launch the watch app, alter workout authority, or permit offline controls.
- The phone cannot infer this capability when its Wear Data Layer service itself fails to start; that remains a service/setup failure.

## Design choice

Use the Wear Data Layer `CapabilityClient` with a new stable capability declared by the Wear app manifest. This is preferable to probing every connected watch and waiting for timeouts because it explicitly advertises app availability. Intersect advertised node IDs with `NodeClient.getConnectedNodes()` so an unreachable stale capability does not count as ready. A successful capability query with an empty intersection is `COMPANION_UNAVAILABLE`; a failed capability query is `TRANSPORT_ERROR`.

After discovery, preserve the existing correlated ping, protocol-version validation, request timeout, trusted-node update, and one-at-a-time preparation handoff.

## Verification

- Unit tests cover connection/capability intersection and distinct handshake presentation.
- Run the app, Wear, and common test/lint/build matrix, including the non-Wear/F-Droid variant where available.
- A paired emulator runtime test remains separately required; never use the user's physical ADB devices.
