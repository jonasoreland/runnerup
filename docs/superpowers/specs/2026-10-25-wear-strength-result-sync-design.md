# Wear Strength Result Synchronization Design

## Intent

Synchronize explicitly confirmed strength and repetition results between Relven on the phone and Wear OS, including confirmed results from a workout that has not finished. The phone remains the authoritative durable history through its existing `StrengthSessionStore`.

## Goals

- Encode a bounded, versioned result event with stable session, result, exercise, and set identity.
- Preserve repetitions, duration, per-side values, timestamps, rest, RPE, discomfort, and notes without inferring missing values from the prescription.
- Persist an outgoing event before attempting Wear Data Layer delivery.
- Commit incoming events through the existing phone strength session store before acknowledging them.
- Make retries and duplicate delivery idempotent; reject reuse of a result ID with different content.
- Keep pending confirmed events through disconnection and partial session completion.

## Non-goals

- Moving the strength workout engine or canonical history database to Wear.
- Silently treating planned repetitions as measured results.
- Automatic conflict resolution for two edits to the same set. A conflict stays visible to the sync layer and does not overwrite confirmed history.
- Claiming runtime phone/watch delivery before paired-emulator validation.

## Approaches

1. Send complete session snapshots. This is simple to retry but increases payload size and can overwrite newer results with stale state.
2. Send a versioned event per confirmed set with an exact ID and acknowledgement. This keeps payloads small, makes retries deterministic, and matches the existing session-command journal. **Selected.**
3. Use only Wear DataClient snapshots. This provides eventual state transfer but does not give the required per-result commit acknowledgement or conflict response.

## Architecture

Add a shared `StrengthResultProtocol` codec and durable outbox store in `common`. Each device queues immutable result events locally before sending. The Wear `StateService` exposes a small enqueue API for confirmed watch results and retries pending events after reconnect. The phone `StrengthResultListenerService` validates that the source is the verified connected peer, applies an event to the active or completed record through `StrengthSessionStore`, and only then sends a correlated acknowledgement. The watch removes an event only after a matching committed/duplicate acknowledgement. Both directions use the same envelope; a phone-origin event is durably copied into the watch inbox, while a watch-origin event updates the phone's existing authoritative history.

Each result carries its own UUID and a stable `(executionId, exerciseIndex, exerciseStepId, setNumber)` identity. Including the expanded exercise index prevents a repeated workout block from reusing an ID when it references the same source step. The stored result also retains the source result ID, so the phone can recognize exact retries after process recreation. Conflicting payloads or an unknown execution are not acknowledged as committed. Null optional measurements remain null.

## Constraints and calls left to the implementer

- Keep the existing phone-only flow and `StrengthSessionStore` file format backward compatible.
- Bound the wire record to 4 KiB and the persisted outbox to 256 events / 1 MiB.
- Use synchronous durable writes before sending an event or acknowledging its commit.
- Use `Wear.MessageClient` for live delivery and retry on service reconnect; do not add a required network service.
- Preserve incomplete workout records and never require final session completion before accepting a confirmed set.
- This backend does not add a Wear workout execution or quick-entry UI. The current watch UI cannot yet produce strength results; it is a subsequent integration slice.

## Verification

Codec tests cover limits, malformed/version-mismatched payloads, optional values, and round trips. Store tests cover persistence, exact duplicates, conflicts, ordered draining, and ACK idempotency. `StrengthSessionStore` tests cover active and completed session commits, duplicate delivery, conflicting set identity, and recovery after reopen. Phone and Wear unit tests plus both debug APK assemblies, lint, and formatting validate integration.
