package org.runnerup.relven.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
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

  @Test
  fun historyRestoresCheckpointMetricsAndMarksAnInterruptedWorkout() {
    val database = SQLiteDatabase.create(null)
    database.execSQL(
        """
        CREATE TABLE ${DB.ACTIVITY.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.ACTIVITY.START_TIME} INTEGER NOT NULL,
          ${DB.ACTIVITY.DISTANCE} REAL,
          ${DB.ACTIVITY.TIME} INTEGER,
          ${DB.ACTIVITY.SPORT} INTEGER,
          ${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent())
    database.execSQL(
        """
        CREATE TABLE ${DB.LOCATION.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LOCATION.ACTIVITY} INTEGER NOT NULL,
          ${DB.LOCATION.TIME} INTEGER NOT NULL,
          ${DB.LOCATION.ELAPSED} REAL,
          ${DB.LOCATION.DISTANCE} REAL
        )
        """.trimIndent())
    database.insertOrThrow(
        DB.ACTIVITY.TABLE,
        null,
        ContentValues().apply {
          put("_id", 7L)
          put(DB.ACTIVITY.START_TIME, 1_800_000_000L)
          put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
        })
    database.insertOrThrow(
        DB.ACTIVITY.TABLE,
        null,
        ContentValues().apply {
          put("_id", 8L)
          put(DB.ACTIVITY.START_TIME, 1_700_000_000L)
          put(DB.ACTIVITY.DISTANCE, 300.0)
          put(DB.ACTIVITY.TIME, 60L)
          put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
        })
    database.insertOrThrow(
        DB.LOCATION.TABLE,
        null,
        ContentValues().apply {
          put(DB.LOCATION.ACTIVITY, 7L)
          put(DB.LOCATION.TIME, 1_800_000_000_000L)
          put(DB.LOCATION.ELAPSED, 18_179.0)
          put(DB.LOCATION.DISTANCE, 131.6)
        })

    try {
      val history = readHistoryActivities(database)

      assertEquals(2, history.size)
      assertEquals(18L, history[0].durationSeconds)
      assertEquals(131.6, history[0].distanceMeters!!, 0.001)
      assertTrue(history[0].unfinished)
      assertFalse(history[1].unfinished)
      assertTrue(filteredHistory(history, HistorySportFilter.RUNNING)[0].unfinished)
    } finally {
      database.close()
    }
  }
}
