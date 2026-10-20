package org.runnerup.relven.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.runnerup.workout.RelvenPlanImporter

internal data class PlanCalendarState(
    val month: String,
    val monthLabel: String,
    val planName: String,
    val selectedDate: String,
    val selectedDateLabel: String,
    val todayDate: String,
    val days: List<PlanCalendarDay>,
    val selectedEntries: List<PlanCalendarEntry>)

internal sealed interface ActivePlanLoadState {
  data object Loading : ActivePlanLoadState

  data object NoActivePlan : ActivePlanLoadState

  data object Unavailable : ActivePlanLoadState

  data class Loaded(val plan: RelvenPlanImporter.Plan) : ActivePlanLoadState
}

internal data class PlanCalendarDay(
    val date: String,
    val dateLabel: String,
    val dayNumber: Int,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val entries: List<PlanCalendarEntry>)

internal enum class CalendarDateEmphasis {
  Default,
  Today,
  Selected,
  TodayAndSelected
}

internal fun calendarDateEmphasis(day: PlanCalendarDay): CalendarDateEmphasis =
    when {
      day.isToday && day.isSelected -> CalendarDateEmphasis.TodayAndSelected
      day.isToday -> CalendarDateEmphasis.Today
      day.isSelected -> CalendarDateEmphasis.Selected
      else -> CalendarDateEmphasis.Default
    }

internal sealed interface PlanCalendarEntry {
  data class Workout(
      val sessionId: String,
      val workoutId: String,
      val workoutName: String,
      val notes: String) :
      PlanCalendarEntry

  data class Rest(val sessionId: String, val notes: String) : PlanCalendarEntry
}

internal fun canAddWorkoutOnDate(entries: List<PlanCalendarEntry>): Boolean =
    entries.none { it is PlanCalendarEntry.Rest }

internal fun canAddRestOnDate(entries: List<PlanCalendarEntry>): Boolean = entries.isEmpty()

internal fun canRemovePlanEntry(totalEntries: Int): Boolean = totalEntries > 1

internal sealed interface PlanRevisionStatus {
  data object Idle : PlanRevisionStatus

  data object Saving : PlanRevisionStatus

  data object Saved : PlanRevisionStatus

  data object Conflict : PlanRevisionStatus

  data object Failed : PlanRevisionStatus
}

internal fun planCalendarState(
    plan: RelvenPlanImporter.Plan,
    month: String,
    selectedDate: String,
    nowMillis: Long,
    locale: Locale = Locale.getDefault()
): PlanCalendarState {
  val timezone = TimeZone.getTimeZone(plan.timezone())
  val displayedMonth = parseCalendarDate("$month-01", timezone)
  val selected = parseCalendarDate(selectedDate, timezone)
  val today = calendarAt(nowMillis, timezone)
  val todayDate = formatDate(today)
  val firstDay = (displayedMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7
  val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
  val cellCount = ((firstDay + daysInMonth + 6) / 7) * 7
  val sessionsByDate = plan.days().groupBy(RelvenPlanImporter.Session::date)
  val dateLabelFormatter = SimpleDateFormat("EEEE d MMMM yyyy", locale).apply {
    timeZone = timezone
  }
  val days = ArrayList<PlanCalendarDay>(cellCount)
  val cursor = displayedMonth.clone() as Calendar
  cursor.add(Calendar.DAY_OF_MONTH, -firstDay)

  repeat(cellCount) {
    val date = formatDate(cursor)
    val entries = sessionsByDate[date].orEmpty().mapNotNull { sessionEntry(it, plan) }
    days.add(
        PlanCalendarDay(
            date = date,
            dateLabel = dateLabelFormatter.format(cursor.time).replaceFirstChar { it.titlecase(locale) },
            dayNumber = cursor.get(Calendar.DAY_OF_MONTH),
            inDisplayedMonth =
                cursor.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH) &&
                    cursor.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR),
            isToday = date == todayDate,
            isSelected = date == formatDate(selected),
            entries = entries))
    cursor.add(Calendar.DAY_OF_MONTH, 1)
  }

  val selectedEntries =
      sessionsByDate[formatDate(selected)].orEmpty().mapNotNull { sessionEntry(it, plan) }

  val monthFormatter = SimpleDateFormat("MMMM yyyy", locale)
  monthFormatter.timeZone = timezone
  val formattedMonth =
      monthFormatter.format(displayedMonth.time).replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(locale) else first.toString()
      }

  return PlanCalendarState(
      month = month,
      monthLabel = formattedMonth,
      planName = plan.name(),
      selectedDate = formatDate(selected),
      selectedDateLabel =
          dateLabelFormatter.format(selected.time).replaceFirstChar { it.titlecase(locale) },
      todayDate = todayDate,
      days = days,
      selectedEntries = selectedEntries)
}

private fun sessionEntry(
    session: RelvenPlanImporter.Session,
    plan: RelvenPlanImporter.Plan
): PlanCalendarEntry? =
    when (session.kind()) {
      "workout" -> {
        val workoutId = session.workoutId()
        plan.workoutNames()[workoutId]?.let {
          PlanCalendarEntry.Workout(session.id(), workoutId, it, session.notes())
        }
      }
      "rest" -> PlanCalendarEntry.Rest(session.id(), session.notes())
      else -> null
    }

internal fun shiftPlanCalendarMonth(
    month: String,
    selectedDate: String,
    monthDelta: Int,
    timezone: String
): Pair<String, String> {
  val zone = TimeZone.getTimeZone(timezone)
  val selected = parseCalendarDate(selectedDate, zone)
  val selectedDay = selected.get(Calendar.DAY_OF_MONTH)
  val destination = parseCalendarDate("$month-01", zone)
  destination.add(Calendar.MONTH, monthDelta)
  val destinationMonth = formatMonth(destination)
  destination.set(Calendar.DAY_OF_MONTH, minOf(selectedDay, destination.getActualMaximum(Calendar.DAY_OF_MONTH)))
  return destinationMonth to formatDate(destination)
}

internal fun planLocalDate(timezone: String, timeMillis: Long): String =
    formatDate(calendarAt(timeMillis, TimeZone.getTimeZone(timezone)))

private fun calendarAt(timeMillis: Long, timezone: TimeZone): Calendar =
    GregorianCalendar(timezone, Locale.ROOT).apply { timeInMillis = timeMillis }

private fun parseCalendarDate(value: String, timezone: TimeZone): Calendar {
  val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
    isLenient = false
    timeZone = timezone
  }
  val date = formatter.parse(value) ?: throw IllegalArgumentException("Invalid calendar date")
  if (formatter.format(date) != value) throw IllegalArgumentException("Invalid calendar date")
  return calendarAt(date.time, timezone)
}

private fun formatDate(calendar: Calendar): String =
    String.format(
        Locale.ROOT,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH))

private fun formatMonth(calendar: Calendar): String =
    String.format(Locale.ROOT, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
