package org.runnerup.relven.ui

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEmptyCalendarTest {
  @Test
  fun mondayFirstGridContainsEveryDateExactlyOnce() {
    val days = emptyPlanCalendarDays(YearMonth.of(2026, 10))

    assertEquals(35, days.size)
    assertEquals(listOf(null, null, null, 1, 2, 3, 4), days.take(7))
    assertEquals(31, days.filterNotNull().last())
    assertEquals(null, days.last())
    assertEquals(31, days.filterNotNull().distinct().size)
    assertTrue(days.filterNotNull().all { it in 1..31 })
  }
}
