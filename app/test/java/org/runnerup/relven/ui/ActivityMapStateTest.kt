package org.runnerup.relven.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.runnerup.common.util.Constants.DB
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ActivityMapStateTest {
  @Test
  fun mapTileAttributionReservesSpaceForTheMapLibreBranding() {
    assertEquals(224, routeMapAttributionMaxWidthPx(360, leftClearancePx = 120, marginPx = 8))
    assertEquals(104, routeMapAttributionMaxWidthPx(240, leftClearancePx = 120, marginPx = 8))
    assertEquals(0, routeMapAttributionMaxWidthPx(100, leftClearancePx = 120, marginPx = 8))
  }

  @Test
  fun loadsMapOnlyWhenSavedActivityHasAtLeastTwoGpsSamples() {
    val database = databaseWithActivityAndLocations()
    insertActivity(database, id = 1L)
    insertGpsPoint(database, activityId = 1L, latitude = 59.3, longitude = 18.0)
    insertGpsPoint(database, activityId = 1L, latitude = 59.301, longitude = 18.001)

    try {
      val state = readActivityMapState(database, activityId = 1L, mapEnabled = true)
      assertTrue(state is ActivityMapLoadState.Loaded)
      assertEquals(1L, (state as ActivityMapLoadState.Loaded).activity.id)
      assertEquals(2, state.route.size)
      assertEquals(59.3, state.route.first().latitude, 0.00001)
    } finally {
      database.close()
    }
  }

  @Test
  fun reportsNoRouteForMissingOrSingleGpsSample() {
    val database = databaseWithActivityAndLocations()
    insertActivity(database, id = 2L)
    insertActivity(database, id = 3L)
    insertGpsPoint(database, activityId = 2L, latitude = 59.3, longitude = 18.0)

    try {
      assertEquals(
          ActivityMapLoadState.RouteUnavailable,
          readActivityMapState(database, activityId = 2L, mapEnabled = true))
      assertEquals(
          ActivityMapLoadState.RouteUnavailable,
          readActivityMapState(database, activityId = 3L, mapEnabled = true))
    } finally {
      database.close()
    }
  }

  @Test
  fun reportsNoRouteForNonGpsSportEvenWhenGpsRowsExist() {
    val database = databaseWithActivityAndLocations()
    insertActivity(database, id = 6L, sport = DB.ACTIVITY.SPORT_GYM)
    insertGpsPoint(database, activityId = 6L, latitude = 59.3, longitude = 18.0)
    insertGpsPoint(database, activityId = 6L, latitude = 59.301, longitude = 18.001)

    try {
      assertEquals(
          ActivityMapLoadState.RouteUnavailable,
          readActivityMapState(database, activityId = 6L, mapEnabled = true))
    } finally {
      database.close()
    }
  }

  @Test
  fun distinguishesUnsupportedMapAndMissingActivityStates() {
    val database = databaseWithActivityAndLocations()
    insertActivity(database, id = 4L)

    try {
      assertEquals(
          ActivityMapLoadState.Unavailable,
          readActivityMapState(database, activityId = 4L, mapEnabled = false))
      assertEquals(
          ActivityMapLoadState.NotFound,
          readActivityMapState(database, activityId = 5L, mapEnabled = true))
    } finally {
      database.close()
    }
  }

  private fun databaseWithActivityAndLocations(): SQLiteDatabase =
      SQLiteDatabase.create(null).apply {
        execSQL(
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
        execSQL(
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
              ${DB.LOCATION.SPEED} REAL
            )
            """.trimIndent())
        execSQL(
            """
            CREATE TABLE ${DB.LAP.TABLE} (
              _id INTEGER PRIMARY KEY,
              ${DB.LAP.ACTIVITY} INTEGER NOT NULL,
              ${DB.LAP.LAP} INTEGER NOT NULL,
              ${DB.LAP.INTENSITY} INTEGER NOT NULL DEFAULT 0,
              ${DB.LAP.TIME} INTEGER,
              ${DB.LAP.DISTANCE} REAL,
              ${DB.LAP.AVG_HR} INTEGER
            )
            """.trimIndent())
      }

  private fun insertActivity(
      database: SQLiteDatabase,
      id: Long,
      sport: Int = DB.ACTIVITY.SPORT_RUNNING) {
    database.insertOrThrow(
        DB.ACTIVITY.TABLE,
        null,
        ContentValues().apply {
          put("_id", id)
          put(DB.ACTIVITY.START_TIME, 1_800_000_000L)
          put(DB.ACTIVITY.SPORT, sport)
        })
  }

  private fun insertGpsPoint(
      database: SQLiteDatabase,
      activityId: Long,
      latitude: Double,
      longitude: Double) {
    val pointNumber =
        database
            .query(
                DB.LOCATION.TABLE,
                arrayOf("_id"),
                "${DB.LOCATION.ACTIVITY} = ?",
                arrayOf(activityId.toString()),
                null,
                null,
                null)
            .use { it.count }
    database.insertOrThrow(
        DB.LOCATION.TABLE,
        null,
        ContentValues().apply {
          put(DB.LOCATION.ACTIVITY, activityId)
          put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS)
          put(DB.LOCATION.LATITUDE, latitude)
          put(DB.LOCATION.LONGITUDE, longitude)
          put(DB.LOCATION.TIME, pointNumber * 60_000L)
          put(DB.LOCATION.LAP, 1)
        })
  }
}
