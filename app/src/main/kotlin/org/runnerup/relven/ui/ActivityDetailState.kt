package org.runnerup.relven.ui

import android.database.sqlite.SQLiteDatabase
import android.location.Location
import org.runnerup.db.ActivityRouteOrder
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
    val maximumHeartRate: Int?,
    val averageCadence: Double? = null,
    val laps: List<ActivityLapDetails> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    val unfinished: Boolean = false)

internal data class ActivityRecoverySummary(
    val durationSeconds: Long?,
    val distanceMeters: Double?)

internal data class ActivityLapDetails(
    val number: Int,
    val intensity: Int,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val averageHeartRate: Int?,
    val plannedDurationSeconds: Long? = null,
    val plannedDistanceMeters: Double? = null,
    val plannedPace: Double? = null)

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

  data class Loaded(val activity: HistoryActivityDetails, val route: List<RoutePoint>) : ActivityMapLoadState
}

internal fun readActivityMapState(
    database: SQLiteDatabase,
    activityId: Long,
    mapEnabled: Boolean
): ActivityMapLoadState {
  if (!mapEnabled) return ActivityMapLoadState.Unavailable
  val activity = readHistoryActivityDetails(database, activityId) ?: return ActivityMapLoadState.NotFound
  if (!activityHasMapRoute(activity, mapEnabled = true)) return ActivityMapLoadState.RouteUnavailable
  val route = activity.route
  return if (route.size >= 2) ActivityMapLoadState.Loaded(activity, route)
  else ActivityMapLoadState.RouteUnavailable
}

internal fun readActivityRoute(database: SQLiteDatabase, activityId: Long): List<RoutePoint> {
  val points = mutableListOf<RoutePoint>()
  var segment = 0
  var previousLatitude: Double? = null
  var previousLongitude: Double? = null
  var previousTime: Long? = null
  var cumulativeDistance = 0.0
  var cumulativeElapsedMillis = 0L
  var timingSegment = 0
  var hasUnknownWearElapsed = false
  val wearRouteLocationIds = ActivityRouteOrder.wearRouteLocationIds(database, activityId)
  val columns =
      arrayOf(
          DB.PRIMARY_KEY,
          DB.LOCATION.TYPE,
          DB.LOCATION.LATITUDE,
          DB.LOCATION.LONGITUDE,
          DB.LOCATION.ALTITUDE,
          DB.LOCATION.TIME,
          DB.LOCATION.ELAPSED,
          DB.LOCATION.DISTANCE,
          DB.LOCATION.LAP,
          DB.LOCATION.HR,
          DB.LOCATION.SPEED,
          DB.LOCATION.CADENCE)
  database
      .query(
          DB.LOCATION.TABLE,
          columns,
          "${DB.LOCATION.ACTIVITY} = ?",
          arrayOf(activityId.toString()),
          null,
          null,
          ActivityRouteOrder.ascending(database, activityId))
      .use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(DB.PRIMARY_KEY)
        val typeIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.TYPE)
        val latitudeIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.LATITUDE)
        val longitudeIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.LONGITUDE)
        val altitudeIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.ALTITUDE)
        val timeIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.TIME)
        val elapsedIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.ELAPSED)
        val distanceIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.DISTANCE)
        val lapIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.LAP)
        val heartRateIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.HR)
        val speedIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.SPEED)
        val cadenceIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.CADENCE)
        while (cursor.moveToNext()) {
          val isWearTelemetry = wearRouteLocationIds.contains(cursor.getLong(idIndex))
          val type = cursor.getInt(typeIndex)
          val latitude = cursor.getDouble(latitudeIndex)
          val longitude = cursor.getDouble(longitudeIndex)
          val time = if (cursor.isNull(timeIndex)) null else cursor.getLong(timeIndex)
          val persistedElapsedMillis =
              if (cursor.isNull(elapsedIndex)) null
              else cursor.getDouble(elapsedIndex).takeIf { it.isFinite() && it >= 0.0 }
          val persistedDistanceMeters =
              if (cursor.isNull(distanceIndex)) null
              else cursor.getDouble(distanceIndex).takeIf { it.isFinite() && it >= 0.0 }
          if (type == DB.LOCATION.TYPE_PAUSE || type == DB.LOCATION.TYPE_RESUME) {
            segment++
            timingSegment++
            hasUnknownWearElapsed = false
          }
          if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) {
            previousLatitude = null
            previousLongitude = null
            previousTime = time
            continue
          }
          val priorLatitude = previousLatitude
          val priorLongitude = previousLongitude
          val priorTime = previousTime
          var geometryDistanceDelta = 0.0
          if ((type == DB.LOCATION.TYPE_PAUSE || type == DB.LOCATION.TYPE_GPS) &&
              priorLatitude != null && priorLongitude != null) {
            val delta = FloatArray(1)
            Location.distanceBetween(
                priorLatitude, priorLongitude, latitude, longitude, delta)
            geometryDistanceDelta = delta[0].toDouble()
          }
          // Tracker persists accepted cumulative values. Prefer them over recomputing distance
          // from coordinates or elapsed time from wall clock, both of which can drift or jump.
          cumulativeDistance =
              persistedDistanceMeters?.takeIf { it >= cumulativeDistance }
                  ?: (cumulativeDistance + geometryDistanceDelta)
          if (persistedElapsedMillis != null) {
            if (persistedElapsedMillis >= cumulativeElapsedMillis) {
              cumulativeElapsedMillis = kotlin.math.round(persistedElapsedMillis).toLong()
            }
          } else if (!isWearTelemetry && time != null && priorTime != null) {
            cumulativeElapsedMillis += (time - priorTime).coerceAtLeast(0L)
          }
          if (type == DB.LOCATION.TYPE_GPS) {
            if (isWearTelemetry && persistedElapsedMillis == null) {
              if (!hasUnknownWearElapsed) timingSegment++
              hasUnknownWearElapsed = true
            } else if (hasUnknownWearElapsed) {
              timingSegment++
              hasUnknownWearElapsed = false
            }
            points +=
                RoutePoint(
                    latitude = latitude,
                    longitude = longitude,
                    lap = if (cursor.isNull(lapIndex)) 0 else cursor.getInt(lapIndex),
                    elapsedSeconds = cumulativeElapsedMillis / 1000.0,
                    distanceMeters = cumulativeDistance,
                    heartRate =
                        if (cursor.isNull(heartRateIndex)) null
                        else cursor.getInt(heartRateIndex).takeIf { it > 0 }?.toDouble(),
                    speedMetersPerSecond =
                        if (cursor.isNull(speedIndex)) null
                        else cursor.getDouble(speedIndex).takeIf { it.isFinite() && it >= 0.0 },
                    cadencePerMinute =
                        if (cursor.isNull(cadenceIndex)) null
                        else cursor.getDouble(cadenceIndex).takeIf { it.isFinite() && it > 0.0 },
                    elevationMeters =
                        if (cursor.isNull(altitudeIndex)) null
                        else cursor.getDouble(altitudeIndex).takeIf { it.isFinite() },
                    segment = segment,
                    timingSegment = timingSegment)
          }
          previousLatitude = latitude
          previousLongitude = longitude
          previousTime = time
        }
      }
  return points
}

