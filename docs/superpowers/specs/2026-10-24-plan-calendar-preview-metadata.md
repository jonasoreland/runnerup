# Plan Calendar Workout Preview Metadata

## Goal

Make selected-day workout previews in the Plan calendar useful at a glance by showing workout type, a defensible duration estimate when possible, and date/completion status.

## Behavior

- A preview shows its localized workout name, category, duration when available, schedule status, and a clear action to open details.
- Schedule status is `Kommande`, `Idag`, or `Tidigare` based on the plan timezone date. A completed guided strength session shows `Avklarat` regardless of schedule date.
- Running duration is estimated only when every segment's duration is derivable from its time end or from distance plus a pace/speed target. Manual or under-specified segments yield no estimate.
- Strength/prehab duration is displayed as an approximate range derived from timed work, repetition prescriptions, rest periods, and short exercise transitions. It is clearly marked as approximate.
- The plan and workout JSON schemas remain unchanged. A missing or unsupported estimate never blocks opening or starting the workout.
- Selected-day previews are ordered before the Add action. Multiple workouts remain supported; empty and rest-day states remain clear.

## Constraints

- Preserve `se.hampusandersson.relven`.
- Preserve the current Relven Compose design tokens, navigation, schedule data, completion records, and start behavior.
- Do not mark an unlinked running activity complete; its status reflects only its scheduled date.
- Keep all app UI copy localized in English and Swedish.
- Do not use the physical Android device; use unit tests and an isolated emulator.

## Approaches

1. Add preview metadata in the existing Compose screen. This keeps presentation logic tied to rendering and is hard to test.
2. Add a small pure preview projection with separately tested duration/status derivation, then render it in the existing selected-day section. This reuses existing plan/session data, adds no schema fields, and keeps estimates deterministic. **Chosen.**
3. Add duration fields to Workout v3 or plan v1. This expands interchange schemas and requires migration/compatibility work for information that can be derived locally.

## Acceptance

- Tests cover run duration calculation and unknown cases, strength duration range, localization-relevant category/status mapping, and completed/future/today/past status.
- The selected-day preview shows the new metadata and retains its details action.
- Latest debug unit tests and build pass.
- A screenshot from an isolated emulator demonstrates the selected-day layout without horizontal overflow or clipping.
