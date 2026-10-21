package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import org.runnerup.workout.RelvenPlanImporter
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession

class PlanCalendarStateTest {
  private val plan =
      RelvenPlanImporter.parse(
          """
          {"format":"relven.plan","schemaVersion":1,"id":"calendar-plan","name":"Base plan",
          "timezone":"Europe/Stockholm","workouts":[{"schemaVersion":3,"id":"run-a",
          "name":"Easy run","sport":"running","steps":[{"id":"step-a","kind":"segment",
          "phase":"work","end":{"type":"time","seconds":1800}}]}],"days":[
          {"id":"october-run","date":"2026-10-20","kind":"workout","workoutId":"run-a","notes":"Keep it easy"},
          {"id":"october-rest","date":"2026-10-21","kind":"rest","notes":"Recovery"}]}
          """.trimIndent())
  private val multiWorkoutPlan =
      RelvenPlanImporter.parse(
          """
          {"format":"relven.plan","schemaVersion":1,"id":"multi-plan","name":"Two a day",
          "timezone":"Europe/Stockholm","workouts":[
          {"schemaVersion":3,"id":"run-a","name":"Easy run","sport":"running","steps":[{"id":"step-a","kind":"segment","phase":"work","end":{"type":"time","seconds":1800}}]},
          {"schemaVersion":3,"id":"run-b","name":"Short strides","sport":"running","steps":[{"id":"step-b","kind":"segment","phase":"work","end":{"type":"time","seconds":600}}]}],"days":[
          {"id":"morning-run","date":"2026-10-20","kind":"workout","workoutId":"run-a"},
          {"id":"evening-run","date":"2026-10-20","kind":"workout","workoutId":"run-b"}]}
          """.trimIndent())
  private val strengthPlan =
      RelvenPlanImporter.parse(
          """
          {"format":"relven.plan","schemaVersion":1,"id":"strength-plan","name":"Strength week",
          "timezone":"Europe/Stockholm","workouts":[{"schemaVersion":3,"id":"strength-a",
          "name":"Bodyweight basics","sport":"strength","steps":[{"id":"squat-step","kind":"segment",
          "phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0",
          "libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Bodyweight squat",
          "instructions":"Stand with your feet about shoulder-width apart.\nBend your knees and hips as far as comfortable while keeping your chest lifted.\nPress through your feet to stand tall again.",
          "equipment":[],"images":[]},"sets":3,"minRepetitions":8,"maxRepetitions":12,"restSeconds":60}}]}],
          "days":[{"id":"strength-day","date":"2026-10-20","kind":"workout","workoutId":"strength-a"}]}
          """.trimIndent())

  @Test
  fun selectedStrengthDayCarriesPinnedSnapshotPrescription() {
    val state = planCalendarState(strengthPlan, "2026-10", "2026-10-20", 1_792_449_000_000L, Locale.ENGLISH)

    val workout = state.selectedEntries.single() as PlanCalendarEntry.Workout
    assertEquals("Bodyweight basics", workout.workoutName)
    assertEquals("Bodyweight squat", workout.strengthDetails?.exercises()?.single()?.name())
    assertEquals(3, workout.strengthDetails?.exercises()?.single()?.sets())
    assertTrue(workout.runningSegments.isEmpty())
  }

  @Test
  fun completionIsLinkedToStrengthScheduleEntryWithoutChangingRunningEntries() {
    val workout = requireNotNull(strengthPlan.strengthWorkouts()["strength-a"])
    val completed =
        StrengthSession.start(strengthPlan.id(), "strength-day", workout)
            .completeRepetitions(8)
            .skipRest()
            .completeRepetitions(9)
            .skipRest()
            .completeRepetitions(10)
    val state =
        planCalendarState(
            strengthPlan,
            "2026-10",
            "2026-10-20",
            1_792_449_000_000L,
            Locale.ENGLISH,
            mapOf("strength-day" to CompletedStrengthSession(completed, 1_792_449_000_000L)))

    assertEquals("strength-day", (state.selectedEntries.single() as PlanCalendarEntry.Workout).completion?.state?.scheduledEntryId)
  }

