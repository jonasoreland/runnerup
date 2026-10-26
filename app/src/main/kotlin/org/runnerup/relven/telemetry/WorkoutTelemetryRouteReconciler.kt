package org.runnerup.relven.telemetry

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import org.runnerup.common.util.Constants.DB
import org.runnerup.common.wear.WorkoutTelemetryProtocol
import kotlin.math.roundToInt

/**
 * Materializes committed watch telemetry into the existing RunnerUp activity route.
 * RunnerUp's persisted activity totals remain authoritative; partial watch routes never become
 * workout totals, and cross-device wall clocks are not promoted to elapsed-time checkpoints.
 */
class WorkoutTelemetryRouteReconciler(private val database: SQLiteDatabase) {
  data class Result(
      val success: Boolean,
      val insertedLocations: Int = 0,
      val enrichedPhoneLocations: Int = 0,
      val coveredByPhone: Int = 0,
      val ignoredSamples: Int = 0,
      val updatedActivityIds: Set<Long> = emptySet())

  private data class PendingSample(
      val sessionId: String,
      val sequence: Long,
      val activityId: Long,
      val activityStartMillis: Long,
      val encoded: ByteArray)

  private data class Neighbor(
      val id: Long,
      val timeMillis: Long,
      val latitude: Double,
      val longitude: Double,
      val distanceMeters: Double?,
      val elapsedMillis: Double?)

  private enum class Disposition(val value: Int) {
    INSERTED(1),
    PHONE_COVERED(2),
    PHONE_ENRICHED(3),
    NO_ROUTE_TARGET(4),
    LOW_ACCURACY(5),
    PAUSED(6),
    OUTSIDE_ACTIVITY(7)
  }

  fun reconcilePending(sessionId: String? = null): Result {
    ensureSchema(database)
    var inserted = 0
    var enriched = 0
    var covered = 0
    var ignored = 0
    val updatedActivities = linkedSetOf<Long>()
    return try {
      pendingSamples(sessionId).forEach { pending ->
        val batch = WorkoutTelemetryProtocol.decodeBatch(pending.encoded)
        if (batch.samples.size != 1 || batch.samples.single().sequence != pending.sequence) {
          return Result(false, inserted, enriched, covered, ignored, updatedActivities)
        }
        val disposition = reconcileOne(pending, batch.samples.single())
            ?: return Result(false, inserted, enriched, covered, ignored, updatedActivities)
        when (disposition) {
          Disposition.INSERTED -> inserted++
          Disposition.PHONE_ENRICHED -> {
            enriched++
            updatedActivities += pending.activityId
          }
          Disposition.PHONE_COVERED -> covered++
          else -> ignored++
        }
        if (disposition == Disposition.INSERTED) updatedActivities += pending.activityId
      }
      Result(true, inserted, enriched, covered, ignored, updatedActivities)
    } catch (_: Exception) {
      Result(false, inserted, enriched, covered, ignored, updatedActivities)
    }
  }

