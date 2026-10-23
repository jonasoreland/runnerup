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
          ${DB.ACTIVITY.AVG_CADENCE} REAL,
          ${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent())
    database.execSQL(
        """
        CREATE TABLE ${DB.LAP.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LAP.ACTIVITY} INTEGER NOT NULL,
          ${DB.LAP.LAP} INTEGER NOT NULL,
          ${DB.LAP.INTENSITY} INTEGER NOT NULL DEFAULT 0,
          ${DB.LAP.TIME} INTEGER,
          ${DB.LAP.DISTANCE} REAL,
          ${DB.LAP.PLANNED_TIME} INTEGER,
          ${DB.LAP.PLANNED_DISTANCE} REAL,
          ${DB.LAP.PLANNED_PACE} REAL,
          ${DB.LAP.AVG_HR} INTEGER
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
          put(DB.ACTIVITY.AVG_CADENCE, 174.5)
        }
    database.insertOrThrow(DB.ACTIVITY.TABLE, null, saved)
    val deleted = ContentValues(saved).apply { put("_id", 8L); put(DB.ACTIVITY.DELETED, 1) }
    database.insertOrThrow(DB.ACTIVITY.TABLE, null, deleted)
    database.insertOrThrow(
        DB.LAP.TABLE,
        null,
        ContentValues().apply {
          put(DB.LAP.ACTIVITY, 7L)
          put(DB.LAP.LAP, 1)
          put(DB.LAP.INTENSITY, 0)
          put(DB.LAP.TIME, 360L)
          put(DB.LAP.DISTANCE, 1_000.0)
          put(DB.LAP.PLANNED_TIME, 360L)
          put(DB.LAP.PLANNED_DISTANCE, 1_000.0)
          put(DB.LAP.PLANNED_PACE, 5.5)
          put(DB.LAP.AVG_HR, 142)
        })
    database.insertOrThrow(
        DB.LAP.TABLE,
        null,
        ContentValues().apply {
          put(DB.LAP.ACTIVITY, 8L)
          put(DB.LAP.LAP, 1)
          put(DB.LAP.TIME, 120L)
          put(DB.LAP.DISTANCE, 500.0)
        })

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
              maximumHeartRate = 171,
              averageCadence = 174.5,
              laps =
                  listOf(
                      ActivityLapDetails(
                          2,
                          0,
                          360L,
                          1_000.0,
                          142,
                          plannedDurationSeconds = 360L,
                          plannedDistanceMeters = 1_000.0,
                          plannedPace = 5.5))),
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

  @Test
  fun reconstructsSummaryForAnInterruptedGpsWorkoutFromItsPersistedCheckpoint() {
    val database = interruptedActivityDatabase()

    try {
      val activity = readHistoryActivityDetails(database, 31L)

      assertEquals(18L, activity?.durationSeconds)
      assertEquals(131.6, activity?.distanceMeters!!, 0.001)
      assertTrue(activity.unfinished)
      assertEquals(2, activity.route.size)
    } finally {
      database.close()
    }
  }

  @Test
  fun rebuildsRouteInInsertionOrderUsingPersistedMonotonicElapsedAndDistance() {
    val database = interruptedActivityDatabase()
    database.insertOrThrow(
        DB.LOCATION.TABLE,
        null,
        ContentValues().apply {
          put(DB.LOCATION.ACTIVITY, 31L)
          put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS)
          put(DB.LOCATION.LATITUDE, 59.3295)
          put(DB.LOCATION.LONGITUDE, 18.0688)
          // Wall clock moved backwards; the monotonic Tracker checkpoint did not.
          put(DB.LOCATION.TIME, 1_700_000_000_000L)
          put(DB.LOCATION.ELAPSED, 20_000.0)
          put(DB.LOCATION.DISTANCE, 150.0)
        })

    try {
      val route = readActivityRoute(database, 31L)

      assertEquals(59.3293, route[0].latitude, 0.0000001)
      assertEquals(59.3294, route[1].latitude, 0.0000001)
      assertEquals(59.3295, route[2].latitude, 0.0000001)
      assertEquals(listOf(0.0, 18.179, 20.0), route.map(RoutePoint::elapsedSeconds))
      assertEquals(listOf(0.0, 131.6, 150.0), route.map(RoutePoint::distanceMeters))
      assertEquals(ActivityRecoverySummary(20L, 150.0), readActivityRecoverySummary(database, 31L))
    } finally {
      database.close()
    }
  }

  @Test
  fun fallsBackToWallTimeAndGeometryForLegacyLocationsWithoutTrackerCheckpoints() {
    val database = interruptedActivityDatabase()
    database.execSQL(
        "UPDATE ${DB.LOCATION.TABLE} SET ${DB.LOCATION.ELAPSED} = NULL, ${DB.LOCATION.DISTANCE} = NULL")

    try {
      val route = readActivityRoute(database, 31L)

      assertEquals(2, route.size)
      assertEquals(0.0, route.first().elapsedSeconds, 0.0)
      assertEquals(0.001, route.last().elapsedSeconds, 0.000001)
      assertTrue(route.last().distanceMeters > route.first().distanceMeters)
    } finally {
      database.close()
    }
  }

  @Test
  fun savingInterruptedActivityWritesItsLastCheckpointAndCannotFinalizeTwice() {
    val database = interruptedActivityDatabase()
    try {
      assertTrue(saveInterruptedActivity(database, 31L, "Cut short by app restart"))
      assertFalse(saveInterruptedActivity(database, 31L, "Overwritten note"))

      database
          .query(
              DB.ACTIVITY.TABLE,
              arrayOf(DB.ACTIVITY.TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.COMMENT),
              "_id = ?",
              arrayOf("31"),
              null,
              null,
              null)
          .use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(18L, cursor.getLong(0))
            assertEquals(131.6, cursor.getDouble(1), 0.001)
            assertEquals("Cut short by app restart", cursor.getString(2))
          }
      assertFalse(readHistoryActivityDetails(database, 31L)!!.unfinished)
    } finally {
      database.close()
    }
  }

  @Test
  fun discardingInterruptedActivitySoftDeletesItAndCannotDeleteTwice() {
    val database = interruptedActivityDatabase()
    try {
      assertTrue(discardInterruptedActivity(database, 31L))
      assertFalse(discardInterruptedActivity(database, 31L))
      assertNull(readHistoryActivityDetails(database, 31L))
    } finally {
      database.close()
    }
  }

  @Test
  fun ignoresInvalidLapMeasurementsAndKeepsLapRowsOrdered() {
    val database = SQLiteDatabase.create(null)
    database.execSQL(
        """
        CREATE TABLE ${DB.LAP.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LAP.ACTIVITY} INTEGER NOT NULL,
          ${DB.LAP.LAP} INTEGER NOT NULL,
          ${DB.LAP.INTENSITY} INTEGER NOT NULL DEFAULT 0,
          ${DB.LAP.TIME} INTEGER,
          ${DB.LAP.DISTANCE} REAL,
          ${DB.LAP.PLANNED_TIME} INTEGER,
          ${DB.LAP.PLANNED_DISTANCE} REAL,
          ${DB.LAP.PLANNED_PACE} REAL,
          ${DB.LAP.AVG_HR} INTEGER
        )
        """.trimIndent())
    database.insertOrThrow(
        DB.LAP.TABLE,
        null,
        ContentValues().apply {
          put(DB.LAP.ACTIVITY, 11L)
          put(DB.LAP.LAP, 2)
          put(DB.LAP.INTENSITY, 0)
          put(DB.LAP.TIME, -4L)
          put(DB.LAP.DISTANCE, Double.NaN)
          put(DB.LAP.AVG_HR, 0)
        })
    database.insertOrThrow(
        DB.LAP.TABLE,
        null,
        ContentValues().apply {
          put(DB.LAP.ACTIVITY, 11L)
          put(DB.LAP.LAP, 1)
          put(DB.LAP.INTENSITY, 2)
          put(DB.LAP.TIME, 300L)
          put(DB.LAP.DISTANCE, 800.0)
          put(DB.LAP.PLANNED_TIME, 300L)
          put(DB.LAP.PLANNED_DISTANCE, 800.0)
          put(DB.LAP.PLANNED_PACE, 4.5)
          put(DB.LAP.AVG_HR, 150)
        })
    database.insertOrThrow(
        DB.LAP.TABLE,
        null,
        ContentValues().apply {
          put(DB.LAP.ACTIVITY, 12L)
          put(DB.LAP.LAP, 1)
          put(DB.LAP.INTENSITY, 0)
          put(DB.LAP.TIME, 100L)
          put(DB.LAP.DISTANCE, 400.0)
        })

    try {
      assertEquals(
          listOf(
              ActivityLapDetails(
                  2,
                  2,
                  300L,
                  800.0,
                  150,
                  plannedDurationSeconds = 300L,
                  plannedDistanceMeters = 800.0,
                  plannedPace = 4.5),
              ActivityLapDetails(3, 0, null, null, null)),
          readActivityLaps(database, 11L))
    } finally {
      database.close()
    }
  }

  private fun interruptedActivityDatabase(): SQLiteDatabase {
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
          ${DB.ACTIVITY.AVG_CADENCE} REAL,
          ${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent())
    database.execSQL(
        """
        CREATE TABLE ${DB.LAP.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LAP.ACTIVITY} INTEGER NOT NULL,
          ${DB.LAP.LAP} INTEGER NOT NULL,
          ${DB.LAP.INTENSITY} INTEGER NOT NULL DEFAULT 0,
          ${DB.LAP.TIME} INTEGER,
          ${DB.LAP.DISTANCE} REAL,
          ${DB.LAP.PLANNED_TIME} INTEGER,
          ${DB.LAP.PLANNED_DISTANCE} REAL,
          ${DB.LAP.PLANNED_PACE} REAL,
          ${DB.LAP.AVG_HR} INTEGER
        )
        """.trimIndent())
    database.execSQL(
        """
        CREATE TABLE ${DB.LOCATION.TABLE} (
          _id INTEGER PRIMARY KEY,
          ${DB.LOCATION.ACTIVITY} INTEGER NOT NULL,
          ${DB.LOCATION.TYPE} INTEGER NOT NULL,
          ${DB.LOCATION.LATITUDE} REAL NOT NULL,
          ${DB.LOCATION.LONGITUDE} REAL NOT NULL,
          ${DB.LOCATION.ALTITUDE} REAL,
          ${DB.LOCATION.TIME} INTEGER NOT NULL,
          ${DB.LOCATION.LAP} INTEGER,
          ${DB.LOCATION.HR} INTEGER,
          ${DB.LOCATION.SPEED} REAL,
          ${DB.LOCATION.CADENCE} REAL,
          ${DB.LOCATION.ELAPSED} REAL,
          ${DB.LOCATION.DISTANCE} REAL
        )
        """.trimIndent())
    database.insertOrThrow(
        DB.ACTIVITY.TABLE,
        null,
        ContentValues().apply {
          put("_id", 31L)
          put(DB.ACTIVITY.START_TIME, 1_800_000_000L)
          put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
        })
    listOf(Triple(1L, 0.0, 0.0), Triple(2L, 18_179.0, 131.6))
        .forEachIndexed { index, (time, elapsed, distance) ->
          database.insertOrThrow(
              DB.LOCATION.TABLE,
              null,
              ContentValues().apply {
                put(DB.LOCATION.ACTIVITY, 31L)
                put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS)
                put(DB.LOCATION.LATITUDE, 59.3293 + index * 0.0001)
                put(DB.LOCATION.LONGITUDE, 18.0686 + index * 0.0001)
                put(DB.LOCATION.TIME, 1_800_000_000_000L + time)
                put(DB.LOCATION.LAP, 0)
                put(DB.LOCATION.ELAPSED, elapsed)
                put(DB.LOCATION.DISTANCE, distance)
              })
        }
    return database
  }
}
