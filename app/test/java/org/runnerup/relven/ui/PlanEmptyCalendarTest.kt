package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEmptyCalendarTest {
  @Test
  fun mondayFirstGridContainsEveryDateExactlyOnce() {
    val days = emptyPlanCalendarDays(PlanCalendarMonth(2026, 10))

    assertEquals(35, days.size)
    assertEquals(listOf(null, null, null, 1, 2, 3, 4), days.take(7))
    assertEquals(31, days.filterNotNull().last())
    assertEquals(null, days.last())
    assertEquals(31, days.filterNotNull().distinct().size)
    assertTrue(days.filterNotNull().all { it in 1..31 })
  }

  @Test
  fun emptyGridProvidesStableIsoDatesForSelection() {
    val dates = emptyPlanCalendarDateStrings(PlanCalendarMonth(2026, 2))

    assertEquals(35, dates.size)
    assertEquals(null, dates.first())
    assertEquals("2026-02-01", dates[6])
    assertEquals("2026-02-28", dates[33])
    assertEquals(null, dates[34])
    assertEquals(28, dates.filterNotNull().distinct().size)
  }
}
