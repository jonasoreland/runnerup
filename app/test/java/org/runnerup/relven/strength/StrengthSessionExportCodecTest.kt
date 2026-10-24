package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionExportCodecTest {
  private val workout =
      RelvenStrengthWorkoutParser.parse(
          """{"schemaVersion":3,"id":"benchmark","name":"Push-up benchmark","sport":"strength","steps":[
            {"id":"push-ups","kind":"segment","phase":"work","end":{"type":"time","seconds":30},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":2,"exerciseRevision":1,"snapshot":{"name":"Armhävningar","instructions":"Complete clean repetitions.","equipment":[],"images":[]},"sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":15}}
          ]}""")

  @Test
  fun roundTripsPrescriptionAndActualPartialPerformanceWithoutLosingUnknownOrZero() {
    val original = completedSession()

    val encoded = StrengthSessionExportCodec.encode(original)
    val decoded = StrengthSessionExportCodec.decode(encoded)

    assertEquals(original.state.executionId, decoded.state.executionId)
    assertEquals(original.state.planId, decoded.state.planId)
    assertEquals(original.state.scheduledEntryId, decoded.state.scheduledEntryId)
    assertEquals(original.state.workout.id(), decoded.state.workout.id())
    assertEquals(original.state.workout.name(), decoded.state.workout.name())
    assertEquals(original.state.workout.exercises(), decoded.state.workout.exercises())
    assertEquals(original.state.results, decoded.state.results)
    assertEquals(original.finishedAtMillis, decoded.finishedAtMillis)
    assertEquals("partial", JSONObject(encoded).getJSONObject("performance").getString("completion"))
    assertEquals(encoded, StrengthSessionExportCodec.encode(decoded))
  }

  @Test
  fun importRejectsDuplicateSetsAndPlanDocumentsBeforePersistence() {
    val root = JSONObject(StrengthSessionExportCodec.encode(completedSession()))
    val sets = root.getJSONObject("performance").getJSONArray("sets")
    sets.put(sets.getJSONObject(0))

    assertThrows(IllegalArgumentException::class.java) {
      StrengthSessionExportCodec.decode(root.toString())
    }
    assertThrows(IllegalArgumentException::class.java) {
      StrengthSessionExportCodec.decode("""{"format":"relven.plan","schemaVersion":1}""")
    }
  }

  @Test
  fun importRejectsValuesWhoseJsonTypesDoNotMatchTheSchema() {
    val encoded = StrengthSessionExportCodec.encode(completedSession())
    val numericString = JSONObject(encoded).also {
      it.getJSONObject("performance").getJSONArray("sets").getJSONObject(0).put("exerciseIndex", "0")
    }
    val fractionalSetNumber = JSONObject(encoded).also {
      it.getJSONObject("performance").getJSONArray("sets").getJSONObject(0).put("setNumber", 1.5)
    }
    val textRepetitions = JSONObject(encoded).also {
      it.getJSONObject("performance").getJSONArray("sets").getJSONObject(0).put("repetitions", "17")
    }

    listOf(numericString, fractionalSetNumber, textRepetitions).forEach { malformed ->
      assertThrows(IllegalArgumentException::class.java) {
        StrengthSessionExportCodec.decode(malformed.toString())
      }
    }
  }

  @Test
  fun explicitZeroIsKeptDistinctFromAnUnknownMeasurement() {
    val state = completedSession().state
    val explicitZero = state.results.first().copy(repetitions = 0, leftRepetitions = null, rightRepetitions = null)
    val unknown = state.results.last().copy(
        status = StrengthSession.SetResult.ResultStatus.INCOMPLETE,
        repetitions = null,
        durationSeconds = null)
    val completion = CompletedStrengthSession(state.copy(results = listOf(explicitZero, unknown)), 50_000)

    val decoded = StrengthSessionExportCodec.decode(StrengthSessionExportCodec.encode(completion))

    assertEquals(0, decoded.state.results.first().repetitions)
    assertEquals(null, decoded.state.results.last().repetitions)
    assertEquals(StrengthSession.SetResult.ResultStatus.INCOMPLETE, decoded.state.results.last().status)
  }

  private fun completedSession(): CompletedStrengthSession {
    val started = StrengthSession.start("plan-1", "scheduled-1", workout)
    val results = listOf(
        StrengthSession.SetResult(
            exerciseIndex = 0,
            exerciseStepId = "push-ups",
            exerciseName = "Armhävningar",
            setNumber = 1,
            repetitions = 17,
            durationSeconds = 28,
            startedAtMillis = 1_000,
            completedAtMillis = 29_000,
            restDurationSeconds = 15,
            rpe = 8.5,
            painRating = 1,
            notes = "Clean reps",
            exerciseCategory = "upper_body"),
        StrengthSession.SetResult(
            exerciseIndex = 0,
            exerciseStepId = "push-ups",
            exerciseName = "Armhävningar",
            setNumber = 2,
            repetitions = null,
            durationSeconds = null,
            status = StrengthSession.SetResult.ResultStatus.SKIPPED))
    return CompletedStrengthSession(
        started.copy(
            executionId = "b3c2a2a1-0139-4a8a-b99e-185e01bb3c70",
            phase = StrengthSession.Phase.COMPLETE,
            secondsRemaining = 0,
            results = results,
            pendingResults = emptyList(),
            timerDeadlineElapsedRealtimeMillis = null,
            timerAnchorElapsedRealtimeMillis = null,
            timerClockObservedElapsedRealtimeMillis = null),
        finishedAtMillis = 50_000)
  }
}
