package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.common.wear.WorkoutPreparationProtocol
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
}
