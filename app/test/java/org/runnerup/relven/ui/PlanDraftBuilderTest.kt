package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar
import org.runnerup.workout.RelvenPlanImporter
import org.runnerup.workout.RelvenExerciseReference

class PlanDraftBuilderTest {
  private fun idSource() = sequence {
    yield("plan-id")
    yield("easy-id")
    yield("interval-id")
    var index = 1
    while (true) yield("generated-${index++}")
  }.iterator()

  @Test
  fun repeatsSelectedWorkoutTypesFromStartDateForWholePlanWeeks() {
    val ids = idSource()
    val json = PlanDraftBuilder.create(
        name = "  Base block  ",
        startDate = "2024-02-27",
        weeks = 2,
        sessions = listOf(
            PlanDraftSession(dayOfWeek = 3, workoutType = PlanWorkoutType.Easy),
            PlanDraftSession(dayOfWeek = 5, workoutType = PlanWorkoutType.Intervals)),
        timezone = "Europe/Stockholm",
        nextId = { ids.next() })

    val plan = RelvenPlanImporter.parse(json)
    assertEquals("Base block", plan.name())
    assertEquals(2, plan.workouts().size)
    assertEquals(4, plan.days().size)
    assertEquals(listOf("2024-02-27", "2024-02-29", "2024-03-05", "2024-03-07"),
        plan.days().map { it.date() }.sorted())
    assertEquals(setOf("easy-id", "interval-id"), plan.workoutNames().keys)
  }

  @Test
  fun generatesEasyIntervalAndLongRunWorkoutDocuments() {
    val ids = idSource()
    val json = PlanDraftBuilder.create(
        name = "Three day plan",
        startDate = "2026-01-05",
        weeks = 1,
        sessions = listOf(
            PlanDraftSession(2, PlanWorkoutType.Easy),
            PlanDraftSession(4, PlanWorkoutType.Intervals),
            PlanDraftSession(6, PlanWorkoutType.LongRun)),
        timezone = "UTC",
        nextId = { ids.next() })

    val plan = RelvenPlanImporter.parse(json)
    assertEquals(3, plan.workouts().size)
    assertEquals(3, plan.days().size)
    assertEquals(setOf("Easy run · 30 min", "Intervals · 4 × 4 min", "Long run · 50 min"),
        plan.workoutNames().values.toSet())
  }

