package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionCodecTest {
  private val workout =
      RelvenStrengthWorkoutParser.parse(
          """{"schemaVersion":3,"id":"bodyweight","name":"Bodyweight","sport":"strength","steps":[
            {"id":"plank","kind":"segment","phase":"work","end":{"type":"time","seconds":30},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Timed repetition test","instructions":"Complete clean repetitions.","equipment":[],"images":[]},"sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":15}},
            {"id":"squat","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000002","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Squat","instructions":"Move slowly.","equipment":[],"images":[]},"sets":1,"minRepetitions":8,"maxRepetitions":12,"restSeconds":0}}
          ]}""")

  @Test
  fun roundTripsPausedAndCompletedSessionsWithTheirPlanEntryIdentity() {
    val paused = StrengthSession.start("plan-a", "entry-a", workout).pause()
    val decodedPaused = StrengthSessionCodec.decode(StrengthSessionCodec.encode(paused, null))

    assertEquals(paused, decodedPaused.first)
    assertNull(decodedPaused.second)

    var complete = paused.resume()
    repeat(30) { complete = complete.tick() }
    complete = complete.completeRepetitions(9)
    repeat(15) { complete = complete.tick() }
    repeat(30) { complete = complete.tick() }
    complete = complete.completeRepetitions(9)
    repeat(15) { complete = complete.tick() }
    complete = complete.completeRepetitions(9)
    assertTrue(complete.isComplete)
    val decodedComplete = StrengthSessionCodec.decode(StrengthSessionCodec.encode(complete, 1234L))
    assertEquals("entry-a", decodedComplete.first.scheduledEntryId)
    assertEquals(complete.executionId, decodedComplete.first.executionId)
    assertEquals(1234L, decodedComplete.second)
    assertEquals(3, decodedComplete.first.results.size)
  }

  @Test
  fun eachRetryKeepsThePlanEntryButGetsANewExecutionIdentity() {
    val first = StrengthSession.start("plan-a", "entry-a", workout)
    val retry = StrengthSession.start("plan-a", "entry-a", workout)

    assertEquals(first.scheduledEntryId, retry.scheduledEntryId)
    org.junit.Assert.assertNotEquals(first.executionId, retry.executionId)
  }

  @Test
  fun readsLegacySessionRecordsUsingScheduledEntryAsExecutionIdentity() {
    val json = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    json.put("schemaVersion", 1)
    json.remove("executionId")
    listOf(
            "pendingResults", "timerDeadlineElapsedRealtimeMillis", "timerAnchorElapsedRealtimeMillis",
            "timerClockObservedElapsedRealtimeMillis")
        .forEach(json::remove)

    val restored = StrengthSessionCodec.decode(json.toString()).first

    assertEquals("entry", restored.executionId)
  }

  @Test
  fun roundTripsConfirmedPerformanceWithoutChangingThePrescription() {
    val original = StrengthSession.start("plan", "entry", workout)
    val result =
        StrengthSession.SetResult(
            exerciseIndex = 0,
            exerciseStepId = "plank",
            exerciseName = "Timed repetition test",
            setNumber = 1,
            repetitions = 17,
            durationSeconds = 27,
            startedAtMillis = 1_000,
            completedAtMillis = 28_000,
            restDurationSeconds = 12,
            rpe = 8.5,
            painRating = 1,
            notes = "Clean repetitions",
            exerciseCategory = "upper_body")
    val state =
        original.copy(
            exerciseIndex = 0,
            setNumber = 2,
            phase = StrengthSession.Phase.REST,
            secondsRemaining = 15,
            results = listOf(result))

    val decoded = StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, null)).first

    assertEquals(state, decoded)
    assertEquals(30, decoded.workout.exercises()[0].durationSeconds())
    assertEquals(17, decoded.results.single().repetitions)
    assertEquals(27, decoded.results.single().durationSeconds)
    assertEquals("upper_body", decoded.results.single().exerciseCategory)
  }

  @Test
  fun acceptsAnExplicitZeroRepetitionResult() {
    val state =
        StrengthSession.start("plan", "entry", workout)
            .copy(
                exerciseIndex = 0,
                setNumber = 2,
                phase = StrengthSession.Phase.REST,
                secondsRemaining = 15,
                results =
                    listOf(
                        StrengthSession.SetResult(
                            0, "plank", "Timed repetition test", 1, 0, 30)))

    val decoded = StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, null)).first

    assertEquals(0, decoded.results.single().repetitions)
  }

  @Test
  fun roundTripsSeparateLeftAndRightCounts() {
    val result =
        StrengthSession.SetResult(
            exerciseIndex = 0,
            exerciseStepId = "plank",
            exerciseName = "Timed repetition test",
            setNumber = 1,
            repetitions = null,
            durationSeconds = 30,
            leftRepetitions = 8,
            rightRepetitions = 7)
    val state =
        StrengthSession.start("plan", "entry", workout)
            .copy(
                exerciseIndex = 0,
                setNumber = 2,
                phase = StrengthSession.Phase.REST,
                secondsRemaining = 15,
                results = listOf(result))

    val decoded = StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, null)).first

    assertEquals(8, decoded.results.single().leftRepetitions)
    assertEquals(7, decoded.results.single().rightRepetitions)
  }

  @Test
  fun persistsAnExplicitIncompleteMarkerWhenACountedSetIsCleared() {
    var progressed = StrengthSession.start("plan", "entry", workout)
    repeat(30) { progressed = progressed.tick() }
    progressed = progressed.completeRepetitions(9)
    val cleared = progressed.clearResult(0, 1)

    val decoded = StrengthSessionCodec.decode(StrengthSessionCodec.encode(cleared, null)).first

    assertEquals(StrengthSession.SetResult.ResultStatus.INCOMPLETE, decoded.results.single().status)
    assertNull(decoded.results.single().durationSeconds)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsRatingsOutsideTheirDocumentedRange() {
    val invalid =
        StrengthSession.start("plan", "entry", workout)
            .copy(
                exerciseIndex = 0,
                setNumber = 2,
                phase = StrengthSession.Phase.REST,
                secondsRemaining = 15,
                results =
                    listOf(
                        StrengthSession.SetResult(
                            0, "plank", "Timed repetition test", 1, 10, 30, rpe = 11.0)))

    StrengthSessionCodec.decode(StrengthSessionCodec.encode(invalid, null))
  }

  @Test
  fun readsVersionOneResultShapeAndWritesVersionThree() {
    val legacy =
        JSONObject(
            StrengthSessionCodec.encode(
                StrengthSession.start("plan", "entry", workout)
                    .copy(
                        exerciseIndex = 0,
                        setNumber = 2,
                        phase = StrengthSession.Phase.REST,
                        secondsRemaining = 15,
                        results =
                            listOf(StrengthSession.SetResult(0, "plank", "Timed repetition test", 1, null, 30))),
                null))
    legacy.put("schemaVersion", 1)
    listOf(
            "pendingResults", "timerDeadlineElapsedRealtimeMillis", "timerAnchorElapsedRealtimeMillis",
            "timerClockObservedElapsedRealtimeMillis")
        .forEach(legacy::remove)
    val oldResult = legacy.getJSONArray("results").getJSONObject(0)
    listOf(
            "startedAtMillis", "completedAtMillis", "restDurationSeconds", "leftRepetitions",
            "rightRepetitions", "rpe", "painRating", "notes", "status", "exerciseCategory")
        .forEach(oldResult::remove)

    val decoded = StrengthSessionCodec.decode(legacy.toString()).first
    val current = JSONObject(StrengthSessionCodec.encode(decoded, null))

    assertEquals(30, decoded.results.single().durationSeconds)
    assertEquals(3, current.getInt("schemaVersion"))
  }

  @Test
  fun roundTripsPendingTimedResultAndMonotonicRestDeadline() {
    val active = StrengthSession.start("plan", "entry", workout, nowElapsedRealtimeMillis = 1_000)
    val state = active.tickAt(31_000)

    val restored = StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, null)).first

    assertEquals(state, restored)
    assertEquals(StrengthSession.Phase.REST, restored.phase)
    assertEquals(1, restored.pendingResults.single().setNumber)
    assertEquals(46_000L, restored.timerDeadlineElapsedRealtimeMillis)
  }

  @Test
  fun roundTripsSkippedFinalSetWhileAnEarlierTimedResultIsStillPending() {
    val finalTimedWorkout =
        RelvenStrengthWorkoutParser.parse(
            """{"schemaVersion":3,"id":"timed","name":"Timed","sport":"strength","steps":[
              {"id":"test","kind":"segment","phase":"work","end":{"type":"time","seconds":30},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Timed test","instructions":"Complete clean repetitions.","equipment":[],"images":[]},"sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":20}}
            ]}""")
    val resting = StrengthSession.start("plan", "entry", finalTimedWorkout, 1_000).tickAt(31_000)
    val skipped = resting.skipCurrentExercise(32_000)

    val restored = StrengthSessionCodec.decode(StrengthSessionCodec.encode(skipped, null)).first

    assertEquals(StrengthSession.Phase.AWAITING_RESULT, restored.phase)
    assertEquals(listOf(1), restored.pendingResults.map { it.setNumber })
    assertEquals(StrengthSession.SetResult.ResultStatus.SKIPPED, restored.results.single().status)
  }

  @Test
  fun readsVersionTwoSessionWithoutTimerAnchors() {
    val legacy = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    legacy.put("schemaVersion", 2)
    listOf(
            "pendingResults", "timerDeadlineElapsedRealtimeMillis", "timerAnchorElapsedRealtimeMillis",
            "timerClockObservedElapsedRealtimeMillis")
        .forEach(legacy::remove)

    val restored = StrengthSessionCodec.decode(legacy.toString()).first
    val upgraded = JSONObject(StrengthSessionCodec.encode(restored, null))

    assertEquals(StrengthSession.Phase.TIMED_WORK, restored.phase)
    assertEquals(3, upgraded.getInt("schemaVersion"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsUnknownFieldsAndUnsupportedVersion() {
    val json = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    json.put("schemaVersion", 2)
    json.put("completed", true)
    StrengthSessionCodec.decode(json.toString())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsACompletionTimestampOnAnActiveSession() {
    val json = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    json.put("finishedAtMillis", 1234L)
    StrengthSessionCodec.decode(json.toString())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsCompletedStateWithoutAllSetResults() {
    val state =
        StrengthSession.start("plan", "entry", workout)
            .copy(phase = StrengthSession.Phase.COMPLETE)
    StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, 1234L))
  }
}
