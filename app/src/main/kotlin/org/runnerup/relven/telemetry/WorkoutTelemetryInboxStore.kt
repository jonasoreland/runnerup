package org.runnerup.relven.telemetry

import android.database.sqlite.SQLiteDatabase
import org.runnerup.common.wear.WorkoutTelemetryProtocol

/** Durable phone receipt ledger for watch telemetry; it does not mutate activity routes. */
class WorkoutTelemetryInboxStore(private val database: SQLiteDatabase) {
  enum class AssociationResult {
    ASSOCIATED,
    DUPLICATE,
    CONFLICT,
    INVALID
  }

  enum class CommitStatus {
    COMMITTED,
    DUPLICATE,
    UNKNOWN_SESSION,
    GAP,
    CONFLICT,
    INVALID_TIME,
    STORAGE_ERROR
  }

  data class CommitResult(val status: CommitStatus, val committedThroughSequence: Long) {
    val shouldAcknowledge: Boolean
      get() = status == CommitStatus.COMMITTED || status == CommitStatus.DUPLICATE
  }

  fun ensureSchema() = ensureSchema(database)

  fun associateActivity(
      sessionId: String,
      activityId: Long,
      activityStartTimeMillis: Long
  ): AssociationResult {
    if (sessionId.isBlank() || activityId <= 0 || activityStartTimeMillis <= 0) {
      return AssociationResult.INVALID
    }
    return try {
      database.beginTransaction()
      val existing =
          database.query(
              SESSION_TABLE,
              arrayOf(ACTIVITY_ID, ACTIVITY_START_TIME),
              "$SESSION_ID = ?",
              arrayOf(sessionId),
              null,
              null,
              null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
              }
      val result =
          when {
            existing == null -> {
              val values = android.content.ContentValues().apply {
                put(SESSION_ID, sessionId)
                put(ACTIVITY_ID, activityId)
                put(ACTIVITY_START_TIME, activityStartTimeMillis)
                put(COMMITTED_THROUGH, 0L)
              }
              database.insertOrThrow(SESSION_TABLE, null, values)
              AssociationResult.ASSOCIATED
            }
            existing.first == activityId && existing.second == activityStartTimeMillis ->
              AssociationResult.DUPLICATE
            else -> AssociationResult.CONFLICT
          }
      database.setTransactionSuccessful()
      result
    } catch (_: RuntimeException) {
      AssociationResult.INVALID
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  fun commitBatch(batch: WorkoutTelemetryProtocol.Batch, nowMillis: Long): CommitResult {
    var committedThrough = 0L
    return try {
      database.beginTransaction()
      val association =
          database.query(
              SESSION_TABLE,
              arrayOf(ACTIVITY_START_TIME, COMMITTED_THROUGH),
              "$SESSION_ID = ?",
              arrayOf(batch.sessionId),
              null,
              null,
              null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
              }
      if (association == null) {
        return CommitResult(CommitStatus.UNKNOWN_SESSION, 0)
      }
      val sessionStart = association.first
      committedThrough = association.second
      var appended = false
      for (sample in batch.samples) {
        if (sample.wallTimeMillis < sessionStart - PRE_SESSION_TOLERANCE_MILLIS ||
            sample.wallTimeMillis > nowMillis + FUTURE_TOLERANCE_MILLIS) {
          return CommitResult(CommitStatus.INVALID_TIME, committedThrough)
        }
        val encoded = encodeSample(sample)
        if (sample.sequence <= committedThrough) {
          val stored = readSample(sample.sessionId, sample.sequence)
              ?: return CommitResult(CommitStatus.CONFLICT, committedThrough)
          if (!stored.contentEquals(encoded)) {
            return CommitResult(CommitStatus.CONFLICT, committedThrough)
          }
        } else {
          if (sample.sequence != committedThrough + 1L) {
            return CommitResult(CommitStatus.GAP, committedThrough)
          }
          insertSample(sample.sessionId, sample.sequence, encoded)
          committedThrough = sample.sequence
          appended = true
        }
      }
      if (appended) updateCursor(batch.sessionId, committedThrough)
      database.setTransactionSuccessful()
      CommitResult(
          if (appended) CommitStatus.COMMITTED else CommitStatus.DUPLICATE,
          committedThrough)
    } catch (_: Exception) {
      CommitResult(CommitStatus.STORAGE_ERROR, committedThrough)
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  fun sampleCount(sessionId: String): Int =
      database.query(
          SAMPLE_TABLE,
          arrayOf("COUNT(*)"),
          "$SESSION_ID = ?",
          arrayOf(sessionId),
          null,
          null,
          null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

  fun containsIdenticalSample(sample: WorkoutTelemetryProtocol.Sample): Boolean {
    val stored = readSample(sample.sessionId, sample.sequence) ?: return false
    return runCatching { stored.contentEquals(encodeSample(sample)) }.getOrDefault(false)
  }

  private fun readSample(sessionId: String, sequence: Long): ByteArray? =
      database.query(
          SAMPLE_TABLE,
          arrayOf(PAYLOAD),
          "$SESSION_ID = ? AND $SEQUENCE = ?",
          arrayOf(sessionId, sequence.toString()),
          null,
          null,
          null).use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }

  private fun insertSample(sessionId: String, sequence: Long, payload: ByteArray) {
    val values = android.content.ContentValues().apply {
      put(SESSION_ID, sessionId)
      put(SEQUENCE, sequence)
      put(PAYLOAD, payload)
    }
    database.insertOrThrow(SAMPLE_TABLE, null, values)
  }

  private fun updateCursor(sessionId: String, sequence: Long) {
    val values = android.content.ContentValues().apply { put(COMMITTED_THROUGH, sequence) }
    check(database.update(SESSION_TABLE, values, "$SESSION_ID = ?", arrayOf(sessionId)) == 1)
  }

  private fun encodeSample(sample: WorkoutTelemetryProtocol.Sample): ByteArray =
      WorkoutTelemetryProtocol.encodeBatch(
          WorkoutTelemetryProtocol.Batch(sample.sessionId, listOf(sample)))

  companion object {
    private const val SESSION_TABLE = "relven_wear_telemetry_session"
    private const val SAMPLE_TABLE = "relven_wear_telemetry_sample"
    private const val SESSION_ID = "session_id"
    private const val ACTIVITY_ID = "activity_id"
    private const val ACTIVITY_START_TIME = "activity_start_time_millis"
    private const val COMMITTED_THROUGH = "committed_through_sequence"
    private const val SEQUENCE = "sequence"
    private const val PAYLOAD = "payload"
    private const val PRE_SESSION_TOLERANCE_MILLIS = 120_000L
    private const val FUTURE_TOLERANCE_MILLIS = 60_000L

    @JvmStatic
    fun ensureSchema(database: SQLiteDatabase) {
      database.execSQL(
          "CREATE TABLE IF NOT EXISTS $SESSION_TABLE (" +
              "$SESSION_ID TEXT PRIMARY KEY NOT NULL, " +
              "$ACTIVITY_ID INTEGER NOT NULL, " +
              "$ACTIVITY_START_TIME INTEGER NOT NULL, " +
              "$COMMITTED_THROUGH INTEGER NOT NULL DEFAULT 0)")
      database.execSQL(
          "CREATE TABLE IF NOT EXISTS $SAMPLE_TABLE (" +
              "$SESSION_ID TEXT NOT NULL, " +
              "$SEQUENCE INTEGER NOT NULL, " +
              "$PAYLOAD BLOB NOT NULL, " +
              "PRIMARY KEY ($SESSION_ID, $SEQUENCE))")
    }
  }
}
