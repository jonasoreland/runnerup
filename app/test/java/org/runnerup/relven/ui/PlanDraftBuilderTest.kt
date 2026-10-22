package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar
import org.runnerup.workout.RelvenPlanImporter

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
