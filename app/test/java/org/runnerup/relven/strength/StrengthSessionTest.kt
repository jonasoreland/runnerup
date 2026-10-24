package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionTest {
  private val workout =
      RelvenStrengthWorkoutParser.Workout(
          "workout-1",
          "Bodyweight",
          "strength",
          listOf(
              exercise("squat", "Squat", sets = 2, min = 8, max = 12, rest = 2),
              exercise("plank", "Plank", sets = 1, min = 1, max = 1, rest = 0, duration = 3)),
          "{}")

  @Test
  fun recordsActualRepetitionsAndMovesThroughRestAndExercises() {
    val first = StrengthSession.start("plan", "scheduled", workout)

    val resting = first.completeRepetitions(10)
    assertEquals(StrengthSession.Phase.REST, resting.phase)
    assertEquals(2, resting.secondsRemaining)
    assertEquals(10, resting.results.single().repetitions)

    val secondSet = resting.tick().tick()
    assertEquals(StrengthSession.Phase.REPETITIONS, secondSet.phase)
    assertEquals(2, secondSet.setNumber)

    val nextExercise = secondSet.completeRepetitions(9)
    assertEquals(StrengthSession.Phase.REST, nextExercise.phase)
    val plank = nextExercise.skipRest()
    assertEquals("Plank", plank.currentExercise().name())
    assertEquals(StrengthSession.Phase.TIMED_WORK, plank.phase)
    assertEquals(3, plank.secondsRemaining)
  }

  @Test
  fun timedSetCountsDownThenCompletesAndFinishesSession() {
    val session = StrengthSession.start("plan", "scheduled", workout)
        .completeRepetitions(8)
        .tick()
        .tick()
        .completeRepetitions(8)

    val timed = session.tick().tick().tick().tick().tick()

    assertTrue(timed.isComplete)
    assertEquals(3, timed.results.last().durationSeconds)
    assertEquals(3, timed.results.size)
  }

  @Test
  fun pauseResumePreservesActiveTimerAndSkipRestAdvancesImmediately() {
    val timed = StrengthSession.start("plan", "scheduled", workout)
        .completeRepetitions(8)
        .tick()
        .tick()
        .completeRepetitions(8)
        .skipRest()
    val paused = timed.pause()

    assertEquals(StrengthSession.Phase.PAUSED, paused.phase)
    assertEquals(3, paused.resume().secondsRemaining)
    assertEquals(StrengthSession.Phase.TIMED_WORK, paused.resume().phase)
    assertFalse(paused.resume().isComplete)
  }

  @Test
  fun existingSessionMatchesOnlyTheSamePlanAndScheduledEntry() {
    val session = StrengthSession.start("plan-a", "scheduled-a", workout)

    assertTrue(session.matchesScheduledEntry("plan-a", "scheduled-a"))
    assertFalse(session.matchesScheduledEntry("plan-b", "scheduled-a"))
    assertFalse(session.matchesScheduledEntry("plan-a", "scheduled-b"))
  }

  @Test
  fun completedScheduledWorkoutCanBeRepeatedAsANewExecution() {
    val completed =
        StrengthSession.start("plan", "scheduled", workout)
            .completeRepetitions(8)
            .skipRest()
            .completeRepetitions(8)
            .skipRest()
            .tick()
            .tick()
            .tick()
    val repeated = StrengthSession.start("plan", "scheduled", workout)

    assertTrue(completed.isComplete)
    assertTrue(repeated.matchesScheduledEntry("plan", "scheduled"))
    assertTrue(repeated.executionId != completed.executionId)
    assertTrue(repeated.results.isEmpty())
  }

  @Test
  fun zeroIsAValidExplicitResultAndHighCountsAreNotCappedAtFiveHundred() {
    val zero = StrengthSession.start("plan", "scheduled", workout).completeRepetitions(0)
    assertEquals(0, zero.results.single().repetitions)

    val high = StrengthSession.start("plan", "scheduled", workout).completeRepetitions(750)
    assertEquals(750, high.results.single().repetitions)
  }

  @Test
  fun resultCorrectionAndClearingKeepTheStableSetIdentity() {
    val session = StrengthSession.start("plan", "scheduled", workout).completeRepetitions(12)
    val corrected = session.replaceResult(session.results.single().copy(repetitions = 10, rpe = 7.5))

    assertEquals(10, corrected.results.single().repetitions)
    assertEquals(7.5, corrected.results.single().rpe!!, 0.0)

    val cleared = corrected.clearResult(0, 1).results.single()
    assertEquals(StrengthSession.SetResult.ResultStatus.INCOMPLETE, cleared.status)
    assertNull(cleared.repetitions)
    assertEquals(1, cleared.setNumber)
  }

  private fun exercise(
      id: String,
      name: String,
      sets: Int,
      min: Int,
      max: Int,
      rest: Int,
      duration: Int? = null
  ) =
      RelvenStrengthWorkoutParser.Exercise(
          id,
          "00000000-0000-0000-0000-000000000001",
          1,
          1,
          name,
          "Move with control.",
          sets,
          min,
          max,
          rest,
          duration)
}