  @Test
  fun generatesPinnedStrengthWorkoutsForSelectedDays() {
    val exerciseId = "3b0e6f4b-0000-4000-8000-000000000001"
    val timedExerciseId = "3b0e6f4b-0000-4000-8000-000000000002"
    val references = mapOf(
        exerciseId to RelvenExerciseReference(
            libraryRevision = 1,
            exerciseRevision = 2,
            name = "Bodyweight squat",
            instructions = "Stand tall.\nLower with control.",
            equipment = emptyList()),
        timedExerciseId to RelvenExerciseReference(
            libraryRevision = 1,
            exerciseRevision = 1,
            name = "Plank",
            instructions = "Hold a straight body position.",
            equipment = emptyList()))
    val ids = idSource()
    val source = PlanDraftBuilder.create(
        name = "Strength base",
        startDate = "2026-01-05",
        weeks = 1,
        sessions = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Strength)),
        timezone = "UTC",
        nextId = { ids.next() },
        strengthExercises = listOf(
            PlanDraftStrengthExercise(exerciseId, sets = 3, minRepetitions = 8, maxRepetitions = 12,
                restSeconds = 60),
            PlanDraftStrengthExercise(timedExerciseId, sets = 2, restSeconds = 15, durationSeconds = 30)),
        exerciseReferences = references)

    val plan = RelvenPlanImporter.parse(source, references)
    val workoutId = plan.days().single().workoutId()
    assertEquals("Bodyweight strength", plan.workoutNames().getValue(workoutId))
    val workout = plan.strengthWorkouts().getValue(workoutId)
    assertEquals("strength", workout.sport())
    assertEquals(2, workout.exercises().size)
    assertEquals("Bodyweight squat", workout.exercises().get(0).name())
    assertEquals(3, workout.exercises().get(0).sets())
    assertEquals(8, workout.exercises().get(0).minRepetitions())
    assertEquals(12, workout.exercises().get(0).maxRepetitions())
    assertEquals(60, workout.exercises().get(0).restSeconds())
    assertEquals("Plank", workout.exercises().get(1).name())
    assertEquals(30, workout.exercises().get(1).durationSeconds())
    assertEquals(2, workout.exercises().get(1).sets())
  }

  @Test
  fun requiresCatalogExercisesForStrengthTrainingDays() {
    val ids = idSource()
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      PlanDraftBuilder.create(
          name = "Strength base",
          startDate = "2026-01-05",
          weeks = 1,
          sessions = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Strength)),
          timezone = "UTC",
          nextId = { ids.next() })
    }
  }

  @Test
  fun rejectsEquipmentBasedCatalogReferencesAndInvalidTimedWork() {
    val exerciseId = "3b0e6f4b-0000-4000-8000-000000000001"
    val session = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Strength))
    val equipmentReference = mapOf(
        exerciseId to RelvenExerciseReference(1, 1, "Weighted squat", "Instructions", listOf("dumbbell")))
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      PlanDraftBuilder.create(
          "Plan", "2026-01-05", 1, session, "UTC",
          strengthExercises = listOf(PlanDraftStrengthExercise(exerciseId)),
          exerciseReferences = equipmentReference)
    }
    val bodyweightReference = mapOf(
        exerciseId to RelvenExerciseReference(1, 1, "Plank", "Instructions", emptyList()))
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      PlanDraftBuilder.create(
          "Plan", "2026-01-05", 1, session, "UTC",
          strengthExercises = listOf(PlanDraftStrengthExercise(exerciseId, durationSeconds = 0)),
          exerciseReferences = bodyweightReference)
    }
  }

  @Test
  fun scheduleCrossesYearBoundaryInThePlanTimezone() {
    val plan = RelvenPlanImporter.parse(PlanDraftBuilder.create(
        name = "New year",
        startDate = "2026-12-29",
        weeks = 2,
        sessions = listOf(PlanDraftSession(3, PlanWorkoutType.Easy)),
        timezone = "Europe/Stockholm"))

    assertEquals(listOf("2026-12-29", "2027-01-05"), plan.days().map { it.date() }.sorted())
  }

  @Test
  fun scheduleKeepsLocalWeekdaysAcrossDaylightSavingTransition() {
    val plan = RelvenPlanImporter.parse(PlanDraftBuilder.create(
        name = "DST plan",
        startDate = "2026-03-23",
        weeks = 2,
        sessions = listOf(PlanDraftSession(Calendar.MONDAY, PlanWorkoutType.Easy)),
        timezone = "Europe/Stockholm"))

    assertEquals(listOf("2026-03-23", "2026-03-30"), plan.days().map { it.date() }.sorted())
  }

  @Test
  fun rejectsInvalidNameDateDurationAndEmptySchedule() {
    fun build(name: String = "Plan", start: String = "2026-01-05", weeks: Int = 2,
        sessions: List<PlanDraftSession> = listOf(PlanDraftSession(2, PlanWorkoutType.Easy))) =
      PlanDraftBuilder.create(name, start, weeks, sessions, "UTC")

    assertThrows(IllegalArgumentException::class.java) { build(name = "  ") }
    assertThrows(IllegalArgumentException::class.java) { build(start = "2026-02-30") }
    assertThrows(IllegalArgumentException::class.java) { build(weeks = 0) }
    assertThrows(IllegalArgumentException::class.java) { build(sessions = emptyList()) }
    assertThrows(IllegalArgumentException::class.java) {
      PlanDraftBuilder.create(
          "Plan", "2026-01-05", 1,
          listOf(PlanDraftSession(2, PlanWorkoutType.Easy), PlanDraftSession(2, PlanWorkoutType.LongRun)),
          "UTC")
    }
  }
}
