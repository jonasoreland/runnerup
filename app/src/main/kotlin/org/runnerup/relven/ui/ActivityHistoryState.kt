package org.runnerup.relven.ui

import org.runnerup.common.util.Constants.DB

internal data class HistoryActivity(
    val id: Long,
    val startedAtSeconds: Long,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val sport: Int)

internal enum class HistorySportFilter {
  ALL,
  RUNNING,
  CYCLING,
  WALKING,
  STRENGTH,
  OTHER
}

internal fun filteredHistory(
    activities: List<HistoryActivity>, filter: HistorySportFilter): List<HistoryActivity> =
    activities.filter { historyFilterMatches(filter, it.sport) }

internal fun historyFilterCount(
    activities: List<HistoryActivity>, filter: HistorySportFilter): Int =
    historyFilterCounts(activities).getValue(filter)

internal fun historyFilterCounts(activities: List<HistoryActivity>): Map<HistorySportFilter, Int> {
  val counts = HistorySportFilter.entries.associateWith { 0 }.toMutableMap()
  activities.forEach { activity ->
    counts[HistorySportFilter.ALL] = counts.getValue(HistorySportFilter.ALL) + 1
    val category = historyCategory(activity.sport)
    counts[category] = counts.getValue(category) + 1
  }
  return counts
}

private fun historyFilterMatches(filter: HistorySportFilter, sport: Int): Boolean =
    filter == HistorySportFilter.ALL || historyCategory(sport) == filter

private fun historyCategory(sport: Int): HistorySportFilter =
    when (sport) {
      DB.ACTIVITY.SPORT_RUNNING,
      DB.ACTIVITY.SPORT_ORIENTEERING,
      DB.ACTIVITY.SPORT_TREADMILL -> HistorySportFilter.RUNNING
      DB.ACTIVITY.SPORT_BIKING,
      DB.ACTIVITY.SPORT_STATIONARY_BIKE -> HistorySportFilter.CYCLING
      DB.ACTIVITY.SPORT_WALKING -> HistorySportFilter.WALKING
      DB.ACTIVITY.SPORT_GYM -> HistorySportFilter.STRENGTH
      else -> HistorySportFilter.OTHER
    }
