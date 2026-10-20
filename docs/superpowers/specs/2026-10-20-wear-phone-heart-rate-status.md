# Phone-side Wear heart-rate status and recovery

## Goal

Show whether the paired Wear heart-rate source is connecting, active, recovering, or unavailable, and perform one bounded service restart when fresh samples stop during a workout.

## Design

- Phone `WearHRProvider` remains the source of truth for the current watch connection state; valid samples remain the only proof of sensor connection.
- Status values are `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, and `UNAVAILABLE`.
- Keep the existing 15-second sample freshness rule and 30-second initial-sample timeout. If a previously connected stream becomes stale, send one new start command to re-register the watch sensor and wait up to 30 seconds. Fresh data restores `CONNECTED`; timeout stops the watch service and marks `UNAVAILABLE`. HR settings can manually retry.
- Repeated start commands on the watch re-register the direct sensor listener once, without duplicate listeners.
- Show localized watch status in the existing phone HR settings screen and workout status/header areas. Other sensor providers keep their current behavior.
- Disconnect/close cancels pending recovery work and clears status to `DISCONNECTED`.

## Out of scope

- Changing phone workout recording or requiring heart rate for a run.
- Changing the Wear message payloads or adding a new watch UI.
- Claiming recovery works on real devices before physical paired-watch validation. No ADB device is used during development.

## Verification

Pure state tests cover status transitions, stale boundaries, and timeout recovery. Wear provider tests cover restart/timeout message behavior. Local build/test/lint gates cover the phone and Wear modules. A Galaxy Watch6 Classic must still validate background message starts, screen-off recovery, and stop/reconnect behavior.
