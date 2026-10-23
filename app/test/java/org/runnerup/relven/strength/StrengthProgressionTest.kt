package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Test
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthProgressionTest {
  private val squatId = "00000000-0000-0000-0000-000000000001"
  private val plankId = "00000000-0000-0000-0000-000000000002"
  private val workout =
      RelvenStrengthWorkoutParser.Workout(
          "workout",
          "Workout",
          "strength",
          listOf(
              exercise("squat-step", squatId, "Squat", sets = 2),
              exercise("plank-step", plankId, "Plank", sets = 1, duration = 30)),
          "{}")

  @Test
  fun groupsActualSetsByStableExerciseId() {
    val older = completed("entry-old", 100, listOf(result(0, 1, 10), result(0, 2, 9)))
    val newer = completed("entry-new", 200, listOf(result(0, 1, 12), result(0, 2, 11)))

    val history = exerciseProgression(squatId, listOf(older, newer))

    assertEquals(listOf(200L, 100L), history.map { it.finishedAtMillis })
    assertEquals(listOf(12, 11), history.first().sets.map { it.repetitions })
  }

  @Test
  fun keepsRepeatedExecutionsOfOnePlannedEntryAsSeparateProgressRecords() {
    val first = completed("same-plan-entry", 100, listOf(result(0, 1, 8)))
    val retry = completed("same-plan-entry", 200, listOf(result(0, 1, 11)))
    val attempts = listOf(
        first.copy(state = first.state.copy(executionId = "execution-a")),
        retry.copy(state = retry.state.copy(executionId = "execution-b")))

    val history = exerciseProgression(squatId, attempts)

    assertEquals(listOf(200L, 100L), history.map { it.finishedAtMillis })
  }

  @Test
  fun keepsSetOrderAndExactValues() {
    val session = completed("entry", 100, listOf(result(0, 1, 7), result(0, 2, 5)))

    val history = exerciseProgression(squatId, listOf(session))

    assertEquals(listOf(1, 2), history.single().sets.map { it.setNumber })
    assertEquals(listOf(7, 5), history.single().sets.map { it.repetitions })
  }

  @Test
  fun sortsNewestFirstAndLimitsToTenSessions() {
    val sessions = (1L..12L).map { completed("entry-$it", it, listOf(result(0, 1, it.toInt()))) }

    val history = exerciseProgression(squatId, sessions)

    assertEquals((12L downTo 3L).toList(), history.map { it.finishedAtMillis })
  }

  @Test
  fun doesNotMatchByDisplayName() {
    val otherExerciseId = "00000000-0000-0000-0000-000000000003"
    val sameNameWorkout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Workout",
            "strength",
            listOf(exercise("other-step", otherExerciseId, "Squat", sets = 1)),
            "{}")
    val session = completed("entry", 100, listOf(result(0, 1, 8)), sameNameWorkout)

    assertEquals(emptyList<ExerciseProgressSession>(), exerciseProgression(squatId, listOf(session)))
  }

  @Test
  fun matchesAcrossLocalizedNamesByStableId() {
    val localizedWorkout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Workout",
            "strength",
            listOf(exercise("squat-step", squatId, "Knäböj", sets = 1)),
            "{}")
    val localizedResult =
        StrengthSession.SetResult(0, "squat-step", "Knäböj", 1, 9, null)
    val session = completed("entry", 100, listOf(localizedResult), localizedWorkout)

    assertEquals(1, exerciseProgression(squatId, listOf(session)).size)
  }

  @Test
  fun keepsTimedResultsDistinctFromRepetitions() {
    val session =
        completed(
            "entry",
            100,
            listOf(StrengthSession.SetResult(1, "plank-step", "Plank", 1, null, 30)))

    val history = exerciseProgression(plankId, listOf(session))

    assertEquals(30, history.single().sets.single().durationSeconds)
    assertEquals(null, history.single().sets.single().repetitions)
  }

  private fun completed(
      entryId: String,
      finishedAt: Long,
      results: List<StrengthSession.SetResult>,
      sourceWorkout: RelvenStrengthWorkoutParser.Workout = workout): CompletedStrengthSession {
    val lastExerciseIndex = sourceWorkout.exercises().lastIndex
    val lastExercise = sourceWorkout.exercises()[lastExerciseIndex]
    val state =
        StrengthSession(
            "plan",
            entryId,
            sourceWorkout,
            lastExerciseIndex,
            lastExercise.sets(),
            StrengthSession.Phase.COMPLETE,
            0,
            results)
    return CompletedStrengthSession(state, finishedAt)
  }

  private fun result(exerciseIndex: Int, set: Int, repetitions: Int) =
      StrengthSession.SetResult(exerciseIndex, "squat-step", "Squat", set, repetitions, null)

  private fun exercise(id: String, exerciseId: String, name: String, sets: Int, duration: Int? = null) =
      RelvenStrengthWorkoutParser.Exercise(
          id, exerciseId, 1, 1, name, "Move with control.", sets, 8, 12, 0, duration)
}
