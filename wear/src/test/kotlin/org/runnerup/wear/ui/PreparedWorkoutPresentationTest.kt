package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.common.wear.WorkoutPreparationProtocol
import org.runnerup.common.tracker.TrackerState
import java.util.UUID

class PreparedWorkoutPresentationTest {
  @Test
  fun validPreparedPayloadShowsWorkoutAndNextStep() {
    val payload =
        WorkoutPreparationProtocol.encodePrepare(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "Intervaller",
            "Uppvärmning",
            "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}")

    assertEquals(
        PreparedWorkoutPresentation("Intervaller", "Uppvärmning"),
        preparedWorkoutPresentation(payload))
  }

  @Test
  fun invalidOrMissingPayloadShowsNoPreparedWorkout() {
    assertNull(preparedWorkoutPresentation(null))
    assertNull(preparedWorkoutPresentation(byteArrayOf(1, 2, 3)))
  }

  @Test
  fun preparedPreviewCanBeReplacedAndRemovedWithoutRecreatingItsConsumer() {
    val first = payload("Intervals", "Warm-up")
    val replacement = payload("Tempo", "Main set")

    assertEquals(
        PreparedWorkoutPresentation("Intervals", "Warm-up"),
        preparedWorkoutPresentation(first))
    assertEquals(
        PreparedWorkoutPresentation("Tempo", "Main set"),
        preparedWorkoutPresentation(replacement))
    assertNull(preparedWorkoutPresentation(null))
  }

  private fun payload(name: String, step: String) =
      WorkoutPreparationProtocol.encodePrepare(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          name,
          step,
          "{\"com.garmin.connect.workout.json.UserWorkoutJson\":{\"workoutSteps\":[]}}")

  @Test
  fun preparedPayloadNeverClaimsReadyWithoutTrackerEvidence() {
    assertEquals(
        "Väntar på telefonen · sensorer ej kontrollerade",
        preparedWorkoutConnectionStatus(null))
    assertEquals(
        "GPS ansluter · sensorer ej kontrollerade",
        preparedWorkoutConnectionStatus(TrackerState.CONNECTING))
    assertEquals(
        "GPS ansluten · övriga sensorer ej kontrollerade",
        preparedWorkoutConnectionStatus(TrackerState.CONNECTED))
    assertEquals(
        "Telefon ansluten · GPS ej startad",
        preparedWorkoutConnectionStatus(TrackerState.INITIALIZED))
    assertEquals(
        "Trackerfel · sensorer ej kontrollerade",
        preparedWorkoutConnectionStatus(TrackerState.ERROR))
  }
}