internal fun readActivityRecoverySummary(
    database: SQLiteDatabase,
    activityId: Long
): ActivityRecoverySummary? {
  var elapsedMillis: Double? = null
  var distanceMeters: Double? = null
  database
      .query(
          DB.LOCATION.TABLE,
          arrayOf(DB.LOCATION.ELAPSED, DB.LOCATION.DISTANCE),
          "${DB.LOCATION.ACTIVITY} = ?",
          arrayOf(activityId.toString()),
          null,
          null,
          ActivityRouteOrder.ascending(database, activityId))
      .use { cursor ->
        val elapsedIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.ELAPSED)
        val distanceIndex = cursor.getColumnIndexOrThrow(DB.LOCATION.DISTANCE)
        while (cursor.moveToNext()) {
          if (!cursor.isNull(elapsedIndex)) {
            cursor.getDouble(elapsedIndex).takeIf { it.isFinite() && it >= 0.0 }?.let {
              elapsedMillis = it
            }
          }
          if (!cursor.isNull(distanceIndex)) {
            cursor.getDouble(distanceIndex).takeIf { it.isFinite() && it >= 0.0 }?.let {
              distanceMeters = it
            }
          }
        }
      }
  if (elapsedMillis == null && distanceMeters == null) return null
  return ActivityRecoverySummary(
      durationSeconds = elapsedMillis?.let { kotlin.math.round(it / 1000.0).toLong() },
      distanceMeters = distanceMeters?.takeIf { it > 0.0 })
}

/** Finalizes only an interrupted row, using the last durable Tracker checkpoint. */
internal fun saveInterruptedActivity(
    database: SQLiteDatabase,
    activityId: Long,
    note: String?): Boolean {
  if (activityId <= 0L) return false
  val summary = readActivityRecoverySummary(database, activityId) ?: return false
  val values = android.content.ContentValues().apply {
    put(DB.ACTIVITY.TIME, summary.durationSeconds ?: 0L)
    summary.distanceMeters?.let { put(DB.ACTIVITY.DISTANCE, it) }
    put(DB.ACTIVITY.COMMENT, note?.trim()?.takeIf(String::isNotEmpty))
  }
  return database.update(
      DB.ACTIVITY.TABLE,
      values,
      "_id = ? AND ${DB.ACTIVITY.TIME} IS NULL AND ${DB.ACTIVITY.DELETED} = 0",
      arrayOf(activityId.toString())) == 1
}

