package org.runnerup.relven.ui

import android.database.sqlite.SQLiteDatabase
import org.runnerup.common.util.Constants.DB

internal data class HistoryActivity(
    val id: Long,
    val startedAtSeconds: Long,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val sport: Int,
    val unfinished: Boolean = false)

internal fun readHistoryActivities(database: SQLiteDatabase): List<HistoryActivity> {
  val columns =
      arrayOf(
          "_id",
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.SPORT)
  return database
      .query(
          DB.ACTIVITY.TABLE,
          columns,
          "${DB.ACTIVITY.DELETED} == 0",
          null,
          null,
          null,
          "${DB.ACTIVITY.START_TIME} desc")
      .use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow("_id")
        val startedColumn = cursor.getColumnIndexOrThrow(DB.ACTIVITY.START_TIME)
        val distanceColumn = cursor.getColumnIndexOrThrow(DB.ACTIVITY.DISTANCE)
        val durationColumn = cursor.getColumnIndexOrThrow(DB.ACTIVITY.TIME)
        val sportColumn = cursor.getColumnIndexOrThrow(DB.ACTIVITY.SPORT)
        buildList {
          while (cursor.moveToNext()) {
            val startedAt = cursor.getLong(startedColumn)
            if (startedAt <= 0L) continue
            val id = cursor.getLong(idColumn)
            val unfinished = cursor.isNull(durationColumn)
            val recovery =
                if (unfinished) readActivityRecoverySummary(database, id) else null
            val distance =
                if (cursor.isNull(distanceColumn)) recovery?.distanceMeters
                else cursor.getDouble(distanceColumn).takeIf { it.isFinite() && it > 0.0 }
            val duration =
                if (unfinished) recovery?.durationSeconds
                else cursor.getLong(durationColumn).takeIf { it >= 0L }
            add(
                HistoryActivity(
                    id = id,
                    startedAtSeconds = startedAt,
                    durationSeconds = duration,
                    distanceMeters = distance,
                    sport = cursor.getInt(sportColumn),
                    unfinished = unfinished))
          }
        }
      }
}

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
