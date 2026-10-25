# Central Voice Settings Design

## Intent

Make the existing speech and workout-announcement controls usable from **Settings → Ljud och röstcoachning** without launching a separate legacy screen. Preserve the current audio-cue engine, profile storage, language preference, and test-cue implementation so existing workouts continue to read the same saved values.

## Scope

- Replace the central Voice page's launch button with the existing `AudioCueSettingsFragment` hosted inside the Settings page.
- Route the Start screen's audio-settings shortcut into the same central Voice destination when hosted by `RelvenHomeActivity`.
- Keep the Relven Settings title/back header and floating navigation shell; give the preference fragment a bounded, independently scrollable area.
- Retain the legacy `AudioCueSettingsActivity` entry points and behavior.
- Keep the legacy Activity as a fallback for any host that does not use `RelvenHomeActivity`.
- Do not move preferences to another store, change keys/defaults, or alter audio cue execution.

## Approaches

1. **Embed the existing preference fragment (chosen).** Reuses controls, profile selection, test speech, and exact preference keys. It carries some legacy Android preference styling, but avoids duplicate settings behavior and preference migration.
2. Rebuild every preference in Compose. This could match Relven styling more closely, but would duplicate complex scheme, trigger, TTS, and sensor-availability logic and create risk of preference drift.
3. Keep launching the legacy Activity. This is the current behavior and does not consolidate the destination.

The embedded fragment is isolated to the Voice destination, uses the Activity's existing FragmentManager, and receives fixed available height so its preference list scrolls within the settings shell rather than nesting in the shell's vertical scroll.

## Constraints and behavior

- Reuse the `AudioCueSettingsFragment` and its existing default/shared-preference storage.
- Preserve `AudioCueSettingsActivity` for existing callers.
- Create the fragment only when its container is attached and the FragmentManager state is not saved; retry after resume if saved state temporarily blocks attachment, and replace any stale fragment whose view still belongs to a previous Compose container.
- Do not embed the fragment in the Settings overview's unbounded vertical scroll.
- No Health Connect, workout, Wear, package identity, or database changes.

## Verification

Add a focused lifecycle/container test and update Settings navigation coverage to assert the central Voice destination no longer offers the separate-Activity launch action. Run the focused unit tests, app lint, and phone/Wear debug builds before opening one PR.