  @Test
  fun buildsMondayFirstMonthCellsAndMapsWorkoutRestAndEmptyDates() {
    val state = calendarState(plan, "2026-10", "2026-10-20", 1_792_449_000_000L)

    assertEquals("Oktober 2026", state.monthLabel)
    assertEquals(35, state.days.size)
    assertEquals("2026-09-28", state.days.first().date)
    assertFalse(state.days.first().inDisplayedMonth)
    assertEquals("2026-10-20", state.days[22].date)
    assertTrue(state.days[22].inDisplayedMonth)
    assertTrue(state.days[22].isSelected)
    assertTrue(state.days[22].isToday)
    val runningEntry = state.selectedEntries.single() as PlanCalendarEntry.Workout
    assertEquals("october-run", runningEntry.sessionId)
    assertEquals("run-a", runningEntry.workoutId)
    assertEquals("Easy run", runningEntry.workoutName)
    assertEquals("Keep it easy", runningEntry.notes)
    assertEquals("step-a", runningEntry.runningSegments.single().id)
    assertEquals(1800.0, runningEntry.runningSegments.single().end.value!!, 0.0)
    assertEquals(
        listOf(PlanCalendarEntry.Rest("october-rest", "Recovery")),
        calendarState(plan, "2026-10", "2026-10-21", 1_792_449_000_000L).selectedEntries)
    assertTrue(
        calendarState(plan, "2026-10", "2026-10-22", 1_792_449_000_000L)
            .selectedEntries.isEmpty())
  }

  @Test
  fun resolvesTodayUsingPlanTimezoneAndBuildsLeapFebruary() {
    val state = calendarState(plan, "2026-10", "2026-10-20", 1_792_449_000_000L)
    assertEquals("2026-10-20", state.todayDate)

    val february = calendarState(plan, "2024-02", "2024-02-29", 1_792_449_000_000L)
    assertEquals(35, february.days.size)
    assertEquals("2024-01-29", february.days.first().date)
    assertTrue(february.days.any { it.date == "2024-02-29" && it.inDisplayedMonth })
  }

  @Test
  fun shiftsAcrossYearAndClampsSelectedDateForShorterMonth() {
    assertEquals(
        "2027-01" to "2027-01-31",
        shiftPlanCalendarMonth("2026-12", "2026-12-31", 1, "Europe/Stockholm"))
    assertEquals(
        "2026-02" to "2026-02-28",
        shiftPlanCalendarMonth("2026-01", "2026-01-31", 1, "Europe/Stockholm"))
  }

  @Test
  fun preservesEveryWorkoutScheduledOnTheSameDate() {
    val state = calendarState(multiWorkoutPlan, "2026-10", "2026-10-20", 1_792_449_000_000L)

    assertEquals(
        listOf("Easy run", "Short strides"),
        state.selectedEntries.map { (it as PlanCalendarEntry.Workout).workoutName })
    assertEquals(2, state.days.first { it.date == "2026-10-20" }.entries.size)
  }

  @Test
  fun scheduleEditAvailabilityRespectsRestCollisionsAndFinalEntry() {
    val workout = PlanCalendarEntry.Workout("run", "easy", "Easy run", "")
    val rest = PlanCalendarEntry.Rest("rest", "")

    assertTrue(canAddWorkoutOnDate(emptyList()))
    assertTrue(canAddWorkoutOnDate(listOf(workout)))
    assertFalse(canAddWorkoutOnDate(listOf(rest)))
    assertTrue(canAddRestOnDate(emptyList()))
    assertFalse(canAddRestOnDate(listOf(workout)))
    assertFalse(canAddRestOnDate(listOf(rest)))
    assertFalse(canRemovePlanEntry(1))
    assertTrue(canRemovePlanEntry(2))
  }

  @Test
  fun planNameValidationRequiresTrimmedDistinctTextWithinLimit() {
    assertTrue(canRenamePlanName("New name", "Current name"))
    assertTrue(canRenamePlanName("  New name  ", "Current name"))
    assertFalse(canRenamePlanName("  ", "Current name"))
    assertFalse(canRenamePlanName("Current name", "Current name"))
    assertFalse(canRenamePlanName("x".repeat(161), "Current name"))
  }

  @Test
  fun workoutNameValidationRequiresTrimmedDistinctTextWithinLimit() {
    assertTrue(canRenameWorkoutName("Tempo run", "Easy run"))
    assertTrue(canRenameWorkoutName("  Tempo run  ", "Easy run"))
    assertFalse(canRenameWorkoutName("  ", "Easy run"))
    assertFalse(canRenameWorkoutName("Easy run", "Easy run"))
    assertFalse(canRenameWorkoutName("x".repeat(161), "Easy run"))
  }

  @Test
  fun formatsMonthAndDateLabelsForTheSelectedAppLocale() {
    val state =
        planCalendarState(
            plan, "2026-10", "2026-10-20", 1_792_449_000_000L, Locale.ENGLISH)

    assertEquals("October 2026", state.monthLabel)
    assertEquals("Tuesday 20 October 2026", state.selectedDateLabel)
  }

