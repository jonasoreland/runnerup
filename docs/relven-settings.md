# Relven Settings

The Relven home Settings destination is the entry point for current app and device settings. The UI
uses `RelvenSettingsRepository`, which keeps Android default `SharedPreferences` as the backing
store so older RunnerUp screens and the workout engine continue reading the same values. The
repository exposes an observable snapshot to Compose and writes through to the existing preference
keys; no user preference migration or reset is required.

## Pages and behavior

| Page | Current behavior |
| --- | --- |
| Watch | Reads currently connected Wear OS Data Layer nodes. It distinguishes a connected node, no connected node, unavailable Wear support, and query failure. The phone build does not claim pairing, battery, companion-app installation, or sync success from node connectivity alone. |
| Health Connect | Opt-in, one-way workout export and persisted outbox status. See [Health Connect export architecture](health-connect.md) and [outbox](health-connect-outbox.md). |
| Training settings | Controls existing screen-awake, auto-pause, and free/interval auto-lap preferences. |
| Voice coaching | Opens the existing TTS and audio-cue preference screen, preserving its voice, cue, and language settings. |
| GPS and sensors | Shows fine-location permission, GPS-provider state, and detected phone sensors. The auto-start GPS preference, step-counter, temperature, and pressure switches share their existing runtime keys. Heart-rate source and zones open the existing HR settings. |
| Appearance | Reports the current dark Relven design. It does not show theme controls because light/system theme switching is not implemented. |
| Language and units | Shows the app's resolved locale and writes the existing `pref_unit` (`km`/`mi`) and `pref_speedunit` (`pace`/`speed`) keys. Stored measurements remain in their canonical values. Language continues to follow Android's app language setting. |
| Notifications | Reports Android's app notification permission state and opens Android notification settings. Relven has no separate reminders or per-notification controls at this time. |
| Data and export | Links to current history, plan, and exercise destinations and the custom-exercise JSON import/export flow; Health Connect opens its own Settings page. Activity and strength-session exports remain available from their detail views. |
| About | Shows the installed app version and local-storage/export information. |

## Existing preference keys used

- `pref_keep_screen_on`
- `pref_startgps`
- `pref_autopause_active`
- `pref_autolap_active`
- `pref_step_autolap_active`
- `pref_use_cadence_step_sensor`
- `pref_use_temperature_sensor`
- `pref_use_pressure_sensor`
- `pref_unit`
- `pref_speedunit`

Values outside the existing unit enums are ignored. Distance defaults follow the same locale rule
as RunnerUp's `Formatter` (miles in US/GB, kilometers elsewhere); speed defaults to pace. Sensor
availability is read from Android's `SensorManager`, not inferred from a saved toggle. Step-counter
availability and Android activity-recognition permission are shown separately. On builds without
the Wear Data Layer dependency, the watch page reports that Wear support is unavailable.

No workout-reminder system, theme selector, custom language selector, notification-category
switches, or weight-unit preference is exposed because those controls are not implemented by the
current app.
