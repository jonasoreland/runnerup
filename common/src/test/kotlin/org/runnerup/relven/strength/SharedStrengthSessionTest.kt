package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser

class SharedStrengthSessionTest {
  @Test
  fun timedRepSetCanBeLoggedAfterItsRestHasAlreadyFinished() {
    val workout = timedWorkout()

    val active = StrengthSession.start("plan", "entry", workout, 1_000L)
    val afterFirstTimer = active.tickAt(3_000L)
    val nextSetDuringRest = afterFirstTimer.tickAt(4_000L)
    val logged = nextSetDuringRest.completeRepetitions(14, 4_001L)

    assertEquals(14, logged.results.first { it.setNumber == 1 }.repetitions)
    assertEquals(StrengthSession.Phase.TIMED_WORK, logged.phase)
    assertEquals(2, logged.setNumber)
  }

  @Test
  fun preparedSessionDoesNotStartItsWorkTimerUntilBeginIsAccepted() {
    val prepared = StrengthSession.prepare("plan", "entry", timedWorkout(), 10_000L)

    assertEquals(StrengthSession.Phase.PREPARING, prepared.phase)
    assertEquals(null, prepared.timerDeadlineElapsedRealtimeMillis)
    assertEquals(0, prepared.secondsRemaining)

    val active = prepared.begin(20_000L)
    assertEquals(StrengthSession.Phase.TIMED_WORK, active.phase)
    assertEquals(22_000L, active.timerDeadlineElapsedRealtimeMillis)
  }

  private fun timedWorkout() =
      RelvenStrengthWorkoutParser.parse(
            """{"schemaVersion":3,"id":"test","name":"Test","sport":"strength","steps":[
              {"id":"test-set","kind":"segment","phase":"work","end":{"type":"time","seconds":2},
               "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
                "exerciseRevision":1,"snapshot":{"name":"Test","instructions":"Test safely.","equipment":[],"images":[]},
                "sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":1}}
            ]}""")

  @Test
  fun sharedCodecRetainsLegacyAndCurrentSessionState() {
    val workout =
        RelvenStrengthWorkoutParser.parse(
            """{"schemaVersion":3,"id":"test","name":"Test","sport":"strength","steps":[
              {"id":"set","kind":"segment","phase":"work","end":{"type":"manual"},
               "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
                "exerciseRevision":1,"snapshot":{"name":"Test","instructions":"Test safely.","equipment":[],"images":[]},
                "sets":1,"minRepetitions":1,"maxRepetitions":20,"restSeconds":0}}
            ]}""")
    val current = StrengthSession.start("plan", "entry", workout).completeRepetitions(9)

    val currentJson = StrengthSessionCodec.encode(current, 1234L)
    val restored = StrengthSessionCodec.decode(currentJson)
    assertEquals(current, restored.first)
    assertEquals(1234L, restored.second)

    val legacyJson = JSONObject(currentJson).apply {
      put("schemaVersion", 1)
      remove("executionId")
      remove("finishedAtMillis")
      remove("pendingResults")
      remove("timerDeadlineElapsedRealtimeMillis")
      remove("timerAnchorElapsedRealtimeMillis")
      remove("timerClockObservedElapsedRealtimeMillis")
      getJSONArray("results").getJSONObject(0).remove("status")
    }
    val legacy = StrengthSessionCodec.decode(legacyJson.toString())
    assertEquals("entry", legacy.first.executionId)
    assertTrue(legacy.first.isComplete)
    assertNull(legacy.second)
  }
}
