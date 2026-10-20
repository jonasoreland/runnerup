package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import org.runnerup.workout.RelvenPlanImporter

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
    assertEquals(
        listOf(PlanCalendarEntry.Workout("october-run", "run-a", "Easy run", "Keep it easy")),
        state.selectedEntries)
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

  private fun calendarState(
      plan: RelvenPlanImporter.Plan,
      month: String,
      selectedDate: String,
      nowMillis: Long
  ) = planCalendarState(plan, month, selectedDate, nowMillis, Locale.forLanguageTag("sv-SE"))
}