  private fun pendingSamples(sessionId: String?): List<PendingSample> {
    val selection = if (sessionId == null) null else "s.$SESSION_ID = ?"
    val args = if (sessionId == null) null else arrayOf(sessionId)
    return database.rawQuery(
        "SELECT s.$SESSION_ID, p.$SEQUENCE, s.$ACTIVITY_ID, s.$ACTIVITY_START, p.$PAYLOAD " +
            "FROM $SAMPLE_TABLE p JOIN $SESSION_TABLE s ON s.$SESSION_ID = p.$SESSION_ID " +
            "LEFT JOIN $ROUTE_TABLE r ON r.$SESSION_ID = p.$SESSION_ID AND r.$SEQUENCE = p.$SEQUENCE " +
            "WHERE r.$SESSION_ID IS NULL" +
            (if (selection == null) "" else " AND $selection") +
            " ORDER BY s.$SESSION_ID ASC, p.$SEQUENCE ASC",
        args).use { cursor ->
      val result = mutableListOf<PendingSample>()
      while (cursor.moveToNext()) {
        result += PendingSample(
            cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3), cursor.getBlob(4))
      }
      result
    }
  }

  private fun reconcileOne(
      pending: PendingSample,
      sample: WorkoutTelemetryProtocol.Sample
  ): Disposition? {
    if (sample.wallTimeMillis < pending.activityStartMillis) {
      return record(pending, Disposition.OUTSIDE_ACTIVITY, null)
    }
    if (!activityExists(pending.activityId)) return record(pending, Disposition.OUTSIDE_ACTIVITY, null)
    if (!isActivityActiveAt(pending.activityId, sample.wallTimeMillis)) {
      return record(pending, Disposition.PAUSED, null)
    }

    val nearestPhonePoint = nearestPhonePoint(pending.activityId, sample.wallTimeMillis)
    if (nearestPhonePoint != null) {
      val heartRate = sample.heartRateBpm?.roundToInt()?.takeIf { it in MIN_HEART_RATE..MAX_HEART_RATE }
      return recordPhoneCoverage(pending, nearestPhonePoint.id, heartRate)
    }

    if (sample.latitude == null || sample.longitude == null) {
      return record(pending, Disposition.NO_ROUTE_TARGET, null)
    }
    val accuracy = sample.horizontalAccuracyMeters
    if (accuracy == null || accuracy > MAX_ROUTE_ACCURACY_METERS) {
      return record(pending, Disposition.LOW_ACCURACY, null)
    }

    val content = ContentValues().apply {
      put(DB.LOCATION.ACTIVITY, pending.activityId)
      put(DB.LOCATION.LAP, nearestLap(pending.activityId, sample.wallTimeMillis))
      put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS)
      put(DB.LOCATION.TIME, sample.wallTimeMillis)
      put(DB.LOCATION.LATITUDE, sample.latitude)
      put(DB.LOCATION.LONGITUDE, sample.longitude)
      put(DB.LOCATION.ACCURANCY, accuracy)
      interpolatedElapsed(pending.activityId, sample.wallTimeMillis)?.let {
        put(DB.LOCATION.ELAPSED, it)
      }
      interpolatedDistance(pending.activityId, sample)?.let { put(DB.LOCATION.DISTANCE, it) }
      sample.speedMetersPerSecond?.takeIf { it <= MAX_RECORDED_SPEED_METERS_PER_SECOND }?.let {
        put(DB.LOCATION.SPEED, it)
      }
      sample.heartRateBpm?.roundToInt()?.takeIf { it in MIN_HEART_RATE..MAX_HEART_RATE }?.let {
        put(DB.LOCATION.HR, it)
      }
      // The protocol has no vertical accuracy, so watch altitude is deliberately not promoted
      // into RunnerUp's elevation fields.
    }

    database.beginTransaction()
    return try {
      val id = database.insertOrThrow(DB.LOCATION.TABLE, null, content)
      insertReceipt(pending, Disposition.INSERTED, id)
      database.setTransactionSuccessful()
      Disposition.INSERTED
    } catch (_: Exception) {
      null
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  private fun activityExists(activityId: Long): Boolean =
      database.query(
          DB.ACTIVITY.TABLE,
          arrayOf(DB.PRIMARY_KEY, DB.ACTIVITY.DELETED),
          "_id = ?",
          arrayOf(activityId.toString()),
          null,
          null,
          null,
          "1").use { cursor -> cursor.moveToFirst() && cursor.getInt(1) == 0 }

  private fun isActivityActiveAt(activityId: Long, timeMillis: Long): Boolean {
    var active = true
    database.query(
        DB.LOCATION.TABLE,
        arrayOf(DB.LOCATION.TYPE),
        "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TIME} <= ? AND ${DB.LOCATION.TYPE} IN (?, ?, ?, ?)",
        arrayOf(activityId.toString(), timeMillis.toString(), DB.LOCATION.TYPE_START.toString(),
            DB.LOCATION.TYPE_PAUSE.toString(), DB.LOCATION.TYPE_RESUME.toString(), DB.LOCATION.TYPE_END.toString()),
        null, null, "${DB.LOCATION.TIME} ASC, _id ASC").use { cursor ->
      while (cursor.moveToNext()) {
        when (cursor.getInt(0)) {
          DB.LOCATION.TYPE_START, DB.LOCATION.TYPE_RESUME -> active = true
          DB.LOCATION.TYPE_PAUSE, DB.LOCATION.TYPE_END -> active = false
        }
      }
    }
    return active
  }

  private fun nearestPhonePoint(activityId: Long, timeMillis: Long): Neighbor? =
      database.rawQuery(
          "SELECT l._id, l.${DB.LOCATION.TIME}, l.${DB.LOCATION.LATITUDE}, l.${DB.LOCATION.LONGITUDE}, " +
              "l.${DB.LOCATION.DISTANCE}, l.${DB.LOCATION.ELAPSED} " +
              "FROM ${DB.LOCATION.TABLE} l WHERE l.${DB.LOCATION.ACTIVITY} = ? " +
              "AND l.${DB.LOCATION.TYPE} = ? AND ABS(l.${DB.LOCATION.TIME} - ?) <= CAST(? AS INTEGER) " +
              "AND l._id NOT IN (SELECT $LOCATION_ID FROM $ROUTE_TABLE WHERE $LOCATION_ID IS NOT NULL) " +
              "ORDER BY ABS(l.${DB.LOCATION.TIME} - ?) ASC, l._id ASC LIMIT 1",
          arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString(), timeMillis.toString(),
              PHONE_COVERAGE_WINDOW_MILLIS.toString(), timeMillis.toString())).use { cursor ->
        if (!cursor.moveToFirst()) null
        else Neighbor(
            cursor.getLong(0), cursor.getLong(1), cursor.getDouble(2), cursor.getDouble(3),
            if (cursor.isNull(4)) null else cursor.getDouble(4),
            if (cursor.isNull(5)) null else cursor.getDouble(5))
      }

  private fun enrichPhoneHeartRate(locationId: Long, heartRate: Int): Boolean {
    val values = ContentValues().apply { put(DB.LOCATION.HR, heartRate) }
    return database.update(DB.LOCATION.TABLE, values,
        "_id = ? AND ${DB.LOCATION.HR} IS NULL", arrayOf(locationId.toString())) == 1
  }

  private fun nearestLap(activityId: Long, timeMillis: Long): Int =
      database.rawQuery(
          "SELECT ${DB.LOCATION.LAP} FROM ${DB.LOCATION.TABLE} WHERE ${DB.LOCATION.ACTIVITY} = ? " +
              "AND ${DB.LOCATION.TYPE} = ? ORDER BY ABS(${DB.LOCATION.TIME} - ?) ASC, _id ASC LIMIT 1",
          arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString(), timeMillis.toString())).use {
        if (it.moveToFirst()) it.getInt(0) else 0
      }

  private fun interpolatedElapsed(activityId: Long, sampleTime: Long): Double? {
    // A watch monotonic clock cannot be subtracted from the phone's clock. Interpolate only when
    // the sample is bracketed by persisted phone checkpoints in the same active segment.
    val before = routeNeighborWithElapsed(activityId, sampleTime, before = true) ?: return null
    val after = routeNeighborWithElapsed(activityId, sampleTime, before = false) ?: return null
    val beforeElapsed = before.elapsedMillis ?: return null
    val afterElapsed = after.elapsedMillis ?: return null
    if (before.timeMillis >= after.timeMillis || beforeElapsed > afterElapsed ||
        sampleTime !in before.timeMillis..after.timeMillis) return null
    if (hasSessionBoundaryBetween(activityId, before.timeMillis, after.timeMillis)) return null
    val fraction = (sampleTime - before.timeMillis).toDouble() / (after.timeMillis - before.timeMillis)
    return beforeElapsed + (afterElapsed - beforeElapsed) * fraction
  }

  private fun routeNeighborWithElapsed(activityId: Long, timeMillis: Long, before: Boolean): Neighbor? {
    val comparator = if (before) "<=" else ">="
    val direction = if (before) "DESC" else "ASC"
    return database.rawQuery(
        "SELECT _id, " + DB.LOCATION.TIME + ", " + DB.LOCATION.LATITUDE + ", " +
            DB.LOCATION.LONGITUDE + ", " + DB.LOCATION.DISTANCE + ", " + DB.LOCATION.ELAPSED +
            " FROM " + DB.LOCATION.TABLE + " WHERE " + DB.LOCATION.ACTIVITY + " = ? AND " +
            DB.LOCATION.TYPE + " = ? AND " + DB.LOCATION.ELAPSED + " IS NOT NULL AND " +
            DB.LOCATION.TIME + " " + comparator + " ? ORDER BY " + DB.LOCATION.TIME + " " +
            direction + ", _id " + direction + " LIMIT 1",
        arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString(), timeMillis.toString())).use {
      if (!it.moveToFirst()) null
      else Neighbor(it.getLong(0), it.getLong(1), it.getDouble(2), it.getDouble(3),
          if (it.isNull(4)) null else it.getDouble(4), if (it.isNull(5)) null else it.getDouble(5))
    }
  }

  private fun interpolatedDistance(activityId: Long, sample: WorkoutTelemetryProtocol.Sample): Double? {
    val latitude = sample.latitude ?: return null
    val longitude = sample.longitude ?: return null
    val before = routeNeighbor(activityId, sample.wallTimeMillis, before = true)
    val after = routeNeighbor(activityId, sample.wallTimeMillis, before = false)
    val segmentStart = before?.timeMillis ?: sample.wallTimeMillis
    val segmentEnd = after?.timeMillis ?: sample.wallTimeMillis
    if (hasSessionBoundaryBetween(activityId, segmentStart, segmentEnd)) return null
    val fromBefore = before?.let { distanceMeters(it.latitude, it.longitude, latitude, longitude) }
    val toAfter = after?.let { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
    val beforeDistance = before?.distanceMeters
    val afterDistance = after?.distanceMeters
    return when {
      beforeDistance != null && afterDistance != null && afterDistance >= beforeDistance -> {
        val geometry = (fromBefore ?: 0.0) + (toAfter ?: 0.0)
        val fraction = if (geometry > 0.0) (fromBefore ?: 0.0) / geometry else 0.5
        beforeDistance + (afterDistance - beforeDistance) * fraction.coerceIn(0.0, 1.0)
      }
      beforeDistance != null -> beforeDistance + (fromBefore ?: 0.0)
      afterDistance != null -> (afterDistance - (toAfter ?: 0.0)).coerceAtLeast(0.0)
      else -> null
    }
  }

  private fun hasSessionBoundaryBetween(activityId: Long, startTime: Long, endTime: Long): Boolean {
    if (startTime > endTime) return true
    return database.query(
        DB.LOCATION.TABLE,
        arrayOf("_id"),
        DB.LOCATION.ACTIVITY + " = ? AND " + DB.LOCATION.TIME + " >= ? AND " +
            DB.LOCATION.TIME + " <= ? AND " + DB.LOCATION.TYPE + " IN (?, ?, ?, ?)",
        arrayOf(activityId.toString(), startTime.toString(), endTime.toString(),
            DB.LOCATION.TYPE_START.toString(), DB.LOCATION.TYPE_PAUSE.toString(),
            DB.LOCATION.TYPE_RESUME.toString(), DB.LOCATION.TYPE_END.toString()),
        null, null, null, "1").use { it.moveToFirst() }
  }

  private fun routeNeighbor(activityId: Long, timeMillis: Long, before: Boolean): Neighbor? {
    val comparator = if (before) "<=" else ">="
    val direction = if (before) "DESC" else "ASC"
    return database.rawQuery(
        "SELECT _id, ${DB.LOCATION.TIME}, ${DB.LOCATION.LATITUDE}, ${DB.LOCATION.LONGITUDE}, " +
            "${DB.LOCATION.DISTANCE}, ${DB.LOCATION.ELAPSED} FROM ${DB.LOCATION.TABLE} " +
            "WHERE ${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ? " +
            "AND ${DB.LOCATION.TIME} $comparator ? ORDER BY ${DB.LOCATION.TIME} $direction, _id $direction LIMIT 1",
        arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString(), timeMillis.toString())).use {
      if (!it.moveToFirst()) null
      else Neighbor(it.getLong(0), it.getLong(1), it.getDouble(2), it.getDouble(3),
          if (it.isNull(4)) null else it.getDouble(4), if (it.isNull(5)) null else it.getDouble(5))
    }
  }

  private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val distance = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, distance)
    return distance[0].toDouble()
  }

  private fun record(pending: PendingSample, disposition: Disposition, locationId: Long?): Disposition? {
    database.beginTransaction()
    return try {
      insertReceipt(pending, disposition, locationId)
      database.setTransactionSuccessful()
      disposition
    } catch (_: Exception) {
      null
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  private fun recordPhoneCoverage(
      pending: PendingSample,
      locationId: Long,
      heartRate: Int?
  ): Disposition? {
    database.beginTransaction()
    return try {
      val enriched = heartRate != null && enrichPhoneHeartRate(locationId, heartRate)
      val disposition = if (enriched) Disposition.PHONE_ENRICHED else Disposition.PHONE_COVERED
      insertReceipt(pending, disposition, locationId)
      database.setTransactionSuccessful()
      disposition
    } catch (_: Exception) {
      null
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  private fun insertReceipt(pending: PendingSample, disposition: Disposition, locationId: Long?) {
    val values = ContentValues().apply {
      put(SESSION_ID, pending.sessionId)
      put(SEQUENCE, pending.sequence)
      put(DISPOSITION, disposition.value)
      if (locationId == null) putNull(LOCATION_ID) else put(LOCATION_ID, locationId)
    }
    database.insertOrThrow(ROUTE_TABLE, null, values)
  }

  companion object {
    private const val SESSION_TABLE = "relven_wear_telemetry_session"
    private const val SAMPLE_TABLE = "relven_wear_telemetry_sample"
    private const val ROUTE_TABLE = "relven_wear_telemetry_route_receipt"
    private const val SESSION_ID = "session_id"
    private const val SEQUENCE = "sequence"
    private const val ACTIVITY_ID = "activity_id"
    private const val ACTIVITY_START = "activity_start_time_millis"
    private const val PAYLOAD = "payload"
    private const val DISPOSITION = "disposition"
    private const val LOCATION_ID = "location_id"
    private const val PHONE_COVERAGE_WINDOW_MILLIS = 2_000L
    private const val MAX_ROUTE_ACCURACY_METERS = 50.0
    private const val MAX_RECORDED_SPEED_METERS_PER_SECOND = 15.0
    private const val MIN_HEART_RATE = 20
    private const val MAX_HEART_RATE = 250

    @JvmStatic
    fun ensureSchema(database: SQLiteDatabase) {
      database.execSQL(
          "CREATE TABLE IF NOT EXISTS $ROUTE_TABLE (" +
              "$SESSION_ID TEXT NOT NULL, $SEQUENCE INTEGER NOT NULL, $DISPOSITION INTEGER NOT NULL, " +
              "$LOCATION_ID INTEGER, PRIMARY KEY ($SESSION_ID, $SEQUENCE))")
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS relven_wear_telemetry_route_location_idx " +
              "ON $ROUTE_TABLE($LOCATION_ID)")
    }

  }
}
