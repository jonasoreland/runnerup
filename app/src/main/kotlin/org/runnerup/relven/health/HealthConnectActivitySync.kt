package org.runnerup.relven.health

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.runnerup.common.util.Constants.DB
import org.runnerup.workout.Sport
import java.time.Instant

/** Safe, non-blocking entry point for legacy RunnerUp Java lifecycle and history actions. */
object HealthConnectActivitySync {
  private const val TAG = "RelvenHealthConnect"
  private const val BRIDGE = "org.runnerup.relven.health.Api26HealthConnectActivitySyncBridge"

  @JvmStatic
  fun onActivitySaved(context: Context, database: SQLiteDatabase, activityId: Long) {
    invoke(context, "onActivitySaved", database, activityId)
  }

  @JvmStatic
  fun onActivityDeleted(context: Context, activityId: Long) {
    invoke(context, "onActivityDeleted", null, activityId)
  }

  private fun invoke(context: Context, method: String, database: SQLiteDatabase?, activityId: Long) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    try {
      val target = Class.forName(BRIDGE)
      if (database == null) {
        target.getMethod(method, Context::class.java, Long::class.javaPrimitiveType)
            .invoke(null, context.applicationContext, activityId)
      } else {
        target.getMethod(method, Context::class.java, SQLiteDatabase::class.java, Long::class.javaPrimitiveType)
            .invoke(null, context.applicationContext, database, activityId)
      }
    } catch (error: ReflectiveOperationException) {
      Log.w(TAG, "Health Connect outbox hook was unavailable", error)
    } catch (error: LinkageError) {
      Log.w(TAG, "Health Connect outbox hook could not load", error)
    } catch (error: RuntimeException) {
      Log.w(TAG, "Could not queue Health Connect activity update", error)
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
internal object Api26HealthConnectActivitySyncBridge {
  @JvmStatic
  fun onActivitySaved(context: Context, database: SQLiteDatabase, activityId: Long) {
    if (!HealthConnectExportPreferences.isEnabled(context)) return
    val workout = readRecordedHealthConnectWorkout(context, database, activityId) ?: return
    val entry = HealthConnectExportStore.enqueueUpsert(context, workout, System.currentTimeMillis())
    if (entry.operation == HealthConnectExportOperation.UPSERT) {
      HealthConnectExportScheduler.enqueue(context, entry.sessionId)
    }
  }

  @JvmStatic
  fun onActivityDeleted(context: Context, activityId: Long) {
    val entry = HealthConnectExportStore.enqueueDelete(context, "activity-$activityId", System.currentTimeMillis())
    if (entry != null) HealthConnectExportScheduler.enqueue(context, entry.sessionId)
  }
}

/** Loads only final, saved RunnerUp data. A missing end marker falls back to recorded duration. */
@RequiresApi(Build.VERSION_CODES.O)
internal fun readRecordedHealthConnectWorkout(
    context: Context,
    database: SQLiteDatabase,
    activityId: Long): RecordedWorkoutForHealthConnect? {
  val activity = database.query(
      DB.ACTIVITY.TABLE,
      arrayOf(DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.NAME,
          DB.ACTIVITY.COMMENT, DB.ACTIVITY.SPORT, DB.ACTIVITY.DELETED),
      "_id = ?", arrayOf(activityId.toString()), null, null, null, "1").use { cursor ->
    if (!cursor.moveToFirst()) return null
    val deleted = cursor.getColumnIndexOrThrow(DB.ACTIVITY.DELETED)
    val duration = cursor.optionalLong(DB.ACTIVITY.TIME)
    if (cursor.getInt(deleted) != 0 || duration == null || duration <= 0) return null
    SavedActivityRow(
        startSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(DB.ACTIVITY.START_TIME)),
        durationSeconds = duration,
        distanceMeters = cursor.optionalDouble(DB.ACTIVITY.DISTANCE),
        name = cursor.optionalString(DB.ACTIVITY.NAME),
        notes = cursor.optionalString(DB.ACTIVITY.COMMENT),
        sport = cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.SPORT)))
  }
  val start = Instant.ofEpochSecond(activity.startSeconds)
  val fallbackEnd = start.plusSeconds(activity.durationSeconds)
  val route = mutableListOf<HealthConnectRouteSample>()
  val lapForPoint = mutableListOf<Pair<Int, Instant>>()
  var recordedEnd: Instant? = null
  database.query(
      DB.LOCATION.TABLE,
      arrayOf(DB.LOCATION.TYPE, DB.LOCATION.TIME, DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE,
          DB.LOCATION.ALTITUDE, DB.LOCATION.HR, DB.LOCATION.SPEED, DB.LOCATION.CADENCE, DB.LOCATION.LAP),
      "${DB.LOCATION.ACTIVITY} = ?", arrayOf(activityId.toString()), null, null, "_id ASC").use { cursor ->
    val type = cursor.getColumnIndexOrThrow(DB.LOCATION.TYPE)
    val time = cursor.getColumnIndexOrThrow(DB.LOCATION.TIME)
    while (cursor.moveToNext()) {
      if (cursor.isNull(time)) continue
      val instant = Instant.ofEpochMilli(cursor.getLong(time))
      if (cursor.getInt(type) == DB.LOCATION.TYPE_END) recordedEnd = instant
      if (cursor.getInt(type) != DB.LOCATION.TYPE_GPS) continue
      route += HealthConnectRouteSample(
          time = instant,
          latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.LOCATION.LATITUDE)),
          longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.LOCATION.LONGITUDE)),
          altitudeMeters = cursor.optionalDouble(DB.LOCATION.ALTITUDE),
          heartRateBpm = cursor.optionalLong(DB.LOCATION.HR)?.toDouble(),
          speedMetersPerSecond = cursor.optionalDouble(DB.LOCATION.SPEED),
          cadencePerMinute = cursor.optionalDouble(DB.LOCATION.CADENCE))
      lapForPoint += (if (cursor.isNull(cursor.getColumnIndexOrThrow(DB.LOCATION.LAP))) 0
          else cursor.getInt(cursor.getColumnIndexOrThrow(DB.LOCATION.LAP))) to instant
    }
  }
  val end = recordedEnd?.takeIf { it > start } ?: fallbackEnd
  if (end <= start) return null
  val laps = readTimestampedLaps(database, activityId, start, end, lapForPoint)
  return RecordedWorkoutForHealthConnect(
      sessionId = "activity-$activityId",
      version = 0,
      activity = activity.sport.toHealthConnectActivity(),
      title = activity.name?.takeIf(String::isNotBlank) ?: Sport.textOf(context.resources, activity.sport),
      notes = activity.notes,
      startTime = start,
      endTime = end,
      distanceMeters = activity.distanceMeters,
      route = route.sortedBy(HealthConnectRouteSample::time),
      laps = laps)
}

