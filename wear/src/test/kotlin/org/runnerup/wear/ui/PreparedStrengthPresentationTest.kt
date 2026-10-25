package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.common.wear.StrengthExecutionProtocol
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser
import java.util.UUID

class PreparedStrengthPresentationTest {
  @Test
  fun preparedStrengthSessionShowsFirstExerciseAndRealPrescription() {
    val session = StrengthSession.prepare("plan", UUID.randomUUID().toString(), workout(), 1_000L)
    val payload = StrengthExecutionProtocol.encodePrepare(session, UUID.randomUUID().toString())

    val presentation = PreparedStrengthSessionPresentationFactory.from(payload)

    assertEquals("Gympass", presentation?.workoutName)
    assertEquals("Knäböj", presentation?.exerciseName)
    assertEquals("3 set • 6–10 reps", presentation?.prescription)
    assertEquals("Håll bålen stadig.", presentation?.instructions)
  }

  @Test
  fun missingOrMalformedPayloadDoesNotCreateAWatchWorkout() {
    assertNull(PreparedStrengthSessionPresentationFactory.from(null))
    assertNull(PreparedStrengthSessionPresentationFactory.from(byteArrayOf(1, 2, 3)))
  }

  private fun workout() =
    RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"test","name":"Gympass","sport":"strength","steps":[
        {"id":"squat","kind":"segment","phase":"work","end":{"type":"manual"},
         "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
          "exerciseRevision":1,"snapshot":{"name":"Knäböj","instructions":"Håll bålen stadig.","equipment":[],"images":[]},
          "sets":3,"minRepetitions":6,"maxRepetitions":10,"restSeconds":60}}
      ]}""")
}
