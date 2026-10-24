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
  fun timedHoldRecordsDurationWithoutAskingForRepetitions() {
    val hold =
        StrengthSession.start("plan", "entry", workout)
            .completeRepetitions(8)
            .skipRest()
            .completeRepetitions(8)
            .skipRest()

    val completed = hold.tickAt(3_000)

    assertTrue(completed.isComplete)
    assertTrue(completed.pendingResults.isEmpty())
    assertEquals(3, completed.results.last().durationSeconds)
    assertNull(completed.results.last().repetitions)
  }

  @Test
  fun finalTimedHoldDoesNotHideAnEarlierPendingRepetitionResult() {
    val mixedWorkout =
        RelvenStrengthWorkoutParser.Workout(
            "mixed",
            "Mixed timed work",
            "strength",
            listOf(
                exercise("rep-test", "Push-up test", 1, 1, 20, 0, duration = 2),
                exercise("hold", "Plank", 1, 1, 1, 0, duration = 3)),
            "{}")
    val firstComplete = StrengthSession.start("plan", "entry", mixedWorkout, 1_000).tickAt(3_000)

    val finished = firstComplete.tickAt(6_000)

    assertEquals(StrengthSession.Phase.AWAITING_RESULT, finished.phase)
    assertEquals(1, finished.pendingResults.single().setNumber)
    assertEquals("Plank", finished.results.single().exerciseName)
    assertEquals(3, finished.results.single().durationSeconds)
  }

  @Test
  fun pendingTimedResultIsLoggedBeforeTheCurrentManualSet() {
    val mixedWorkout =
        RelvenStrengthWorkoutParser.Workout(
            "mixed",
            "Mixed workout",
            "strength",
            listOf(
                exercise("rep-test", "Push-up test", 1, 1, 20, 0, duration = 3),
                exercise("squat", "Squat", 1, 8, 12, 0)),
            "{}")
    val manualSet = StrengthSession.start("plan", "entry", mixedWorkout, 1_000).tickAt(4_000)

    val loggedPending = manualSet.completeRepetitions(11, nowElapsedRealtimeMillis = 4_000)
    assertEquals(0, loggedPending.results.single().exerciseIndex)
    assertEquals(StrengthSession.Phase.REPETITIONS, loggedPending.phase)

    val complete = loggedPending.completeRepetitions(12, nowElapsedRealtimeMillis = 5_000)
    assertTrue(complete.isComplete)
    assertEquals(listOf(11, 12), complete.results.map { it.repetitions })
    assertEquals(listOf(0, 1), complete.results.map { it.exerciseIndex })
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

  @Test
  fun timedResultCanBeLoggedDuringRestWithoutResettingItsTimer() {
    val timedWorkout = timedWorkout(restSeconds = 20)
    val active = StrengthSession.start("plan", "entry", timedWorkout, nowElapsedRealtimeMillis = 1_000)
    val rest = active.tickAt(31_000)
    assertEquals(StrengthSession.Phase.REST, rest.phase)
    assertEquals(2, rest.setNumber)
    assertEquals(20, rest.secondsRemaining)
    assertEquals(1, rest.pendingResults.single().setNumber)

    val beforeLog = rest.timerDeadlineElapsedRealtimeMillis
    val logged = rest.completeRepetitions(17, nowElapsedRealtimeMillis = 33_000)

    assertEquals(17, logged.results.single().repetitions)
    assertEquals(1, logged.results.single().setNumber)
    assertEquals(2, logged.setNumber)
    assertEquals(beforeLog, logged.timerDeadlineElapsedRealtimeMillis)
    assertEquals(18, logged.secondsRemaining)
  }

  @Test
  fun skippingPendingTimedResultIsExplicitAndDoesNotResetRest() {
    val active = StrengthSession.start("plan", "entry", timedWorkout(restSeconds = 20), 1_000)
    val rest = active.tickAt(31_000)
    val deadline = rest.timerDeadlineElapsedRealtimeMillis

    val skipped = rest.skipPendingTimedResult(nowElapsedRealtimeMillis = 33_000)

    val result = skipped.results.single()
    assertEquals(StrengthSession.SetResult.ResultStatus.SKIPPED, result.status)
    assertNull(result.repetitions)
    assertNull(result.durationSeconds)
    assertEquals(deadline, skipped.timerDeadlineElapsedRealtimeMillis)
    assertEquals(18, skipped.secondsRemaining)
    assertEquals(2, skipped.setNumber)
  }

  @Test
  fun skippingExerciseMarksRemainingSetsAndAdvancesWithoutInventingPerformance() {
    val skipped = StrengthSession.start("plan", "entry", workout).skipCurrentExercise(10_000)

    assertEquals(1, skipped.exerciseIndex)
    assertEquals(1, skipped.setNumber)
    assertEquals(StrengthSession.Phase.TIMED_WORK, skipped.phase)
    assertEquals(listOf(1, 2), skipped.results.map { it.setNumber })
    assertTrue(skipped.results.all { it.status == StrengthSession.SetResult.ResultStatus.SKIPPED })
    assertTrue(skipped.results.all { it.repetitions == null && it.durationSeconds == null })
  }

  @Test
  fun skippingFinalExerciseKeepsEarlierPendingResultAvailable() {
    val rest = StrengthSession.start("plan", "entry", timedWorkout(restSeconds = 20), 1_000).tickAt(31_000)

    val skipped = rest.skipCurrentExercise(nowElapsedRealtimeMillis = 32_000)

    assertEquals(StrengthSession.Phase.AWAITING_RESULT, skipped.phase)
    assertEquals(listOf(1), skipped.pendingResults.map { it.setNumber })
    assertEquals(StrengthSession.SetResult.ResultStatus.SKIPPED, skipped.results.single().status)
  }

  @Test
  fun pendingTimedResultSurvivesRestEndAndNextSetStart() {
    val timedWorkout = timedWorkout(restSeconds = 20)
    val active = StrengthSession.start("plan", "entry", timedWorkout, nowElapsedRealtimeMillis = 1_000)
    val rest = active.tickAt(31_000)
    val secondWork = rest.tickAt(51_000)

    assertEquals(StrengthSession.Phase.TIMED_WORK, secondWork.phase)
    assertEquals(2, secondWork.setNumber)
    assertEquals(1, secondWork.pendingResults.single().setNumber)

    val withFirstLogged = secondWork.completeRepetitions(15, nowElapsedRealtimeMillis = 52_000)
    val complete = withFirstLogged.tickAt(82_000).completeRepetitions(12, nowElapsedRealtimeMillis = 82_000)

    assertTrue(complete.isComplete)
    assertEquals(listOf(1, 2), complete.results.map { it.setNumber })
    assertEquals(listOf(15, 12), complete.results.map { it.repetitions })
  }

  @Test
  fun zeroRestKeepsBothTimedResultsPendingAfterDelayedUiRefresh() {
    val active = StrengthSession.start("plan", "entry", timedWorkout(restSeconds = 0), 1_000)

    val awaiting = active.tickAt(61_000)

    assertEquals(StrengthSession.Phase.AWAITING_RESULT, awaiting.phase)
    assertEquals(listOf(1, 2), awaiting.pendingResults.map { it.setNumber })
    assertTrue(awaiting.results.isEmpty())

    val firstLogged = awaiting.completeRepetitions(17, nowElapsedRealtimeMillis = 61_000)
    assertEquals(StrengthSession.Phase.AWAITING_RESULT, firstLogged.phase)
    assertEquals(2, firstLogged.pendingResults.single().setNumber)

    val bothLogged = firstLogged.completeRepetitions(12, nowElapsedRealtimeMillis = 61_000)
    assertTrue(bothLogged.isComplete)
    assertEquals(listOf(17, 12), bothLogged.results.map { it.repetitions })
  }

  @Test
  fun monotonicCountdownAccountsForDelayedUiTicksAndReanchorsAfterReboot() {
    val active = StrengthSession.start("plan", "entry", timedWorkout(restSeconds = 0), 1_000)
    val lateTick = active.tickAt(21_250)

    assertEquals(10, lateTick.secondsRemaining)
    assertEquals(31_000L, lateTick.timerDeadlineElapsedRealtimeMillis)

    val rebooted = lateTick.tickAt(500)

    assertEquals(10, rebooted.secondsRemaining)
    assertEquals(10_500L, rebooted.timerDeadlineElapsedRealtimeMillis)
  }

  private fun timedWorkout(restSeconds: Int) =
      RelvenStrengthWorkoutParser.Workout(
          "timed-workout",
          "Push-up test",
          "strength",
          listOf(exercise("push-ups", "Push-ups", sets = 2, min = 1, max = 20, rest = restSeconds, duration = 30)),
          "{}")

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