/** Soft-deletes only an interrupted row. Repeated discard callbacks are harmless. */
internal fun discardInterruptedActivity(database: SQLiteDatabase, activityId: Long): Boolean {
  if (activityId <= 0L) return false
  val values = android.content.ContentValues().apply { put(DB.ACTIVITY.DELETED, 1) }
  return database.update(
      DB.ACTIVITY.TABLE,
      values,
      "_id = ? AND ${DB.ACTIVITY.TIME} IS NULL AND ${DB.ACTIVITY.DELETED} = 0",
      arrayOf(activityId.toString())) == 1
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
          DB.ACTIVITY.MAX_HR,
          DB.ACTIVITY.AVG_CADENCE)
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
  val activity = database
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
        fun doubleValue(column: String): Double? {
          val index = cursor.getColumnIndexOrThrow(column)
          return if (cursor.isNull(index)) null else cursor.getDouble(index)
        }
        val distanceIndex = cursor.getColumnIndexOrThrow(DB.ACTIVITY.DISTANCE)
        val durationIndex = cursor.getColumnIndexOrThrow(DB.ACTIVITY.TIME)
        val commentIndex = cursor.getColumnIndexOrThrow(DB.ACTIVITY.COMMENT)
        val unfinished = cursor.isNull(durationIndex)
        val recovery = if (unfinished) readActivityRecoverySummary(database, activityId) else null
        HistoryActivityDetails(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
            startedAtSeconds = startedAt,
            durationSeconds = longValue(DB.ACTIVITY.TIME) ?: recovery?.durationSeconds,
            distanceMeters =
                if (cursor.isNull(distanceIndex)) recovery?.distanceMeters
                else cursor.getDouble(distanceIndex).takeIf { it.isFinite() && it > 0.0 },
            sport = cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.SPORT)),
            gpsSampleCount = gpsSampleCount,
            comment = if (cursor.isNull(commentIndex)) null else cursor.getString(commentIndex),
            averageHeartRate = intValue(DB.ACTIVITY.AVG_HR),
            maximumHeartRate = intValue(DB.ACTIVITY.MAX_HR),
            averageCadence =
                doubleValue(DB.ACTIVITY.AVG_CADENCE)?.takeIf { it.isFinite() && it > 0.0 },
            unfinished = unfinished)
      }
  return activity?.copy(
      laps = readActivityLaps(database, activityId),
      route = if (gpsSampleCount >= 2) readActivityRoute(database, activityId) else emptyList())
}

internal fun readActivityLaps(database: SQLiteDatabase, activityId: Long): List<ActivityLapDetails> {
  val columns =
      arrayOf(
          DB.LAP.LAP,
          DB.LAP.INTENSITY,
          DB.LAP.TIME,
          DB.LAP.DISTANCE,
          DB.LAP.PLANNED_TIME,
          DB.LAP.PLANNED_DISTANCE,
          DB.LAP.PLANNED_PACE,
          DB.LAP.AVG_HR)
  return database
      .query(
          DB.LAP.TABLE,
          columns,
          "${DB.LAP.ACTIVITY} = ?",
          arrayOf(activityId.toString()),
          null,
          null,
          DB.LAP.LAP + " ASC")
      .use { cursor ->
        val numberIndex = cursor.getColumnIndexOrThrow(DB.LAP.LAP)
        val intensityIndex = cursor.getColumnIndexOrThrow(DB.LAP.INTENSITY)
        val durationIndex = cursor.getColumnIndexOrThrow(DB.LAP.TIME)
        val distanceIndex = cursor.getColumnIndexOrThrow(DB.LAP.DISTANCE)
        val plannedDurationIndex = cursor.getColumnIndexOrThrow(DB.LAP.PLANNED_TIME)
        val plannedDistanceIndex = cursor.getColumnIndexOrThrow(DB.LAP.PLANNED_DISTANCE)
        val plannedPaceIndex = cursor.getColumnIndexOrThrow(DB.LAP.PLANNED_PACE)
        val heartRateIndex = cursor.getColumnIndexOrThrow(DB.LAP.AVG_HR)
        buildList {
          while (cursor.moveToNext()) {
            val distance =
                if (cursor.isNull(distanceIndex)) null else cursor.getDouble(distanceIndex)
            val duration =
                if (cursor.isNull(durationIndex)) null else cursor.getLong(durationIndex)
            val heartRate =
                if (cursor.isNull(heartRateIndex)) null else cursor.getInt(heartRateIndex)
            add(
                ActivityLapDetails(
                    number = cursor.getInt(numberIndex).coerceAtLeast(0) + 1,
                    intensity = cursor.getInt(intensityIndex),
                    durationSeconds = duration?.takeIf { it >= 0L },
                    distanceMeters = distance?.takeIf { it.isFinite() && it >= 0.0 },
                    averageHeartRate = heartRate?.takeIf { it > 0 },
                    plannedDurationSeconds =
                        if (cursor.isNull(plannedDurationIndex)) null
                        else cursor.getLong(plannedDurationIndex).takeIf { it >= 0L },
                    plannedDistanceMeters =
                        if (cursor.isNull(plannedDistanceIndex)) null
                        else cursor.getDouble(plannedDistanceIndex).takeIf { it.isFinite() && it > 0.0 },
                    plannedPace =
                        if (cursor.isNull(plannedPaceIndex)) null
                        else cursor.getDouble(plannedPaceIndex).takeIf { it.isFinite() && it > 0.0 }))
          }
        }
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
