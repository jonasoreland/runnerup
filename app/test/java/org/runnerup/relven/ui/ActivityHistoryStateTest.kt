package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.runnerup.common.util.Constants

class ActivityHistoryStateTest {
  private val activities =
      listOf(
          HistoryActivity(1L, 100L, 900L, 5_000.0, Constants.DB.ACTIVITY.SPORT_RUNNING),
          HistoryActivity(2L, 90L, 1_200L, 10_000.0, Constants.DB.ACTIVITY.SPORT_BIKING),
          HistoryActivity(3L, 80L, 1_800L, null, Constants.DB.ACTIVITY.SPORT_GYM),
          HistoryActivity(4L, 70L, 600L, 2_000.0, Constants.DB.ACTIVITY.SPORT_TREADMILL),
          HistoryActivity(5L, 60L, 600L, null, Constants.DB.ACTIVITY.SPORT_WALKING),
          HistoryActivity(6L, 50L, null, null, Int.MIN_VALUE))

  @Test
  fun filteringKeepsNewestOrderAndGroupsRelatedSports() {
    assertEquals(listOf(1L, 4L), filteredHistory(activities, HistorySportFilter.RUNNING).map { it.id })
    assertEquals(listOf(2L), filteredHistory(activities, HistorySportFilter.CYCLING).map { it.id })
    assertEquals(listOf(3L), filteredHistory(activities, HistorySportFilter.STRENGTH).map { it.id })
    assertEquals(listOf(5L), filteredHistory(activities, HistorySportFilter.WALKING).map { it.id })
  }

  @Test
  fun filterCountsUseFullVisibleHistory() {
    assertEquals(6, historyFilterCount(activities, HistorySportFilter.ALL))
    assertEquals(2, historyFilterCount(activities, HistorySportFilter.RUNNING))
    assertEquals(1, historyFilterCount(activities, HistorySportFilter.STRENGTH))
    assertEquals(1, historyFilterCount(activities, HistorySportFilter.OTHER))
    assertEquals(null, activities[2].distanceMeters)
  }

  @Test
  fun aggregateCountsIncludeEmptyCategoriesAndUnknownSports() {
    assertEquals(
        mapOf(
            HistorySportFilter.ALL to 6,
            HistorySportFilter.RUNNING to 2,
            HistorySportFilter.CYCLING to 1,
            HistorySportFilter.WALKING to 1,
            HistorySportFilter.STRENGTH to 1,
            HistorySportFilter.OTHER to 1),
        historyFilterCounts(activities))
  }

  @Test
  fun allFilterReturnsOriginalActivityOrder() {
    assertEquals(activities, filteredHistory(activities, HistorySportFilter.ALL))
  }
}
