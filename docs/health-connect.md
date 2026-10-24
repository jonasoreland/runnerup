# Health Connect export architecture

Relven is the source of truth for workouts. Health Connect is a publication target only. The
integration must remain one-way: the app declares only write permissions and must not read or
import Health Connect records.

## Current implementation slice

The app now includes AndroidX Health Connect Client 1.1.0, a write-only permission-name provider,
provider availability reporting, and a deterministic mapper from already-recorded session values
to Health Connect records. This is groundwork for the later opt-in UI, persistent outbox, worker,
history backfill, edit/delete handling, and export status. It does not yet publish workouts.

Relven supports Android API 24. The Health Connect SDK requires API 26, so `HealthConnectAvailability`
returns `UNSUPPORTED_ANDROID_VERSION` below API 26 and loads its typed SDK bridge reflectively only
after the API-level guard. The manifest uses a targeted `tools:overrideLibrary` for the SDK; this
compatibility decision is covered by an API 24 Robolectric test. Health Connect features are
unavailable on API 24 and 25; workout recording remains available.

## Write records and data limits

| Health Connect record | Relven source | Rules |
| --- | --- | --- |
| `ExerciseSessionRecord` | Completed or explicitly saved partial session identity, sport, title, notes, actual start/end | No record for an unfinished/discarded session or invalid/non-positive interval. |
| `DistanceRecord` | Positive finite recorded total distance | Omitted if unavailable, zero, negative, or non-finite. |
| `ExerciseRoute` on the session | Chronological recorded GPS points | Requires at least two valid points within the session interval. Invalid coordinates are omitted; duplicate timestamps are collapsed. Route permission is independent from session permission. |
| `HeartRateRecord` | Heart-rate samples attached to recorded route points | Omitted unless at least one plausible measured sample exists. Session average/max are not converted into invented time-series samples. |
| `SpeedRecord` | Speed samples attached to recorded route points | Omitted unless at least two positive finite measured samples exist. |
| `StepsCadenceRecord` | Cadence samples attached to recorded route points | Omitted unless a positive finite recorded sample exists. Uses Health Connect's `WRITE_STEPS` permission for steps cadence. Aggregate cadence is never expanded into samples. |

Current exercise mappings are running → running, treadmill → treadmill running, walking → walking,
biking → biking, stationary bike → stationary biking, gym → strength training, and
other/orienteering → other workout. `ExerciseLap`
mapping accepts only explicit timestamped lap bounds contained within the session and omits
overlapping intervals. The database lap summary stores duration and distance without wall-clock
bounds; those summaries alone are not sufficient to create laps. The application integration must
derive bounds only from persisted timestamped lap transitions (and omit a lap if those transitions
are unavailable). Strength set repetitions, calories, and elevation gain are not currently emitted.
Relven does not have a supported reliable calorie/elevation-gain source for those exports in this
mapping slice. Route altitude samples are included only when finite. Cadence is mapped only from
persisted per-route-point cadence samples; average cadence is not expanded into a sample series.

All exported records use stable session-derived client record IDs with distinct suffixes per record
type and a caller-supplied client record version. The future outbox must keep the same IDs on retry
and increment versions only when the Relven source session changes.

## Permissions

The current write boundary declares and returns only:

- `WRITE_EXERCISE`
- `WRITE_EXERCISE_ROUTE`
- `WRITE_DISTANCE`
- `WRITE_HEART_RATE`
- `WRITE_SPEED`
- `WRITE_STEPS` (the Health Connect permission associated with `StepsCadenceRecord`)

There are no Health Connect read permissions. Only route altitude samples are available, not reliable
total elevation gain, so `WRITE_ELEVATION_GAINED` is intentionally not requested. The final app flow
must request the write permission set only after a user action and after opt-in.

## Official API references

- [Health Connect exercise routes and route write permission](https://developer.android.com/health-and-fitness/health-connect/features/exercise-routes)
- [Health Connect workout experiences](https://developer.android.com/health-and-fitness/health-connect/experiences/workouts)
- [Health Connect metadata and client record IDs](https://developer.android.com/health-and-fitness/health-connect/metadata)
- [AndroidX Health Connect `Metadata` API](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata)

Samsung Health interoperability has not been tested against the Samsung Health app. Health Connect
records are only made available for compatible apps to read according to the user's settings and
permissions.
