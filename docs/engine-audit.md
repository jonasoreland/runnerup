# RunnerUp engine source audit

Source baseline: `ac4f8baa0473299e389f6bc70107db8ffbef23c7`. This is a source-level map of the inherited implementation; the runtime paths have not yet been verified on a device.

| Concern | Existing owner and behavior | Preservation boundary |
| --- | --- | --- |
| Workout model, steps and repeats | `app/src/main/org/runnerup/workout/Workout.java`, `Step.java`, `RepeatStep.java`, `WorkoutBuilder.java` | Use these for live execution; imported plans need an adapter into these types. |
| Time/distance completion and targets | `Step.java` tests step/lap duration, including a distance crossing estimate; `IntervalTrigger.java`, `TargetTrigger.java`, `Range.java`, `Dimension.java`, `Scope.java` handle interval/target feedback | Keep units and target semantics in the engine, not a Compose screen. |
| Pause, resume, stop, auto-pause | `tracker/Tracker.java` owns session state and exposes `start`, `pause`, `resume`, `stop`; `workout/AutoPauseTrigger.java` handles configured auto-pause | Phone `Tracker` remains the authoritative state owner. |
| GPS, pace, distance, activity recording | `Tracker.java` receives locations, applies `GpsFixFilter` to GPS-backed fixes, calculates active distance/speed and writes via its DB writer; `tracker/component/TrackerGPS.java` handles GPS setup; `db/DBHelper.java` and entities persist activity, lap and location rows | The filter rejects stale/invalid/inaccurate fixes and implausible speed spikes, gates displacement using combined reported accuracy, and starts a route segment after a >30 second gap. Reliable stationary fixes still advance workout time while persisting the last stable route coordinate with zero distance. Pause/resume resets the movement anchor, and synthetic no-GPS updates retain their existing path. Sequence tests pass; foreground/lifecycle and emulator route behavior remain to be verified. |
| Heart rate and other sensors | `tracker/component/TrackerHRM.java` consumes an `HRProvider`; `hrdevice` supplies BLE/ANT providers. Cadence, pressure, elevation and temperature live in tracker components | Wear HR should be another provider, with fresh readings and independent connection status. |
| Spoken and vibration feedback | `workout/feedback/` has audio, countdown, coaching, TTS and vibration; `tracker/component/TrackerTTS.java` attaches it | Swedish voice behavior needs physical device checks. |
| Import and export | `workout/WorkoutSerializer.java` contains an inherited workout JSON serializer; `export/format/GPX.java` and `TCX.java` serialize recordings; `content/WorkoutFileProvider.java` handles workout files | New Relven JSON and FIT require explicit schemas and round-trip tests; do not confuse a plan with an activity. |
| Maps | `app/src/osmdroid/.../MapWrapper.java` is the default free map variant; optional Mapbox and no-map variants exist | Route rendering and colored segments require runtime/metric audit. |
| Phone/watch messages | Phone `app/src/wear/.../TrackerWear.java`; watch `wear/.../service/ListenerService.java` and `StateService.java`; shared `common/src/wear/.../WearableClient.java` | Existing watch status/control protocol should remain intact. |
| Background execution | Phone session runs in `Tracker` Android service; Wear `StateService` manages display state | Test OS background restrictions and process recovery before claiming workout durability. |

`DBHelper` stores RunnerUp recordings in `runnerup.db`. The previous Relven phone app stores workouts in `relven-workouts.db`; those files do not collide, but the new app does not read the old database yet. See [migration](migration.md).

No new Gradle modules are justified at this stage. New import and planning code should have testable boundaries while adapting to the inherited workout engine.
