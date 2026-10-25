package org.runnerup.relven.health

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.SpeedRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkoutHealthConnectMapperTest {
  private val start = Instant.parse("2026-10-24T08:00:00Z")
  private val end = Instant.parse("2026-10-24T08:30:00Z")

  @Test
  fun mapsRecordedRunAndRouteWithStableVersionedClientIds() {
    val records = mapWorkoutForHealthConnect(
        run(
            distanceMeters = 5_000.0,
            route = listOf(
                sample(start.plusSeconds(20), latitude = 59.0, heartRate = 140.0, speed = 2.8, cadence = 166.0),
                sample(start.plusSeconds(10), latitude = 58.999, heartRate = 135.0, speed = 2.5, cadence = 165.0))))

    val session = records.filterIsInstance<ExerciseSessionRecord>().single()
    val distance = records.filterIsInstance<DistanceRecord>().single()
    val heartRate = records.filterIsInstance<HeartRateRecord>().single()
    val speed = records.filterIsInstance<SpeedRecord>().single()
    val cadence = records.filterIsInstance<StepsCadenceRecord>().single()

    assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, session.exerciseType)
    assertEquals(start, session.startTime)
    assertEquals(end, session.endTime)
    assertEquals("relven-run-42-session", session.metadata.clientRecordId)
    assertEquals(3L, session.metadata.clientRecordVersion)
    val routeResult = session.exerciseRouteResult
    assertTrue(routeResult is ExerciseRouteResult.Data)
    val route = (routeResult as ExerciseRouteResult.Data).exerciseRoute
    assertEquals(2, route.route.size)
    assertEquals(start.plusSeconds(10), route.route.first().time)
    assertEquals(5_000.0, distance.distance.inMeters, 0.001)
    assertEquals(2, heartRate.samples.size)
    assertEquals(2, speed.samples.size)
    assertEquals(2, cadence.samples.size)
    assertEquals(165.0, cadence.samples.first().rate, 0.001)
    assertEquals("relven-run-42-distance", distance.metadata.clientRecordId)
    assertEquals("relven-run-42-heart-rate", heartRate.metadata.clientRecordId)
    assertEquals("relven-run-42-speed", speed.metadata.clientRecordId)
    assertEquals("relven-run-42-cadence", cadence.metadata.clientRecordId)
    assertEquals(3L, speed.metadata.clientRecordVersion)
  }

  @Test
  fun mapsActivityTypesToTheirClosestHealthConnectCategories() {
    val cases = listOf(
        HealthConnectActivity.Running to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        HealthConnectActivity.TreadmillRunning to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        HealthConnectActivity.Walking to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        HealthConnectActivity.Cycling to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        HealthConnectActivity.StationaryCycling to ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        HealthConnectActivity.Strength to ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        HealthConnectActivity.Other to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)

    cases.forEach { (activity, expectedType) ->
      val session = mapWorkoutForHealthConnect(run().copy(activity = activity))
          .filterIsInstance<ExerciseSessionRecord>()
          .single()
      assertEquals(expectedType, session.exerciseType)
    }
  }

  @Test
  fun mapsOnlyValidRecordedStrengthSetIntervalsAndRpeIntoExerciseSegments() {
    val records = mapWorkoutForHealthConnect(
        run().copy(
            activity = HealthConnectActivity.Strength,
            notes = "Knäböj set 1: 6/6 reps",
            segments = listOf(
                HealthConnectExerciseSegment(
                    start.plusSeconds(60), start.plusSeconds(90), "Knäböj", repetitions = 12,
                    leftRepetitions = 6, rightRepetitions = 6, rpe = 7.5, setNumber = 1),
                HealthConnectExerciseSegment(
                    start.plusSeconds(80), start.plusSeconds(110), "Planka", repetitions = 0,
                    durationSeconds = 30),
                HealthConnectExerciseSegment(
                    start.minusSeconds(10), start.plusSeconds(20), "Ogiltig", repetitions = 5))))

    val session = records.filterIsInstance<ExerciseSessionRecord>().single()
    assertEquals(1, session.segments.size)
    assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, session.segments.single().segmentType)
    assertEquals(12, session.segments.single().repetitions)
    assertEquals(start.plusSeconds(60), session.segments.single().startTime)
    assertEquals(start.plusSeconds(90), session.segments.single().endTime)
    val notes = requireNotNull(session.notes)
    assertTrue(notes.contains("Knäböj"))
    assertTrue(notes.contains("6/6"))
  }

  @Test
  fun mapsOnlyTimestampedNonOverlappingLapsInsideSession() {
    val records = mapWorkoutForHealthConnect(
        run().copy(
            laps = listOf(
                HealthConnectLap(start.plusSeconds(600), start.plusSeconds(1200), 1_000.0),
                HealthConnectLap(start, start.plusSeconds(600), 1_000.0),
                HealthConnectLap(start.plusSeconds(1200), end.plusSeconds(1), 1_000.0))))

    val session = records.filterIsInstance<ExerciseSessionRecord>().single()
    assertEquals(2, session.laps.size)
    assertEquals(start, session.laps.first().startTime)
    assertEquals(start.plusSeconds(600), session.laps.first().endTime)
    assertEquals(1_000.0, session.laps.first().length!!.inMeters, 0.001)
  }

  @Test
  fun doesNotExportCyclingPedalCadenceAsStepsCadence() {
    val records = mapWorkoutForHealthConnect(
        run(route = listOf(sample(start.plusSeconds(5), latitude = 59.0, cadence = 82.0)))
            .copy(activity = HealthConnectActivity.Cycling))

    assertFalse(records.any { it is StepsCadenceRecord })
  }

  @Test
  fun omitsUnknownMeasurementsAndDoesNotCreateRouteFromOnePoint() {
    val records = mapWorkoutForHealthConnect(
        run(distanceMeters = null, route = listOf(sample(start.plusSeconds(5), latitude = 59.0))))

    assertEquals(1, records.size)
    val session = records.single() as ExerciseSessionRecord
    assertTrue(session.exerciseRouteResult is ExerciseRouteResult.NoData)
    assertFalse(records.any { it is DistanceRecord || it is HeartRateRecord || it is SpeedRecord })
    assertFalse(records.any { it is StepsCadenceRecord })
  }

  @Test
  fun respectsWriteCapabilitiesAndFiltersInvalidRouteSamples() {
    val input = run(
        distanceMeters = 1_000.0,
        route = listOf(
            sample(start.plusSeconds(5), latitude = 59.0, heartRate = 0.0, speed = Double.NaN),
            sample(start.plusSeconds(6), latitude = 100.0),
            sample(start.plusSeconds(7), latitude = 59.001, heartRate = 150.0, speed = 3.0)))

    val records = mapWorkoutForHealthConnect(
        input,
        HealthConnectWriteCapabilities(exercise = true, route = false, distance = false, heartRate = true, speed = false, cadence = false))

    val session = records.filterIsInstance<ExerciseSessionRecord>().single()
    assertTrue(session.exerciseRouteResult is ExerciseRouteResult.NoData)
    assertEquals(1, records.filterIsInstance<HeartRateRecord>().single().samples.size)
    assertFalse(records.any { it is DistanceRecord || it is SpeedRecord })
    assertFalse(records.any { it is StepsCadenceRecord })
  }

  @Test
  fun rejectsUnfinishedOrInvalidSessions() {
    assertTrue(mapWorkoutForHealthConnect(run(endTime = null)).isEmpty())
    assertTrue(mapWorkoutForHealthConnect(run(endTime = start)).isEmpty())
    assertTrue(mapWorkoutForHealthConnect(run(startTime = end, endTime = start)).isEmpty())
    assertTrue(mapWorkoutForHealthConnect(
        run().copy(disposition = HealthConnectSessionDisposition.Unfinished)).isEmpty())
    assertTrue(mapWorkoutForHealthConnect(
        run().copy(disposition = HealthConnectSessionDisposition.Discarded)).isEmpty())
    assertEquals(1, mapWorkoutForHealthConnect(
        run().copy(disposition = HealthConnectSessionDisposition.Partial)).size)
  }

  private fun run(
      distanceMeters: Double? = null,
      route: List<HealthConnectRouteSample> = emptyList(),
      startTime: Instant = start,
      endTime: Instant? = end) = RecordedWorkoutForHealthConnect(
      sessionId = "run-42",
      version = 3,
      activity = HealthConnectActivity.Running,
      title = "Morgonlöpning",
      notes = "Lugn runda",
      startTime = startTime,
      endTime = endTime,
      distanceMeters = distanceMeters,
      route = route)

  private fun sample(
      time: Instant,
      latitude: Double,
      heartRate: Double? = null,
      speed: Double? = null,
      cadence: Double? = null) = HealthConnectRouteSample(
      time = time,
      latitude = latitude,
      longitude = 18.0,
      altitudeMeters = null,
      heartRateBpm = heartRate,
      speedMetersPerSecond = speed,
      cadencePerMinute = cadence)
}
