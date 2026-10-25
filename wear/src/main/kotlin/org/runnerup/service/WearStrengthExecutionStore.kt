package org.runnerup.service

import android.content.Context
import android.content.SharedPreferences
import android.annotation.SuppressLint
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionCodec

/** Synchronous single-session persistence; commits are durable before a projection is sent. */
@SuppressLint("ApplySharedPref", "UseKtx")
class WearStrengthExecutionStore internal constructor(
    private val storage: Storage,
    private val elapsedRealtimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
  constructor(context: Context) : this(SharedPreferenceStorage(context.applicationContext))

  enum class CommitResult { COMMITTED, DUPLICATE, STALE, CONFLICT, STORAGE_ERROR }

  @Synchronized
  fun savePreparedSession(session: StrengthSession): Boolean {
    if (session.phase != StrengthSession.Phase.PREPARING || session.authorityRevision != 0L) return false
    val current = load()
    if (current != null) {
      if (current.executionId != session.executionId) {
        val acknowledgedComplete =
            current.phase == StrengthSession.Phase.COMPLETE &&
                current.authorityRevision <= storage.readAcknowledgedRevision()
        if (!acknowledgedComplete &&
            (current.phase != StrengthSession.Phase.PREPARING || current.results.isNotEmpty())) return false
        return storage.write(StrengthSessionCodec.encode(session, null), 0L)
      }
      if (current == session) return true
      return false
    }
    return storage.write(StrengthSessionCodec.encode(session, null), 0L)
  }

  @Synchronized
  fun load(): StrengthSession? = storage.readState()?.let { json ->
    runCatching { StrengthSessionCodec.decode(json).first }.getOrNull()
  }

  @Synchronized
  fun commit(next: StrengthSession): CommitResult {
    val current = load() ?: return CommitResult.CONFLICT
    if (next.executionId != current.executionId) return CommitResult.CONFLICT
    if (next == current) return CommitResult.DUPLICATE
    if (next.authorityRevision <= current.authorityRevision) return CommitResult.STALE
    if (current.authorityRevision == Long.MAX_VALUE ||
        next.authorityRevision != current.authorityRevision + 1L ||
        next.lastAuthorityCommandId == null) return CommitResult.CONFLICT
    return if (storage.write(StrengthSessionCodec.encode(next, null), storage.readAcknowledgedRevision())) {
      CommitResult.COMMITTED
    } else {
      CommitResult.STORAGE_ERROR
    }
  }

  @Synchronized
  fun pendingProjection(): ByteArray? {
    val session = load() ?: return null
    if (session.authorityRevision <= storage.readAcknowledgedRevision()) return null
    val requestId = session.lastAuthorityCommandId ?: return null
    return runCatching {
      StrengthSessionAuthorityProtocol.encodeProjection(
          session, requestId, elapsedRealtimeMillis().coerceAtLeast(1L))
    }.getOrNull()
  }

  @Synchronized
  fun acknowledge(ack: StrengthSessionAuthorityProtocol.Acknowledgement): Boolean {
    val session = load() ?: return false
    if (ack.sessionId != session.executionId || ack.revision != session.authorityRevision ||
        ack.requestId != session.lastAuthorityCommandId ||
        ack.outcome !in setOf(
            StrengthSessionAuthorityProtocol.Outcome.COMMITTED,
            StrengthSessionAuthorityProtocol.Outcome.DUPLICATE)) return false
    if (ack.revision <= storage.readAcknowledgedRevision()) return true
    return storage.write(StrengthSessionCodec.encode(session, null), ack.revision)
  }

  interface Storage {
    fun readState(): String?
    fun readAcknowledgedRevision(): Long
    fun write(stateJson: String?, acknowledgedRevision: Long): Boolean
  }

  private class SharedPreferenceStorage(context: Context) : Storage {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun readState(): String? = preferences.getString(STATE, null)
    override fun readAcknowledgedRevision(): Long = preferences.getLong(ACKNOWLEDGED_REVISION, 0L)
    override fun write(stateJson: String?, acknowledgedRevision: Long): Boolean =
        preferences.edit().putString(STATE, stateJson).putLong(ACKNOWLEDGED_REVISION, acknowledgedRevision).commit()
  }

  companion object {
    const val PREFERENCES_NAME = "wear_strength_execution"
    private const val PREFERENCES = PREFERENCES_NAME
    const val STATE_KEY = "active_session_json"
    private const val STATE = STATE_KEY
    private const val ACKNOWLEDGED_REVISION = "acknowledged_revision"
  }
}
