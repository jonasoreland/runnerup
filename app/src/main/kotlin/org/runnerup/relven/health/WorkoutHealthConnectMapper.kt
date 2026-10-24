package org.runnerup.relven.health

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.meters
import androidx.health.connect.client.units.metersPerSecond
import androidx.annotation.RequiresApi
import android.os.Build
import java.time.Instant

internal enum class HealthConnectActivity {
  Running,
  TreadmillRunning,
  Walking,
  Cycling,
  StationaryCycling,
  Strength,
  Other
}

internal enum class HealthConnectSessionDisposition { Completed, Partial, Discarded, Unfinished }

@RequiresApi(Build.VERSION_CODES.O)
internal data class HealthConnectRouteSample(
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val heartRateBpm: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val cadencePerMinute: Double? = null)

/** Lap bounds must come from actual timestamped boundaries, never from duration-only summaries. */
@RequiresApi(Build.VERSION_CODES.O)
internal data class HealthConnectLap(
    val startTime: Instant,
    val endTime: Instant,
    val distanceMeters: Double? = null)

/** Recorded values only. Planned prescriptions and summary averages are deliberately excluded. */
@RequiresApi(Build.VERSION_CODES.O)
internal data class RecordedWorkoutForHealthConnect(
    val sessionId: String,
    val version: Long,
    val activity: HealthConnectActivity,
    val title: String?,
    val notes: String?,
    val startTime: Instant,
    val endTime: Instant?,
    val disposition: HealthConnectSessionDisposition = HealthConnectSessionDisposition.Completed,
    val distanceMeters: Double? = null,
    val route: List<HealthConnectRouteSample> = emptyList(),
    val laps: List<HealthConnectLap> = emptyList())

internal data class HealthConnectWriteCapabilities(
    val exercise: Boolean = true,
    val route: Boolean = true,
    val distance: Boolean = true,
    val heartRate: Boolean = true,
    val speed: Boolean = true,
    val cadence: Boolean = true)

