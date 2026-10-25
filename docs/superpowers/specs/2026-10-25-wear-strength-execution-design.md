# Wear strength execution design

## Intent

Let a connected Relven Wear app execute a prepared strength or prehab workout and record actual repetitions without creating a second workout engine. The watch is authoritative while connected; the phone keeps the durable history and phone-only execution path.

## Goals

- Share the existing `StrengthSession` reducer and workout parser across phone and Wear.
- Transfer the prepared execution state to the watch using the existing stable execution ID.
- Persist watch state before projecting it or sending confirmed set results.
- Provide a compact Wear screen for current exercise, set, target/rest time and a no-keyboard repetition logger.
- Reuse the existing durable result outbox/inbox and make repeated submissions idempotent.
- Keep phone-only sessions and old saved-session formats readable.

## Non-goals

- Replacing RunnerUp's run tracker or changing running-session control.
- Moving phone history to Wear or adding cloud storage.
- Claiming paired-device runtime success before testing a paired emulator.
- Silent authority takeover during a disconnect.

## Approaches

1. Duplicate the strength reducer on Wear. Rejected because phone and watch could disagree on rest, progression, or completion.
2. Move the existing pure Kotlin reducer and its JSON parser/codec to `common`, then let the watch persist and project that same model. **Selected** because it keeps one definition of set progression and reuses existing phone tests and stored JSON.
3. Keep phone authority and treat watch controls as remote buttons. Rejected for connected workouts because the requested behavior assigns live execution authority to the watch.

## Architecture

The phone constructs the same validated `StrengthSession` it already stores and prepares that session for the paired watch. The watch receives and durably stores the session before acknowledging preparation. Once the user starts on either device, the watch owns reducer transitions while connected. Watch transitions are persisted before result events are queued through the PR #168 durable outbox; the phone applies those events through its existing `StrengthSessionStore`. The phone continues as the long-term history/export authority. Both apps use the same reducer and JSON codec. If a device cannot prove current authority or session identity, it must not locally advance or mark an action accepted.

## Constraints and implementation calls

- Preserve `se.hampusandersson.relven`, the existing RunnerUp run engine, and phone-only workouts.
- Keep old strength session JSON readable; only add optional fields or a backward-compatible schema version.
- Preserve monotonic timer deadlines and rebase them when a session changes device authority; do not compare raw elapsed-realtime values across devices.
- Keep the Wear logger keyboard-free, with touch-friendly decrement/increment, explicit save, and no inferred repetitions.
- Do not change confirmed historical results in place on a retry; conflict remains visible to sync handling.
- No physical-device ADB. Use unit tests and paired emulators only when they are available without disturbing shared ADB state.
