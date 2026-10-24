package org.runnerup.relven.health

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants.DB

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectActivitySyncTest {
  @Test
  fun readsSavedActivityWithChronologicalRouteAndTimestampedLap() {
    val database = database()
    val start = Instant.parse("2026-10-24T08:00:00Z")
    val activityId = insertActivity(database, start.epochSecond, deleted = false)
    insertLocation(database, activityId, DB.LOCATION.TYPE_GPS, start.plusSeconds(2), 59.1, 18.1, lap = 0)
    insertLocation(database, activityId, DB.LOCATION.TYPE_GPS, start.plusSeconds(1), 59.0, 18.0, lap = 0)
    insertLocation(database, activityId, DB.LOCATION.TYPE_END, start.plusSeconds(60), 0.0, 0.0, lap = 0)
    database.insert(DB.LAP.TABLE, null, ContentValues().apply {
      put(DB.LAP.ACTIVITY, activityId)
      put(DB.LAP.LAP, 0)
      put(DB.LAP.DISTANCE, 125.0)
    })

    val workout = readRecordedHealthConnectWorkout(
        ApplicationProvider.getApplicationContext(), database, activityId)!!

    assertEquals("activity-$activityId", workout.sessionId)
    assertEquals(HealthConnectActivity.Running, workout.activity)
    assertEquals("Morning run", workout.title)
    assertEquals(2, workout.route.size)
    assertEquals(start.plusSeconds(1), workout.route[0].time)
    assertEquals(start.plusSeconds(2), workout.route[1].time)
    assertEquals(1, workout.laps.size)
    assertEquals(start, workout.laps.single().startTime)
    assertEquals(start.plusSeconds(60), workout.laps.single().endTime)
    assertEquals(125.0, workout.laps.single().distanceMeters!!, 0.0)
    database.close()
  }

  @Test
  fun skipsDeletedAndUnfinishedActivities() {
    val database = database()
    val start = Instant.parse("2026-10-24T08:00:00Z")
    val deleted = insertActivity(database, start.epochSecond, deleted = true)
    val unfinished = insertActivity(database, start.epochSecond, deleted = false, duration = null)

    assertNull(readRecordedHealthConnectWorkout(ApplicationProvider.getApplicationContext(), database, deleted))
    assertNull(readRecordedHealthConnectWorkout(ApplicationProvider.getApplicationContext(), database, unfinished))
    database.close()
  }

  private fun database(): SQLiteDatabase = SQLiteDatabase.create(null).apply {
    execSQL("CREATE TABLE ${DB.ACTIVITY.TABLE} (_id INTEGER PRIMARY KEY, ${DB.ACTIVITY.START_TIME} INTEGER, ${DB.ACTIVITY.DISTANCE} REAL, ${DB.ACTIVITY.TIME} INTEGER, ${DB.ACTIVITY.NAME} TEXT, ${DB.ACTIVITY.COMMENT} TEXT, ${DB.ACTIVITY.SPORT} INTEGER, ${DB.ACTIVITY.DELETED} INTEGER)")
    execSQL("CREATE TABLE ${DB.LOCATION.TABLE} (_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LOCATION.ACTIVITY} INTEGER, ${DB.LOCATION.TYPE} INTEGER, ${DB.LOCATION.TIME} INTEGER, ${DB.LOCATION.LATITUDE} REAL, ${DB.LOCATION.LONGITUDE} REAL, ${DB.LOCATION.ALTITUDE} REAL, ${DB.LOCATION.HR} INTEGER, ${DB.LOCATION.SPEED} REAL, ${DB.LOCATION.CADENCE} REAL, ${DB.LOCATION.LAP} INTEGER)")
    execSQL("CREATE TABLE ${DB.LAP.TABLE} (_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LAP.ACTIVITY} INTEGER, ${DB.LAP.LAP} INTEGER, ${DB.LAP.DISTANCE} REAL)")
  }

  private fun insertActivity(database: SQLiteDatabase, startSeconds: Long, deleted: Boolean, duration: Long? = 60): Long =
      database.insert(DB.ACTIVITY.TABLE, null, ContentValues().apply {
        put(DB.ACTIVITY.START_TIME, startSeconds)
        duration?.let { put(DB.ACTIVITY.TIME, it) }
        put(DB.ACTIVITY.DISTANCE, 125.0)
        put(DB.ACTIVITY.NAME, "Morning run")
        put(DB.ACTIVITY.COMMENT, "Notes")
        put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING)
        put(DB.ACTIVITY.DELETED, if (deleted) 1 else 0)
      })

  private fun insertLocation(
      database: SQLiteDatabase, activityId: Long, type: Int, time: Instant,
      latitude: Double, longitude: Double, lap: Int) {
    database.insert(DB.LOCATION.TABLE, null, ContentValues().apply {
      put(DB.LOCATION.ACTIVITY, activityId)
      put(DB.LOCATION.TYPE, type)
      put(DB.LOCATION.TIME, time.toEpochMilli())
      put(DB.LOCATION.LATITUDE, latitude)
      put(DB.LOCATION.LONGITUDE, longitude)
      put(DB.LOCATION.LAP, lap)
    })
  }
}
