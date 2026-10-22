package org.runnerup.relven.ui

import java.util.Calendar
import java.util.Locale

internal data class PlanCalendarMonth(val year: Int, val month: Int) {
  init {
    require(year > 0 && month in 1..12)
  }

  fun daysInMonth(): Int =
      Calendar.getInstance().apply {
        set(year, month - 1, 1)
      }.getActualMaximum(Calendar.DAY_OF_MONTH)

  fun mondayFirstOffset(): Int {
    val calendar = Calendar.getInstance().apply { set(year, month - 1, 1) }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
  }

  fun plusMonths(amount: Int): PlanCalendarMonth {
    val calendar = Calendar.getInstance().apply { set(year, month - 1, 1) }
    calendar.add(Calendar.MONTH, amount)
    return PlanCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
  }

  fun key(): String = "%04d-%02d".format(Locale.ROOT, year, month)

  fun label(locale: Locale): String {
    val calendar = Calendar.getInstance(locale).apply { set(year, month - 1, 1) }
    val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, locale).orEmpty()
    return "$monthName $year".replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
  }

  companion object {
    fun now(): PlanCalendarMonth {
      val calendar = Calendar.getInstance()
      return PlanCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
    }

    fun parse(value: String): PlanCalendarMonth {
      val parts = value.split('-')
      require(parts.size == 2)
      return PlanCalendarMonth(parts[0].toInt(), parts[1].toInt())
    }
  }
}

internal fun emptyPlanCalendarDays(month: PlanCalendarMonth): List<Int?> {
  val emptyCells = month.mondayFirstOffset()
  val daysInMonth = month.daysInMonth()
  val cellCount = ((emptyCells + daysInMonth + 6) / 7) * 7
  return List(cellCount) { index ->
    val day = index - emptyCells + 1
    day.takeIf { it in 1..daysInMonth }
  }
}

internal fun emptyPlanCalendarDateStrings(month: PlanCalendarMonth): List<String?> =
    emptyPlanCalendarDays(month).map { day ->
      day?.let { "%s-%02d".format(Locale.ROOT, month.key(), it) }
    }