/** Converts one completed Relven activity into Health Connect write records; it has no read path. */
@RequiresApi(Build.VERSION_CODES.O)
internal fun mapWorkoutForHealthConnect(
    workout: RecordedWorkoutForHealthConnect,
    capabilities: HealthConnectWriteCapabilities = HealthConnectWriteCapabilities()): List<Record> {
  val endTime = workout.endTime ?: return emptyList()
  if (workout.disposition !in setOf(
          HealthConnectSessionDisposition.Completed,
          HealthConnectSessionDisposition.Partial)) return emptyList()
  if (workout.sessionId.isBlank() || workout.version < 0 || endTime <= workout.startTime) return emptyList()
  if (!capabilities.exercise) return emptyList()

  val routeSamples = workout.route
      .asSequence()
      .filter { it.time >= workout.startTime && it.time < endTime }
      .filter { it.latitude.isFinite() && it.latitude in -90.0..90.0 }
      .filter { it.longitude.isFinite() && it.longitude in -180.0..180.0 }
      .sortedBy(HealthConnectRouteSample::time)
      .distinctBy(HealthConnectRouteSample::time)
      .toList()
  val exportedRoute =
      if (capabilities.route && routeSamples.size >= 2) {
        ExerciseRoute(
            routeSamples.map { sample ->
              ExerciseRoute.Location(
                  time = sample.time,
                  latitude = sample.latitude,
                  longitude = sample.longitude,
                  altitude = sample.altitudeMeters?.takeIf(Double::isFinite)?.meters)
            })
      } else {
        null
      }

  val exportedLaps = mutableListOf<ExerciseLap>()
  var previousLapEnd: Instant? = null
  workout.laps.sortedBy(HealthConnectLap::startTime).forEach { lap ->
    if (lap.startTime < workout.startTime || lap.endTime > endTime || lap.endTime <= lap.startTime) return@forEach
    if (previousLapEnd?.let { lap.startTime < it } == true) return@forEach
    val length = lap.distanceMeters?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000.0 }?.meters
    exportedLaps += ExerciseLap(lap.startTime, lap.endTime, length)
    previousLapEnd = lap.endTime
  }

  val records = mutableListOf<Record>()
  val sessionClientId = clientRecordId(workout.sessionId, "session")
  records +=
      ExerciseSessionRecord(
          startTime = workout.startTime,
          startZoneOffset = null,
          endTime = endTime,
          endZoneOffset = null,
          metadata = metadata(sessionClientId, workout.version),
          exerciseType = workout.activity.toHealthConnectExerciseType(),
          title = workout.title?.takeIf(String::isNotBlank)?.take(200),
          notes = workout.notes?.takeIf(String::isNotBlank)?.take(1_000),
          laps = exportedLaps,
          exerciseRoute = exportedRoute)

  workout.distanceMeters
      ?.takeIf { capabilities.distance && it.isFinite() && it > 0.0 }
      ?.let { distance ->
        records +=
            DistanceRecord(
                startTime = workout.startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                distance = distance.meters,
                metadata = metadata(clientRecordId(workout.sessionId, "distance"), workout.version))
      }

  if (capabilities.heartRate) {
    val samples = routeSamples.mapNotNull { sample ->
      val bpm = sample.heartRateBpm?.takeIf { it.isFinite() && it in 20.0..260.0 } ?: return@mapNotNull null
      HeartRateRecord.Sample(sample.time, bpm.toLong())
    }
    if (samples.isNotEmpty()) {
      records +=
          HeartRateRecord(
              startTime = workout.startTime,
              startZoneOffset = null,
              endTime = endTime,
              endZoneOffset = null,
              samples = samples,
              metadata = metadata(clientRecordId(workout.sessionId, "heart-rate"), workout.version))
    }
  }

  if (capabilities.speed) {
    val samples = routeSamples.mapNotNull { sample ->
      val speed = sample.speedMetersPerSecond?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
      SpeedRecord.Sample(sample.time, speed.metersPerSecond)
    }
    if (samples.size >= 2) {
      records +=
          SpeedRecord(
              startTime = workout.startTime,
              startZoneOffset = null,
              endTime = endTime,
              endZoneOffset = null,
              samples = samples,
              metadata = metadata(clientRecordId(workout.sessionId, "speed"), workout.version))
    }
  }

  // Health Connect's StepsCadenceRecord is specifically steps/minute. Relven's cycling cadence
  // represents pedal revolutions, so it must not be exported as step cadence.
  if (capabilities.cadence && workout.activity in setOf(
          HealthConnectActivity.Running,
          HealthConnectActivity.TreadmillRunning,
          HealthConnectActivity.Walking)) {
    val samples = routeSamples.mapNotNull { sample ->
      val cadence = sample.cadencePerMinute?.takeIf { it.isFinite() && it in 1.0..10_000.0 }
          ?: return@mapNotNull null
      StepsCadenceRecord.Sample(sample.time, cadence)
    }
    if (samples.isNotEmpty()) {
      records +=
          StepsCadenceRecord(
              startTime = workout.startTime,
              startZoneOffset = null,
              endTime = endTime,
              endZoneOffset = null,
              samples = samples,
              metadata = metadata(clientRecordId(workout.sessionId, "cadence"), workout.version))
    }
  }
  return records
}

private fun HealthConnectActivity.toHealthConnectExerciseType(): Int = when (this) {
  HealthConnectActivity.Running -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
  HealthConnectActivity.TreadmillRunning -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
  HealthConnectActivity.Walking -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
  HealthConnectActivity.Cycling -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
  HealthConnectActivity.StationaryCycling -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
  HealthConnectActivity.Strength -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
  HealthConnectActivity.Other -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
}

private fun metadata(clientRecordId: String, version: Long): Metadata =
    Metadata.activelyRecorded(
        clientRecordId = clientRecordId,
        clientRecordVersion = version,
        device = Device(type = Device.TYPE_UNKNOWN))

private fun clientRecordId(sessionId: String, recordKind: String): String =
    "relven-$sessionId-$recordKind"
