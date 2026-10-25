# Wear phone-peer readiness — design

## Context

The Wear service currently maps `GoogleApiClient.onConnected` directly to `WearConnectionStatus.CONNECTED`. That only means the local Wearable API client reached Google Play services; it does not prove a phone node is connected. The waiting screen therefore can show a misleading connected label. The retry action also uses default Material purple and mixes Swedish labels with the app's English fallback.

## Behavior

- Treat `CONNECTING` as the state while the Wearable API client is connecting or the phone-peer query is pending.
- Treat `CONNECTED` as true only when `NodeClient.getConnectedNodes()` contains the expected phone node, or at least one peer when no expected node ID is known.
- Report `DISCONNECTED` when the API client or expected phone peer is absent.
- On no peer, keep the prepared workout and durable session/outbox data; retry peer discovery with the existing capped backoff.
- Manual retry refreshes peer connectivity and durable Data Layer snapshots.
- Connection copy must say transport/peer status only and explicitly avoid claiming that the Relven app handshake was verified.
- Localize the new status and retry label in English and Swedish, and style the retry button with the existing Wear screen blue accent.

## Non-goals

- Does not claim the app-level ping/pong or workout transfer succeeded.
- Does not change workout ownership, GPS recording ownership, or session protocol.
- Does not introduce background polling beyond the existing bounded retry schedule.

## Validation

- Unit-test expected-node matching with missing, unrelated, and matching node IDs.
- Unit-test transport status mapping and localized string-resource selection.
- Capture the Wear waiting screen in an isolated read-only AVD.
- Run full app/Wear/common tests, lint, assemblies, Spotless, and diff checks.
- Do not use physical-device ADB.
