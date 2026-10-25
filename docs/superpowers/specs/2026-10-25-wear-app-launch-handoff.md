# Wear prepared-workout activity handoff

## Intent
After the phone receives the watch's correlated application-level `PREPARED` acknowledgement, request that the connected Wear device open Relven's prepared-workout screen. Keep the existing watch notification as the user-visible fallback when the remote launch is unavailable or the watch cannot open the activity.

## Approaches
- Keep only the current notification: reliable fallback, but the user must tap it after selecting a plan on the phone.
- Send a remote explicit component intent: not supported by `RemoteActivityHelper`, which requires an `ACTION_VIEW` browsable URI.
- Use `RemoteActivityHelper` with `relven://workout/prepared`: supported by the Wear API and an app-owned manifest route; selected.

## Behavior and boundaries
- Request remote launch only after a matching application-level prepare acknowledgement, never on transport delivery alone.
- Target the responding connected node.
- The watch manifest maps the browsable URI to `MainActivity`; its existing prepared-workout store and start screen remain authoritative.
- Do not claim the UI visibly opened when the future merely confirms the request was sent.
- Catch synchronous and asynchronous launch failures. Do not interrupt workout preparation, and retain the existing prepared-workout notification.
- Keep the dependency in the Wear-enabled phone source configuration so builds without Wear do not resolve it.

## Verification
Test the URI route, inspect the merged Wear manifest, and build Wear-enabled and no-Wear variants. Runtime remote launch still requires a compatible paired phone/watch Data Layer environment.
