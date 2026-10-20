# Feature parity

Status describes this checkout, not planned designs.

| Capability | Current status | Evidence or next check |
| --- | --- | --- |
| Phone GPS, pace and distance | Inherited, runtime unverified | Baseline APK compiled; device test pending |
| Structured intervals, repeats and targets | Inherited, runtime unverified | Workout model and selected tests present |
| Audio and Swedish TTS | Inherited, runtime unverified | Swedish voice/device test pending |
| Activity history and GPX/TCX export | Inherited, runtime unverified | File round-trip test pending |
| Wear workout display and controls | Inherited, runtime unverified | Paired-device test pending |
| Watch heart-rate measurement | Initial integration, unverified | [PR #1378 audit](wear-hr-audit.md); Galaxy Watch6 Classic, screen-off and permission tests pending |
| FIT Workout import | Bounded running-workout import with step preview | FIT file type 5 decoder validates structure and converts supported running steps; import confirmation previews nested steps, duration and targets. Real-device sample and start/finish/export checks remain. |
| Versioned JSON workouts and plans | Running-workout v3 import and locally stored plan packages | Plan packages can be validated, saved, previewed, and activated/switched/cleared. Calendar name, schedule, running segment end, and target edits create immutable schema-v2 snapshots with one-operation lineage checks. Strength editing and plan-linked execution remain pending |
| Calendar and strength details | Active-plan month calendar with immutable schedule and running definition edits | Month navigation, selected-day workout/rest details, running segment end/target edits, strength/prehab prescriptions, and one-at-a-time schedule changes are available. Earlier revisions are retained; strength details remain read-only |
| Offline exercise catalog and gym logging | Searchable bilingual bodyweight catalog plus phone-guided strength/prehab execution with local set logging | Scheduled sessions record actual reps or timed sets, support rest countdown, pause/resume, and store completion against a plan entry offline. Device timing validation, progression history, custom exercises, and media remain pending. |
| AI Plan Kit ZIP | Implemented in this increment | Workout Manager can copy the generation instructions, share the full ZIP, or save it through the document picker. The reproducible archive contains plan v1, app-managed revision v2, workout v3 schemas and the supported example; no AI service is called. |
| Free map and route coloring | Free osmdroid route map with pace, heart-rate, speed, and elevation coloring | Route values come from stored samples; pace is derived from active distance/time; missing metrics are gray and unavailable metrics are labeled. Map tiles use the existing OpenStreetMap source; online tile availability and runtime rendering remain to be checked on a device. |
| New Compose phone/watch UI | Phone foundation/preview only | Debug-only native screen rendered on a phone; production navigation and Wear UI pending |

Build success does not prove device behavior. No phone or watch device was connected during the baseline build.