private data class SavedActivityRow(
    val startSeconds: Long,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val name: String?,
    val notes: String?,
    val sport: Int)

@RequiresApi(Build.VERSION_CODES.O)
private fun readTimestampedLaps(
    database: SQLiteDatabase,
    activityId: Long,
    sessionStart: Instant,
    sessionEnd: Instant,
    gpsLapMarkers: List<Pair<Int, Instant>>): List<HealthConnectLap> {
  val rows = mutableListOf<Pair<Int, Double?>>()
  database.query(DB.LAP.TABLE, arrayOf(DB.LAP.LAP, DB.LAP.DISTANCE),
      "${DB.LAP.ACTIVITY} = ?", arrayOf(activityId.toString()), null, null, "_id ASC").use { cursor ->
    while (cursor.moveToNext()) rows += cursor.getInt(0) to cursor.optionalDouble(DB.LAP.DISTANCE)
  }
  if (rows.isEmpty()) return emptyList()
  val starts = gpsLapMarkers.groupBy(Pair<Int, Instant>::first).mapValues { (_, markers) -> markers.minOf(Pair<Int, Instant>::second) }
  return rows.mapIndexedNotNull { index, (number, distance) ->
    val lapStart = if (index == 0) sessionStart else starts[number] ?: return@mapIndexedNotNull null
    val lapEnd = if (index == rows.lastIndex) sessionEnd else starts[rows[index + 1].first] ?: return@mapIndexedNotNull null
    if (lapStart >= lapEnd) return@mapIndexedNotNull null
    HealthConnectLap(lapStart, lapEnd, distance)
  }
}

private fun Int.toHealthConnectActivity(): HealthConnectActivity = when (this) {
  DB.ACTIVITY.SPORT_RUNNING -> HealthConnectActivity.Running
  DB.ACTIVITY.SPORT_TREADMILL -> HealthConnectActivity.TreadmillRunning
  DB.ACTIVITY.SPORT_WALKING -> HealthConnectActivity.Walking
  DB.ACTIVITY.SPORT_BIKING -> HealthConnectActivity.Cycling
  DB.ACTIVITY.SPORT_STATIONARY_BIKE -> HealthConnectActivity.StationaryCycling
  DB.ACTIVITY.SPORT_GYM -> HealthConnectActivity.Strength
  else -> HealthConnectActivity.Other
}

private fun Cursor.optionalLong(column: String): Long? {
  val index = getColumnIndexOrThrow(column)
  return if (isNull(index)) null else getLong(index)
}

private fun Cursor.optionalDouble(column: String): Double? {
  val index = getColumnIndexOrThrow(column)
  return if (isNull(index)) null else getDouble(index).takeIf(Double::isFinite)
}

private fun Cursor.optionalString(column: String): String? {
  val index = getColumnIndexOrThrow(column)
  return if (isNull(index)) null else getString(index)
}
