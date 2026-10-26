package org.runnerup.relven.telemetry

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants.DB
import org.runnerup.common.wear.WorkoutTelemetryProtocol
import org.runnerup.relven.ui.readActivityRecoverySummary
import org.runnerup.relven.ui.readActivityRoute
import org.runnerup.relven.ui.RouteMetric

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutTelemetryRouteReconcilerTest {
  private val database = SQLiteDatabase.create(null).apply {
    execSQL(
        "CREATE TABLE ${DB.ACTIVITY.TABLE} (" +
            "_id INTEGER PRIMARY KEY, ${DB.ACTIVITY.DISTANCE} REAL, ${DB.ACTIVITY.TIME} INTEGER, " +
            "${DB.ACTIVITY.AVG_HR} INTEGER, ${DB.ACTIVITY.MAX_HR} INTEGER, " +
            "${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0)")
    execSQL(
        "CREATE TABLE ${DB.LOCATION.TABLE} (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "${DB.LOCATION.ACTIVITY} INTEGER NOT NULL, ${DB.LOCATION.LAP} INTEGER NOT NULL, " +
            "${DB.LOCATION.TYPE} INTEGER NOT NULL, ${DB.LOCATION.TIME} INTEGER NOT NULL, " +
            "${DB.LOCATION.LONGITUDE} REAL NOT NULL, ${DB.LOCATION.LATITUDE} REAL NOT NULL, " +
            "${DB.LOCATION.ALTITUDE} REAL, ${DB.LOCATION.ACCURANCY} REAL, ${DB.LOCATION.HR} INTEGER, " +
            "${DB.LOCATION.SPEED} REAL, ${DB.LOCATION.CADENCE} REAL, " +
            "${DB.LOCATION.ELAPSED} REAL, ${DB.LOCATION.DISTANCE} REAL)")
    insert(DB.ACTIVITY.TABLE, null, ContentValues().apply {
      put("_id", ACTIVITY_ID)
      put(DB.ACTIVITY.DISTANCE, 50.0)
      put(DB.ACTIVITY.TIME, 60)
    })
    WorkoutTelemetryInboxStore.ensureSchema(this)
  }
  private val inbox = WorkoutTelemetryInboxStore(database)

  @After
  fun closeDatabase() = database.close()

  @Test
  fun materializesCommittedAccurateWatchLocationIntoTheExistingActivity() {
    commit(sample(1))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(1, result.insertedLocations)
    assertEquals(1, locationCount())
    database.query(DB.LOCATION.TABLE, arrayOf(DB.LOCATION.ACTIVITY, DB.LOCATION.HR), null, null,
        null, null, "_id ASC").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(ACTIVITY_ID, cursor.getLong(0))
      assertEquals(140, cursor.getInt(1))
    }
  }

  @Test
  fun identicalReconciliationReplayDoesNotInsertDuplicateLocation() {
    commit(sample(1))
    val reconciler = WorkoutTelemetryRouteReconciler(database)

    assertTrue(reconciler.reconcilePending(SESSION_ID).success)
    val replay = reconciler.reconcilePending(SESSION_ID)

    assertTrue(replay.success)
    assertEquals(0, replay.insertedLocations)
    assertEquals(1, locationCount())
  }

  @Test
  fun phoneGpsCoverageWinsAndWatchHeartRateOnlyFillsMissingValue() {
    val phoneLocationId = insertPhoneLocation(SAMPLE_TIME + 500)
    commit(sample(1))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(0, result.insertedLocations)
    assertEquals(1, result.enrichedPhoneLocations)
    assertEquals(1, locationCount())
    database.query(DB.LOCATION.TABLE, arrayOf(DB.LOCATION.HR), "_id = ?",
        arrayOf(phoneLocationId.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(140, cursor.getInt(0))
    }
  }

  @Test
  fun phoneHeartRateEnrichmentRollsBackWhenReceiptCannotCommit() {
    val phoneLocationId = insertPhoneLocation(SAMPLE_TIME + 500)
    commit(sample(1))
    WorkoutTelemetryRouteReconciler.ensureSchema(database)
    database.execSQL(
        "CREATE TRIGGER reject_enriched_receipt BEFORE INSERT ON relven_wear_telemetry_route_receipt " +
            "WHEN NEW.disposition = 3 BEGIN SELECT RAISE(ABORT, 'injected receipt failure'); END")

    val failed = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertFalse(failed.success)
    database.query(DB.LOCATION.TABLE, arrayOf(DB.LOCATION.HR), "_id = ?",
        arrayOf(phoneLocationId.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(0))
    }
    database.execSQL("DROP TRIGGER reject_enriched_receipt")
    val retry = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)
    assertTrue(retry.success)
    assertEquals(1, retry.enrichedPhoneLocations)
  }

  @Test
  fun inaccurateWatchFixIsRetainedInInboxButNotAddedToActivityRoute() {
    commit(sample(1, accuracyMeters = 60.0))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(0, result.insertedLocations)
    assertEquals(0, locationCount())
    assertEquals(1, inbox.sampleCount(SESSION_ID))
  }

  @Test
  fun lateOfflineWatchPointIsReadInCapturedTimeOrder() {
    insertPhoneLocation(SAMPLE_TIME + 20_000, latitude = 59.35)
    commit(sample(1))
    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)
    val route = readActivityRoute(database, ACTIVITY_ID)

    assertTrue(result.success)
    assertEquals(result.toString(), 1, result.insertedLocations)
    assertEquals(2, locationCount())
    assertEquals(2, route.size)
    assertEquals(59.33001, route.first().latitude, 0.000001)
    assertEquals(59.35, route.last().latitude, 0.000001)
  }

  @Test
  fun phoneWallClockRegressionKeepsOriginalRouteChronology() {
    insertPhoneLocation(SAMPLE_TIME + 20_000, latitude = 59.36,
        elapsedMillis = 20_000.0, distanceMeters = 100.0)
    insertPhoneLocation(SAMPLE_TIME + 10_000, latitude = 59.35,
        elapsedMillis = 30_000.0, distanceMeters = 110.0)
    commit(sample(1, wallTimeMillis = SAMPLE_TIME + 15_000))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)
    val route = readActivityRoute(database, ACTIVITY_ID)

    assertTrue(result.success)
    assertEquals(1, result.insertedLocations)
    assertEquals(3, route.size)
    assertEquals(59.36, route[0].latitude, 0.000001)
    assertEquals(59.35, route[1].latitude, 0.000001)
    assertEquals(59.33001, route[2].latitude, 0.000001)
  }

  @Test
  fun watchOnlyRouteKeepsPartialMetricsUnknown() {
    commit(sample(1))
    commit(sample(2))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)
    val route = readActivityRoute(database, ACTIVITY_ID)
    val recovery = readActivityRecoverySummary(database, ACTIVITY_ID)

    assertTrue(result.success)
    assertEquals(2, result.insertedLocations)
    assertEquals(2, route.size)
    assertTrue(route.last().distanceMeters > route.first().distanceMeters)
    assertEquals(route.first().elapsedSeconds, route.last().elapsedSeconds, 0.0)
    assertNull(RouteMetric.PACE.value(route.last(), route.first()))
    assertNull(recovery?.distanceMeters)
    assertNull(recovery?.durationSeconds)
    database.query(DB.ACTIVITY.TABLE, arrayOf(DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME),
        "_id = ?", arrayOf(ACTIVITY_ID.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(50.0, cursor.getDouble(0), 0.0)
      assertEquals(60L, cursor.getLong(1))
    }
  }

  @Test
  fun watchElapsedIsInterpolatedOnlyBetweenPhoneCheckpointsInOneActiveSegment() {
    insertPhoneLocation(SAMPLE_TIME - 6_000, elapsedMillis = 5_000.0, distanceMeters = 50.0)
    insertPhoneLocation(SAMPLE_TIME + 6_000, latitude = 59.34, elapsedMillis = 17_000.0,
        distanceMeters = 60.0)
    commit(sample(1))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(1, result.insertedLocations)
    database.query(DB.LOCATION.TABLE, arrayOf(DB.LOCATION.ELAPSED),
        "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ? AND ${DB.LOCATION.ELAPSED} IS NOT NULL",
        arrayOf(ACTIVITY_ID.toString(), DB.LOCATION.TYPE_GPS.toString()), null, null,
        "${DB.LOCATION.TIME} ASC").use { cursor ->
      assertEquals(3, cursor.count)
      cursor.moveToPosition(1)
      assertEquals(12_000.0, cursor.getDouble(0), 1.0)
    }
  }

  @Test
  fun watchElapsedStaysUnknownWhenPhoneCheckpointsSpanPauseAndResume() {
    insertPhoneLocation(SAMPLE_TIME - 6_000, elapsedMillis = 5_000.0, distanceMeters = 50.0)
    insertPhoneLocation(SAMPLE_TIME + 6_000, latitude = 59.34, elapsedMillis = 11_000.0,
        distanceMeters = 55.0)
    insertMarker(DB.LOCATION.TYPE_PAUSE, SAMPLE_TIME - 2_000)
    insertMarker(DB.LOCATION.TYPE_RESUME, SAMPLE_TIME + 2_000)
    commit(sample(1, wallTimeMillis = SAMPLE_TIME + 3_000))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)
    val route = readActivityRoute(database, ACTIVITY_ID)

    assertTrue(result.success)
    assertEquals(1, result.insertedLocations)
    assertEquals(3, route.size)
    assertNull(RouteMetric.PACE.value(route[1], route[0]))
    assertNull(RouteMetric.PACE.value(route[2], route[1]))
    database.query(DB.LOCATION.TABLE, arrayOf(DB.LOCATION.ELAPSED, DB.LOCATION.DISTANCE),
        "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ? AND ${DB.LOCATION.TIME} = ?",
        arrayOf(ACTIVITY_ID.toString(), DB.LOCATION.TYPE_GPS.toString(),
            (SAMPLE_TIME + 3_000).toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(0))
      assertTrue(cursor.isNull(1))
    }
  }

  @Test
  fun pausedWatchPointIsRetainedButNotAddedToTheRoute() {
    insertMarker(DB.LOCATION.TYPE_START, SESSION_START)
    insertMarker(DB.LOCATION.TYPE_PAUSE, SAMPLE_TIME)
    commit(sample(1))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(0, result.insertedLocations)
    assertEquals(1, result.ignoredSamples)
    assertEquals(0, locationCount())
  }

  @Test
  fun preStartWatchPointWithinInboxToleranceIsNotAddedToRoute() {
    commit(sample(1, wallTimeMillis = SESSION_START - 60_000))

    val result = WorkoutTelemetryRouteReconciler(database).reconcilePending(SESSION_ID)

    assertTrue(result.success)
    assertEquals(0, result.insertedLocations)
    assertEquals(0, locationCount())
  }

  private fun commit(sample: WorkoutTelemetryProtocol.Sample) {
    inbox.associateActivity(SESSION_ID, ACTIVITY_ID, SESSION_START)
    val result = inbox.commitBatch(
        WorkoutTelemetryProtocol.Batch(SESSION_ID, listOf(sample)), NOW)
    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.COMMITTED, result.status)
  }

  private fun sample(
      sequence: Long,
      accuracyMeters: Double = 5.0,
      wallTimeMillis: Long = SAMPLE_TIME + sequence * 1_000) =
      WorkoutTelemetryProtocol.Sample(
          SESSION_ID,
          sequence,
          sequence * 1_000_000_000,
          wallTimeMillis,
          59.33 + sequence * 0.00001,
          18.06,
          accuracyMeters,
          null,
          3.0,
          140.0)

  private fun insertPhoneLocation(
      timeMillis: Long,
      latitude: Double = 59.33,
      elapsedMillis: Double = 10_000.0,
      distanceMeters: Double = 50.0
  ): Long =
      database.insertOrThrow(DB.LOCATION.TABLE, null, ContentValues().apply {
        put(DB.LOCATION.ACTIVITY, ACTIVITY_ID)
        put(DB.LOCATION.LAP, 0)
        put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS)
        put(DB.LOCATION.TIME, timeMillis)
        put(DB.LOCATION.LATITUDE, latitude)
        put(DB.LOCATION.LONGITUDE, 18.06)
        put(DB.LOCATION.ELAPSED, elapsedMillis)
        put(DB.LOCATION.DISTANCE, distanceMeters)
      })

  private fun insertMarker(type: Int, timeMillis: Long) {
    database.insertOrThrow(DB.LOCATION.TABLE, null, ContentValues().apply {
      put(DB.LOCATION.ACTIVITY, ACTIVITY_ID)
      put(DB.LOCATION.LAP, 0)
      put(DB.LOCATION.TYPE, type)
      put(DB.LOCATION.TIME, timeMillis)
      put(DB.LOCATION.LATITUDE, 59.33)
      put(DB.LOCATION.LONGITUDE, 18.06)
    })
  }

  private fun locationCount(): Int =
      database.query(
          DB.LOCATION.TABLE,
          arrayOf("COUNT(*)"),
          "${DB.LOCATION.TYPE} = ?",
          arrayOf(DB.LOCATION.TYPE_GPS.toString()),
          null,
          null,
          null).use {
        it.moveToFirst()
        it.getInt(0)
      }

  private companion object {
    const val SESSION_ID = "wear-route-session"
    const val ACTIVITY_ID = 41L
    const val SESSION_START = 1_800_000_000_000L
    const val SAMPLE_TIME = SESSION_START + 10_000
    const val NOW = SESSION_START + 60_000
  }
}