  @Test
  fun keepsTodayVisibleSeparatelyFromTheSelectedDate() {
    val selectedToday = calendarState(plan, "2026-10", "2026-10-20", 1_792_449_000_000L).days[22]

    assertEquals(CalendarDateEmphasis.TodayAndSelected, calendarDateEmphasis(selectedToday))
    assertEquals(CalendarDateEmphasis.Today, calendarDateEmphasis(selectedToday.copy(isSelected = false)))
    assertEquals(CalendarDateEmphasis.Selected, calendarDateEmphasis(selectedToday.copy(isToday = false)))
    assertEquals(
        CalendarDateEmphasis.Default,
        calendarDateEmphasis(selectedToday.copy(isToday = false, isSelected = false)))
  }

  @Test
  fun weekViewUsesMondayFirstSelectedDateRangeAndKeepsScheduledEntries() {
    val state =
        planCalendarState(
            plan,
            "2026-10",
            "2026-10-21",
            1_792_449_000_000L,
            Locale.ENGLISH,
            viewMode = PlanCalendarViewMode.Week)

    assertEquals(PlanCalendarViewMode.Week, state.viewMode)
    assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25"), state.days.map { it.date })
    assertEquals("October 19–25, 2026", state.periodLabel)
    assertEquals("october-rest", (state.selectedEntries.single() as PlanCalendarEntry.Rest).sessionId)
  }

  @Test
  fun dayViewContainsOnlySelectedPlanLocalDate() {
    val state =
        planCalendarState(
            plan,
            "2026-10",
            "2026-10-20",
            1_792_449_000_000L,
            Locale.ENGLISH,
            viewMode = PlanCalendarViewMode.Day)

    assertEquals(listOf("2026-10-20"), state.days.map { it.date })
    assertEquals("Tuesday 20 October 2026", state.periodLabel)
    assertEquals("october-run", (state.selectedEntries.single() as PlanCalendarEntry.Workout).sessionId)
    assertTrue(state.days.single().isToday)
  }

  @Test
  fun shiftsSelectedDateByCalendarDayWeekAndMonthAcrossBoundaries() {
    assertEquals("2027-01-01", shiftPlanCalendarDate("2026-12-31", PlanCalendarViewMode.Day, 1, "Europe/Stockholm"))
    assertEquals("2027-01-04", shiftPlanCalendarDate("2026-12-28", PlanCalendarViewMode.Week, 1, "Europe/Stockholm"))
    assertEquals("2026-02-28", shiftPlanCalendarDate("2026-01-31", PlanCalendarViewMode.Month, 1, "Europe/Stockholm"))
    assertEquals("2026-01-24", shiftPlanCalendarDate("2026-01-31", PlanCalendarViewMode.Week, -1, "Europe/Stockholm"))
    assertEquals("2026-03-29", shiftPlanCalendarDate("2026-03-28", PlanCalendarViewMode.Day, 1, "Europe/Stockholm"))
  }

  @Test
  fun weekLabelIncludesBothYearsWhenTheWeekCrossesNewYear() {
    val state =
        planCalendarState(
            plan,
            "2027-01",
            "2027-01-01",
            1_792_449_000_000L,
            Locale.ENGLISH,
            viewMode = PlanCalendarViewMode.Week)

    assertEquals("December 28, 2026 – January 3, 2027", state.periodLabel)
    assertEquals("2026-12-28", state.days.first().date)
    assertEquals("2027-01-03", state.days.last().date)
  }

  @Test
  fun weekLabelsUseSwedishDateOrderAndIncludeBothMonths() {
    val originalTimeZone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
      val swedishWeek =
          planCalendarState(
              plan,
              "2026-10",
              "2026-10-21",
              1_792_449_000_000L,
              Locale.forLanguageTag("sv-SE"),
              viewMode = PlanCalendarViewMode.Week)
      val crossMonthWeek =
          planCalendarState(
              plan,
              "2026-11",
              "2026-10-28",
              1_792_449_000_000L,
              Locale.ENGLISH,
              viewMode = PlanCalendarViewMode.Week)

      assertEquals("19–25 oktober 2026", swedishWeek.periodLabel)
      assertEquals("October 26 – November 1, 2026", crossMonthWeek.periodLabel)
    } finally {
      TimeZone.setDefault(originalTimeZone)
    }
  }

  private fun calendarState(
      plan: RelvenPlanImporter.Plan,
      month: String,
      selectedDate: String,
      nowMillis: Long
  ) = planCalendarState(plan, month, selectedDate, nowMillis, Locale.forLanguageTag("sv-SE"))
}
