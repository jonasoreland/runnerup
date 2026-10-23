# Exercises layout and simple run goals

## Intent

Bring the Exercises screen header back into the viewport width so its subtitle wraps and the search field follows the header without a large blank gap. Add time and distance as selectable end goals for a simple run while preserving the existing pace and configured heart-rate options.

## Scope and constraints

- Keep the existing Relven dark/orange Compose shell, bottom navigation, exercise collection actions, and catalog behavior.
- Make the Exercises header a normal width-constrained column; place import/export controls after search so they do not push the primary search interaction down.
- Store simple-run goal type and values in existing shared preferences. Clear the selected mode at app startup alongside RunnerUp's existing volatile target selection. Translate time and preferred display distance into RunnerUp step durations so its existing workout engine ends the active step and emits the established countdown cues.
- Keep old pace/heart-rate preferences working when no new goal preference exists.
- Do not use the owner's physical Android device. Verify on the isolated phone emulator.
- Continue the one-PR-at-a-time workflow.

## Considered approaches

1. Keep the header horizontally scrollable and add a separate search action: this would preserve the source of the overflow and leave the large vertical gap unresolved.
2. Remove horizontal scrolling from the header and restore normal text wrapping; move collection transfer actions below the search field. This keeps the screen's current content and makes search visible sooner. **Chosen.**
3. Add distance/time as new target dimensions: this would conflate a pace/heart-rate target with the workout's ending condition. Instead, expose one simple goal selector and map timed/distance goals onto the active step's duration, while preserving target dimensions for pace/heart rate.

## Acceptance

- At narrow phone width, the Exercises title and subtitle are fully visible, the subtitle wraps naturally, and search follows the header without the large blank area or horizontal clipping.
- Search, category filters, add exercise, import, export, and list interactions remain available.
- Simple run goal options include none, pace, time, and distance, plus heart-rate zone when configured.
- Time and preferred-unit distance goals set the active RunnerUp step's duration; pace and heart-rate remain targets and old saved preferences remain readable.
- Unit tests, lint, release checks, and emulator rendering pass before PR merge.
