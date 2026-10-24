package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionSummaryTest {
  private val workout =
      RelvenStrengthWorkoutParser.Workout(
          "summary",
          "Summary workout",
          "strength",
          listOf(
              exercise("push-ups", "Push-ups", sets = 2, duration = 30),
              exercise("plank", "Plank", sets = 1, duration = 45)),
          "{}")

  @Test
  fun summarizesActualResultsWithoutTurningUnrecordedSetsIntoZeros() {
    val session =
        StrengthSession.start("plan", "entry", workout)
            .copy(
                results =
                    listOf(
                        result(0, "push-ups", "Push-ups", 1, repetitions = 17, durationSeconds = 30),
                        result(
                            0,
                            "push-ups",
                            "Push-ups",
                            2,
                            repetitions = null,
                            durationSeconds = 30,
                            leftRepetitions = 9,
                            rightRepetitions = 8,
                            rpe = 8.0,
                            painRating = 1,
                            notes = "Stable")))

    val summary = strengthSessionSummary(session)

    assertEquals(3, summary.prescribedSetCount)
    assertEquals(2, summary.completedSetCount)
    assertEquals(0, summary.skippedSetCount)
    assertEquals(0, summary.incompleteSetCount)
    assertEquals(1, summary.unrecordedSetCount)
    assertEquals(34, summary.totalRepetitions)
    assertEquals(17, summary.bestSetRepetitions)
    assertEquals(17.0, summary.averageRepetitionsPerSet!!, 0.0)
    assertEquals(60L, summary.totalWorkSeconds)
    assertNull(summary.totalRestSeconds)

    val pushUps = summary.exercises.first()
    assertEquals(17, pushUps.bestSetRepetitions)
    assertEquals(17.0, pushUps.averageRepetitionsPerSet!!, 0.0)
    assertEquals(9, pushUps.leftRepetitions)
    assertEquals(8, pushUps.rightRepetitions)
    assertEquals(1, pushUps.sideDifference)
  }

  @Test
  fun skipsAndUnknownPerformanceStayDistinctFromMeasuredZero() {
    val skipped =
        result(
            0,
            "push-ups",
            "Push-ups",
            1,
            repetitions = null,
            durationSeconds = null,
            status = StrengthSession.SetResult.ResultStatus.SKIPPED)
    val zero =
        result(
            0,
            "push-ups",
            "Push-ups",
            2,
            repetitions = 0,
            durationSeconds = 30)
    val summary = strengthSessionSummary(StrengthSession.start("plan", "entry", workout).copy(results = listOf(skipped, zero)))

    assertEquals(3, summary.prescribedSetCount)
    assertEquals(1, summary.completedSetCount)
    assertEquals(1, summary.skippedSetCount)
    assertEquals(1, summary.unrecordedSetCount)
    assertEquals(0, summary.totalRepetitions)
    assertEquals(0, summary.bestSetRepetitions)
    assertEquals(0.0, summary.averageRepetitionsPerSet!!, 0.0)
    assertNull(summary.exercises.last().totalRepetitions)
  }

  @Test
  fun incompleteSidePairDoesNotBecomeAWholeSetTotal() {
    val leftOnly =
        result(
            0,
            "push-ups",
            "Push-ups",
            1,
            repetitions = null,
            durationSeconds = null,
            leftRepetitions = 6)

    val summary = strengthSessionSummary(StrengthSession.start("plan", "entry", workout).copy(results = listOf(leftOnly)))

    assertNull(summary.totalRepetitions)
    assertNull(summary.exercises.first().totalRepetitions)
    assertNull(summary.exercises.first().leftRepetitions)
    assertNull(summary.exercises.first().rightRepetitions)
  }

  @Test
  fun summarizesOnlyRecordedRestAndPreservesExplicitZero() {
    val session = StrengthSession.start("plan", "entry", workout).copy(
        results = listOf(
            result(0, "push-ups", "Push-ups", 1, 10, 30).copy(restDurationSeconds = 0),
            result(0, "push-ups", "Push-ups", 2, 8, 30).copy(restDurationSeconds = 12)))

    val summary = strengthSessionSummary(session)

    assertEquals(12L, summary.totalRestSeconds)
    assertEquals(12L, summary.exercises.first().totalRestSeconds)
    assertNull(summary.exercises.last().totalRestSeconds)
  }

  private fun result(
      exerciseIndex: Int,
      stepId: String,
      name: String,
      setNumber: Int,
      repetitions: Int?,
      durationSeconds: Int?,
      leftRepetitions: Int? = null,
      rightRepetitions: Int? = null,
      rpe: Double? = null,
      painRating: Int? = null,
      notes: String? = null,
      status: StrengthSession.SetResult.ResultStatus = StrengthSession.SetResult.ResultStatus.COMPLETED) =
      StrengthSession.SetResult(
          exerciseIndex = exerciseIndex,
          exerciseStepId = stepId,
          exerciseName = name,
          setNumber = setNumber,
          repetitions = repetitions,
          durationSeconds = durationSeconds,
          leftRepetitions = leftRepetitions,
          rightRepetitions = rightRepetitions,
          rpe = rpe,
          painRating = painRating,
          notes = notes,
          status = status)

  private fun exercise(id: String, name: String, sets: Int, duration: Int) =
      RelvenStrengthWorkoutParser.Exercise(
          id,
          "00000000-0000-0000-0000-000000000001",
          1,
          1,
          name,
          "Move with control.",
          sets,
          1,
          1,
          0,
          duration)
}
