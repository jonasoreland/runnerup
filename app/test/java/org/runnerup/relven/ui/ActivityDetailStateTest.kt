package org.runnerup.relven.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.runnerup.common.util.Constants.DB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ActivityDetailStateTest {
  @Test
  fun offersRouteOnlyForGpsSportWithAtLeastTwoGpsSamplesAndMapSupport() {
    val activity =
        HistoryActivityDetails(
            id = 7L,
            startedAtSeconds = 1_800_000_000L,
            durationSeconds = null,
            distanceMeters = null,
            sport = DB.ACTIVITY.SPORT_RUNNING,
            gpsSampleCount = 2,
            comment = null,
            averageHeartRate = null,
            maximumHeartRate = null)

    assertTrue(activityHasMapRoute(activity, mapEnabled = true))
    assertFalse(activityHasMapRoute(activity.copy(gpsSampleCount = 1), mapEnabled = true))
    assertFalse(activityHasMapRoute(activity.copy(gpsSampleCount = 0), mapEnabled = true))
    assertFalse(activityHasMapRoute(activity.copy(sport = DB.ACTIVITY.SPORT_GYM), mapEnabled = true))
    assertFalse(activityHasMapRoute(activity, mapEnabled = false))
  }

  @Test
  fun readsOneSavedActivityAndSkipsDeletedRows() {
    val database = SQLiteDatabase.create(null)
    database.execSQL(
        """
        CREATE TABLE ${DB.ACTIVITY.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.ACTIVITY.START_TIME} INTEGER NOT NULL,
          ${DB.ACTIVITY.DISTANCE} REAL,
          ${DB.ACTIVITY.TIME} INTEGER,
          ${DB.ACTIVITY.SPORT} INTEGER,
          ${DB.ACTIVITY.COMMENT} TEXT,
          ${DB.ACTIVITY.AVG_HR} INTEGER,
          ${DB.ACTIVITY.MAX_HR} INTEGER,
          ${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent())
    database.execSQL(
        """
        CREATE TABLE ${DB.LOCATION.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LOCATION.ACTIVITY} INTEGER NOT NULL,
          ${DB.LOCATION.TYPE} INTEGER NOT NULL
        )
        """.trimIndent())
    val saved =
        ContentValues().apply {
          put("_id", 7L)
          put(DB.ACTIVITY.START_TIME, 1_800_000_000L)
          put(DB.ACTIVITY.DISTANCE, 5_000.0)
          put(DB.ACTIVITY.TIME, 1_800L)
          put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
          put(DB.ACTIVITY.COMMENT, "Morning run")
          put(DB.ACTIVITY.AVG_HR, 145)
          put(DB.ACTIVITY.MAX_HR, 171)
        }
    database.insertOrThrow(DB.ACTIVITY.TABLE, null, saved)
    val deleted = ContentValues(saved).apply { put("_id", 8L); put(DB.ACTIVITY.DELETED, 1) }
    database.insertOrThrow(DB.ACTIVITY.TABLE, null, deleted)

    try {
      assertEquals(
          HistoryActivityDetails(
              id = 7L,
              startedAtSeconds = 1_800_000_000L,
              durationSeconds = 1_800L,
              distanceMeters = 5_000.0,
              sport = DB.ACTIVITY.SPORT_RUNNING,
              gpsSampleCount = 0,
              comment = "Morning run",
              averageHeartRate = 145,
              maximumHeartRate = 171),
          readHistoryActivityDetails(database, 7L))
      assertNull(readHistoryActivityDetails(database, 8L))
    } finally {
      database.close()
    }
  }

  @Test
  fun derivesAverageSpeedOnlyFromValidDistanceAndDuration() {
    val summary =
        HistoryActivityDetails(
            id = 7L,
            startedAtSeconds = 1_800_000_000L,
            durationSeconds = 1_800L,
            distanceMeters = 5_000.0,
            sport = 0,
            gpsSampleCount = 0,
            comment = null,
            averageHeartRate = 145,
            maximumHeartRate = 171)

    assertEquals(
        ActivityDetailMeasurements(
            distanceMeters = 5_000.0,
            durationSeconds = 1_800L,
            averageSpeedMetersPerSecond = 5_000.0 / 1_800.0,
            averageHeartRate = 145,
            maximumHeartRate = 171),
        activityDetailMeasurements(summary))
  }

  @Test
  fun omitsInvalidDistanceDurationAndHeartRateValues() {
    val summary =
        HistoryActivityDetails(
            id = 8L,
            startedAtSeconds = 1_800_000_000L,
            durationSeconds = 0L,
            distanceMeters = Double.NaN,
            sport = 0,
            gpsSampleCount = 0,
            comment = "  ",
            averageHeartRate = 0,
            maximumHeartRate = -1)

    val measurements = activityDetailMeasurements(summary)

    assertNull(measurements.distanceMeters)
    assertEquals(0L, measurements.durationSeconds)
    assertNull(measurements.averageSpeedMetersPerSecond)
    assertNull(measurements.averageHeartRate)
    assertNull(measurements.maximumHeartRate)
  }
}
