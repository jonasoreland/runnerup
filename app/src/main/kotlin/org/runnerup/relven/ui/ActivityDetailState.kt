package org.runnerup.relven.ui

import android.database.sqlite.SQLiteDatabase
import org.runnerup.common.util.Constants.DB
import org.runnerup.workout.Sport

internal sealed interface ActivityDetailLoadState {
  data object Loading : ActivityDetailLoadState

  data object NotFound : ActivityDetailLoadState

  data object Unavailable : ActivityDetailLoadState

  data class Loaded(val activity: HistoryActivityDetails) : ActivityDetailLoadState
}

internal data class HistoryActivityDetails(
    val id: Long,
    val startedAtSeconds: Long,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val sport: Int,
    val gpsSampleCount: Int,
    val comment: String?,
    val averageHeartRate: Int?,
    val maximumHeartRate: Int?)

internal data class ActivityDetailMeasurements(
    val distanceMeters: Double?,
    val durationSeconds: Long?,
    val averageSpeedMetersPerSecond: Double?,
    val averageHeartRate: Int?,
    val maximumHeartRate: Int?)

internal sealed interface ActivityMapLoadState {
  data object NotFound : ActivityMapLoadState

  data object RouteUnavailable : ActivityMapLoadState

  data object Unavailable : ActivityMapLoadState

  data class Loaded(val activity: HistoryActivityDetails) : ActivityMapLoadState
}

internal fun readActivityMapState(
    database: SQLiteDatabase,
    activityId: Long,
    mapEnabled: Boolean
): ActivityMapLoadState {
  if (!mapEnabled) return ActivityMapLoadState.Unavailable
  val activity = readHistoryActivityDetails(database, activityId) ?: return ActivityMapLoadState.NotFound
  return if (activityHasMapRoute(activity, mapEnabled = true)) ActivityMapLoadState.Loaded(activity)
  else ActivityMapLoadState.RouteUnavailable
}

internal fun activityHasMapRoute(activity: HistoryActivityDetails, mapEnabled: Boolean): Boolean =
    mapEnabled && !Sport.isWithoutGps(activity.sport) && activity.gpsSampleCount >= 2

internal fun readHistoryActivityDetails(
    database: SQLiteDatabase,
    activityId: Long
): HistoryActivityDetails? {
  if (activityId <= 0L) return null
  val columns =
      arrayOf(
          "_id",
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.SPORT,
          DB.ACTIVITY.COMMENT,
          DB.ACTIVITY.AVG_HR,
          DB.ACTIVITY.MAX_HR)
  val gpsSampleCount =
      database
          .query(
              DB.LOCATION.TABLE,
              arrayOf("_id"),
              "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ?",
              arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString()),
              null,
              null,
              null)
          .use { it.count }
  return database
      .query(
          DB.ACTIVITY.TABLE,
          columns,
          "${DB.ACTIVITY.DELETED} == 0 AND _id == ?",
          arrayOf(activityId.toString()),
          null,
          null,
          null)
      .use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val startedAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.ACTIVITY.START_TIME))
        if (startedAt <= 0L) return@use null
        fun longValue(column: String): Long? {
          val index = cursor.getColumnIndexOrThrow(column)
          return if (cursor.isNull(index)) null else cursor.getLong(index)
        }
        fun intValue(column: String): Int? {
          val index = cursor.getColumnIndexOrThrow(column)
          return if (cursor.isNull(index)) null else cursor.getInt(index)
        }
        val distanceIndex = cursor.getColumnIndexOrThrow(DB.ACTIVITY.DISTANCE)
        val commentIndex = cursor.getColumnIndexOrThrow(DB.ACTIVITY.COMMENT)
        HistoryActivityDetails(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
            startedAtSeconds = startedAt,
            durationSeconds = longValue(DB.ACTIVITY.TIME),
            distanceMeters =
                if (cursor.isNull(distanceIndex)) null else cursor.getDouble(distanceIndex),
            sport = cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.SPORT)),
            gpsSampleCount = gpsSampleCount,
            comment = if (cursor.isNull(commentIndex)) null else cursor.getString(commentIndex),
            averageHeartRate = intValue(DB.ACTIVITY.AVG_HR),
            maximumHeartRate = intValue(DB.ACTIVITY.MAX_HR))
      }
}

internal fun activityDetailMeasurements(
    activity: HistoryActivityDetails
): ActivityDetailMeasurements {
  val distance = activity.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
  val duration = activity.durationSeconds?.takeIf { it >= 0L }
  val averageSpeed =
      if (distance != null && duration != null && duration > 0L) distance / duration else null
  return ActivityDetailMeasurements(
      distanceMeters = distance,
      durationSeconds = duration,
      averageSpeedMetersPerSecond = averageSpeed?.takeIf { it.isFinite() && it > 0.0 },
      averageHeartRate = activity.averageHeartRate?.takeIf { it > 0 },
      maximumHeartRate = activity.maximumHeartRate?.takeIf { it > 0 })
}
