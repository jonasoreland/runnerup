package org.runnerup.relven.ui

import java.time.YearMonth

internal fun emptyPlanCalendarDays(month: YearMonth): List<Int?> {
  val emptyCells = month.atDay(1).dayOfWeek.value - 1
  val cellCount = ((emptyCells + month.lengthOfMonth() + 6) / 7) * 7
  return List(cellCount) { index ->
    val day = index - emptyCells + 1
    day.takeIf { it in 1..month.lengthOfMonth() }
  }
}
