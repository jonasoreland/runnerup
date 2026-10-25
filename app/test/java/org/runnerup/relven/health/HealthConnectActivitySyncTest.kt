package org.runnerup.relven.health

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants.DB
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionStore
import org.runnerup.workout.RelvenStrengthWorkoutParser

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

  @Test
  fun automaticSyncDoesNotQueuePositiveDurationActivityWithoutEndMarker() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getDir("relven-health-connect-outbox", android.content.Context.MODE_PRIVATE).deleteRecursively()
    HealthConnectExportPreferences.setEnabled(context, true)
    val database = database()
    val activityId = insertActivity(
        database, Instant.parse("2026-10-24T08:00:00Z").epochSecond, deleted = false)

    Api26HealthConnectActivitySyncBridge.onActivitySaved(context, database, activityId)

    assertTrue(HealthConnectExportStore.pending(context).isEmpty())
    assertNull(readRecordedHealthConnectWorkout(context, database, activityId, requireFinalized = true))
    database.close()
    HealthConnectExportPreferences.setEnabled(context, false)
  }

  @Test
  fun automaticSyncCanExportFinalizedGpslessWorkoutWithoutInventingARoutePoint() {
    val database = database()
    val start = Instant.parse("2026-10-24T08:00:00Z")
    val activityId = insertActivity(database, start.epochSecond, deleted = false, duration = 90)
    org.runnerup.tracker.ActivityFinalizationStore.markFinalized(database, activityId, 10_000L, true)

    val workout = readRecordedHealthConnectWorkout(
        ApplicationProvider.getApplicationContext(), database, activityId, requireFinalized = true)

    assertEquals(start.plusSeconds(90), workout!!.endTime)
    assertTrue(workout.route.isEmpty())
    database.close()
  }

  @Test
  fun editingAnAlreadyPublishedLegacyActivityUpdatesItsExistingExport() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getDir("relven-health-connect-outbox", android.content.Context.MODE_PRIVATE).deleteRecursively()
    HealthConnectExportPreferences.setEnabled(context, true)
    val database = database()
    val activityId = insertActivity(
        database, Instant.parse("2026-10-24T08:00:00Z").epochSecond, deleted = false)
    val original = readRecordedHealthConnectWorkout(context, database, activityId)!!
    val published = HealthConnectExportStore.enqueueUpsert(context, original, 1_000L)
    assertTrue(
        HealthConnectExportStore.markExported(
            context,
            original.sessionId,
            published.version,
            setOf(HealthConnectRecordKind.SESSION),
            emptySet(),
            2_000L))
    database.update(
        DB.ACTIVITY.TABLE,
        ContentValues().apply { put(DB.ACTIVITY.NAME, "Edited run") },
        "_id = ?",
        arrayOf(activityId.toString()))

    Api26HealthConnectActivitySyncBridge.onActivitySaved(context, database, activityId)

    val updated = HealthConnectExportStore.get(context, original.sessionId)!!
    assertEquals(1L, updated.version)
    assertEquals("Edited run", HealthConnectWorkoutCodec.decode(updated.payload!!).title)
    database.close()
    HealthConnectExportPreferences.setEnabled(context, false)
  }

  @Test
  fun completedStrengthSessionUsesOneStableOptInOutboxEntry() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getDir("relven-health-connect-outbox", android.content.Context.MODE_PRIVATE).deleteRecursively()
    context.getDir("relven-strength-sessions", android.content.Context.MODE_PRIVATE).deleteRecursively()
    HealthConnectExportPreferences.setEnabled(context, true)
    val start = 1_800_000_000_000L
    val workout = strengthWorkout()
    val completed = StrengthSession.prepare("plan", "entry", workout)
        .begin(1_000L, start)
        .completeRepetitions(12, 5_000L, start + 4_000L)
    StrengthSessionStore.complete(context, completed, start + 5_000L, healthConnectExportRequested = true)

    Api26HealthConnectActivitySyncBridge.onStrengthSessionSaved(context, completed.executionId)
    val first = HealthConnectExportStore.get(context, "strength-${completed.executionId}")!!
    Api26HealthConnectActivitySyncBridge.onStrengthSessionSaved(context, completed.executionId)
    val retry = HealthConnectExportStore.get(context, first.sessionId)!!
    val workoutRecord = HealthConnectWorkoutCodec.decode(requireNotNull(retry.payload))

    assertEquals(first.version, retry.version)
    assertEquals(first.payload, retry.payload)
    assertEquals(HealthConnectActivity.Strength, workoutRecord.activity)
    assertEquals("Wall push-up · set 1: 12 reps", workoutRecord.notes)
    HealthConnectExportPreferences.setEnabled(context, false)
  }

  @Test
  fun startupReconciliationDoesNotSilentlyBackfillUnrequestedStrengthHistory() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getDir("relven-health-connect-outbox", android.content.Context.MODE_PRIVATE).deleteRecursively()
    context.getDir("relven-strength-sessions", android.content.Context.MODE_PRIVATE).deleteRecursively()
    HealthConnectExportPreferences.setEnabled(context, true)
    val start = 1_800_000_000_000L
    val completed = StrengthSession.prepare("plan", "entry", strengthWorkout())
        .begin(1_000L, start)
        .completeRepetitions(9, 5_000L, start + 4_000L)
    // Legacy imports and sessions completed while export was off carry no automatic export request.
    StrengthSessionStore.complete(context, completed, start + 5_000L)
    val database = database()

    Api26HealthConnectActivitySyncBridge.reconcilePending(context, database)

    assertNull(HealthConnectExportStore.get(context, "strength-${completed.executionId}"))
    assertTrue(HealthConnectExportStore.pending(context).isEmpty())
    database.close()
    HealthConnectExportPreferences.setEnabled(context, false)
  }

  @Test
  fun explicitBackfillIncludesCompletedStrengthSessionsWithRealStartTimes() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getDir("relven-health-connect-outbox", android.content.Context.MODE_PRIVATE).deleteRecursively()
    context.getDir("relven-strength-sessions", android.content.Context.MODE_PRIVATE).deleteRecursively()
    HealthConnectExportPreferences.setEnabled(context, true)
    val start = 1_800_000_000_000L
    val completed = StrengthSession.prepare("plan", "entry", strengthWorkout())
        .begin(1_000L, start)
        .completeRepetitions(9, 5_000L, start + 4_000L)
    StrengthSessionStore.complete(context, completed, start + 5_000L)
    val database = database()

    val snapshot = Api26HealthConnectSettingsBridge.snapshot(context, database, emptySet())
    val backfill = Api26HealthConnectSettingsBridge.queueBackfill(context, database)

    assertEquals(1, snapshot.eligibleBackfillCount)
    assertEquals(1, backfill.queued)
    assertEquals(HealthConnectExportStatus.PENDING,
        HealthConnectExportStore.get(context, "strength-${completed.executionId}")!!.status)
    database.close()
    HealthConnectExportPreferences.setEnabled(context, false)
  }

  private fun database(): SQLiteDatabase = SQLiteDatabase.create(null).apply {
    execSQL("CREATE TABLE ${DB.ACTIVITY.TABLE} (_id INTEGER PRIMARY KEY, ${DB.ACTIVITY.START_TIME} INTEGER, ${DB.ACTIVITY.DISTANCE} REAL, ${DB.ACTIVITY.TIME} INTEGER, ${DB.ACTIVITY.NAME} TEXT, ${DB.ACTIVITY.COMMENT} TEXT, ${DB.ACTIVITY.SPORT} INTEGER, ${DB.ACTIVITY.DELETED} INTEGER)")
    execSQL("CREATE TABLE ${DB.LOCATION.TABLE} (_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LOCATION.ACTIVITY} INTEGER, ${DB.LOCATION.TYPE} INTEGER, ${DB.LOCATION.TIME} INTEGER, ${DB.LOCATION.LATITUDE} REAL, ${DB.LOCATION.LONGITUDE} REAL, ${DB.LOCATION.ALTITUDE} REAL, ${DB.LOCATION.HR} INTEGER, ${DB.LOCATION.SPEED} REAL, ${DB.LOCATION.CADENCE} REAL, ${DB.LOCATION.LAP} INTEGER)")
    execSQL("CREATE TABLE ${DB.LAP.TABLE} (_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LAP.ACTIVITY} INTEGER, ${DB.LAP.LAP} INTEGER, ${DB.LAP.DISTANCE} REAL)")
    org.runnerup.tracker.ActivityFinalizationStore.ensureSchema(this)
  }

  private fun strengthWorkout() = RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"strength","name":"Prehab","sport":"strength","steps":[
        {"id":"pushup","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Wall push-up","instructions":"Push with control.","equipment":[],"images":[]},"sets":1,"minRepetitions":1,"maxRepetitions":20,"restSeconds":0}}]}""")

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
