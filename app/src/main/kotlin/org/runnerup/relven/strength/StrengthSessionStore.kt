package org.runnerup.relven.strength

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.system.Os
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser
import org.runnerup.common.wear.StrengthResultProtocol
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class CompletedStrengthSession(val state: StrengthSession, val finishedAtMillis: Long)

internal data class StrengthSessionHistoryLoad(
    val sessions: List<CompletedStrengthSession>,
    val invalidRecordCount: Int,
    val omittedRecordCount: Int = 0)

/** Private, offline persistence for the single resumable strength session and its completions. */
internal object StrengthSessionStore {
  private const val MAX_SAVED_SESSION_BYTES = 16_777_216
  private const val MAX_HISTORY_SCAN = 500
  private const val MAX_HISTORY_READ_BYTES = 33_554_432L

  internal enum class SynchronizedResultApplyResult {
    COMMITTED,
    DUPLICATE,
    CONFLICT,
    SESSION_MISSING,
    INVALID
  }

  internal enum class AuthorityProjectionApplyResult {
    COMMITTED,
    DUPLICATE,
    STALE,
    CONFLICT,
    SESSION_MISSING,
    INVALID
  }

  /** Persists a watch-authoritative control projection before its Data Layer acknowledgement. */
  @Synchronized
  @Throws(IOException::class)
  fun applyAuthoritativeProjection(
      context: Context,
      projection: StrengthSessionAuthorityProtocol.Projection): AuthorityProjectionApplyResult {
    val incoming = try {
      StrengthSessionCodec.decode(projection.sessionJson).first
    } catch (_: RuntimeException) {
      return AuthorityProjectionApplyResult.INVALID
    }
    if (incoming.executionId != projection.sessionId ||
        incoming.authorityRevision != projection.revision ||
        incoming.lastAuthorityCommandId != projection.requestId ||
        incoming.authorityRevision <= 0L) return AuthorityProjectionApplyResult.INVALID
    val current = loadInProgress(context) ?: return AuthorityProjectionApplyResult.SESSION_MISSING
    if (current.executionId != incoming.executionId) return AuthorityProjectionApplyResult.SESSION_MISSING
    if (current.planId != incoming.planId || current.scheduledEntryId != incoming.scheduledEntryId ||
        current.workout.sourceJson() != incoming.workout.sourceJson() ||
        current.results != incoming.results || current.pendingResults != incoming.pendingResults) {
      return AuthorityProjectionApplyResult.CONFLICT
    }
    if (incoming.authorityRevision == current.authorityRevision) {
      val sameSnapshotIgnoringClock =
          incoming == current.copy(
              timerDeadlineElapsedRealtimeMillis = incoming.timerDeadlineElapsedRealtimeMillis,
              timerAnchorElapsedRealtimeMillis = incoming.timerAnchorElapsedRealtimeMillis,
              timerClockObservedElapsedRealtimeMillis = incoming.timerClockObservedElapsedRealtimeMillis)
      return if (sameSnapshotIgnoringClock) AuthorityProjectionApplyResult.DUPLICATE
      else AuthorityProjectionApplyResult.CONFLICT
    }
    if (incoming.authorityRevision < current.authorityRevision) return AuthorityProjectionApplyResult.STALE
    if (current.authorityRevision == Long.MAX_VALUE || incoming.authorityRevision != current.authorityRevision + 1L) {
      return AuthorityProjectionApplyResult.STALE
    }
    val receivedAt = SystemClock.elapsedRealtime()
    val watchDeadline = incoming.timerDeadlineElapsedRealtimeMillis
    val phoneClockProjection =
        if (watchDeadline != null) {
          val remainingMillis =
              (watchDeadline - projection.projectedAtElapsedRealtimeMillis).coerceAtLeast(0L)
          incoming.copy(
              timerDeadlineElapsedRealtimeMillis = receivedAt + remainingMillis,
              timerAnchorElapsedRealtimeMillis = receivedAt,
              timerClockObservedElapsedRealtimeMillis = receivedAt)
        } else {
          incoming.copy(timerClockObservedElapsedRealtimeMillis = receivedAt)
        }
    saveInProgress(context, phoneClockProjection)
    return AuthorityProjectionApplyResult.COMMITTED
  }

  @Synchronized
  @Throws(IOException::class)
  fun applySynchronizedResult(
      context: Context,
      incoming: StrengthResultProtocol.Result): SynchronizedResultApplyResult {
    val active = loadInProgress(context)
    if (active != null && active.executionId == incoming.executionId) {
      return applySynchronizedResult(active, incoming) { updated -> saveInProgress(context, updated) }
    }
    val completion = completedExecution(context, incoming.executionId)
        ?: return SynchronizedResultApplyResult.SESSION_MISSING
    return applySynchronizedResult(completion.state, incoming) { updated ->
      replaceCompleted(
          context,
          completion,
          CompletedStrengthSession(updated, completion.finishedAtMillis))
    }
  }

  private fun applySynchronizedResult(
      state: StrengthSession,
      incoming: StrengthResultProtocol.Result,
      persist: (StrengthSession) -> Unit): SynchronizedResultApplyResult {
    val exercise = state.workout.exercises().getOrNull(incoming.exerciseIndex)
        ?: return SynchronizedResultApplyResult.INVALID
    if (exercise.stepId() != incoming.exerciseStepId || exercise.name() != incoming.exerciseName) {
      return SynchronizedResultApplyResult.INVALID
    }
    if (incoming.resultId !=
        StrengthResultProtocol.stableResultId(
            incoming.executionId,
            incoming.exerciseIndex,
            incoming.exerciseStepId,
            incoming.setNumber)) {
      return SynchronizedResultApplyResult.INVALID
    }
    val result =
        StrengthSession.SetResult(
            incoming.exerciseIndex,
            incoming.exerciseStepId,
            incoming.exerciseName,
            incoming.setNumber,
            incoming.repetitions,
            incoming.durationSeconds,
            incoming.startedAtMillis,
            incoming.completedAtMillis,
            incoming.restDurationSeconds,
            incoming.leftRepetitions,
            incoming.rightRepetitions,
            incoming.rpe,
            incoming.painRating,
            incoming.notes,
            StrengthSession.SetResult.ResultStatus.COMPLETED,
            sourceResultId = incoming.resultId)
    val sameId = state.results.firstOrNull { it.sourceResultId == incoming.resultId }
    if (sameId != null) {
      return if (sameId == result) SynchronizedResultApplyResult.DUPLICATE
      else SynchronizedResultApplyResult.CONFLICT
    }
    val sameSetIndex = state.results.indexOfFirst { it.hasSameSetIdentity(result) }
    val updated =
        try {
          if (sameSetIndex >= 0) {
            val old = state.results[sameSetIndex]
            if (old.status == StrengthSession.SetResult.ResultStatus.COMPLETED &&
                old != result.copy(sourceResultId = null)) return SynchronizedResultApplyResult.CONFLICT
            state.replaceResult(result)
          } else {
            state.withSynchronizedResult(result)
          }
        } catch (_: IllegalArgumentException) {
          return SynchronizedResultApplyResult.INVALID
        }
    persist(updated)
    return SynchronizedResultApplyResult.COMMITTED
  }

  @Synchronized
  @Throws(IOException::class)
  fun saveInProgress(context: Context, state: StrengthSession) {
      write(context, "active.json", StrengthSessionCodec.encode(state, null))
  }

  @Synchronized
  @Throws(IOException::class)
  fun loadInProgress(context: Context): StrengthSession? {
    val file = file(context, "active.json")
    if (!file.exists()) return null
    val (state, finishedAt) =
        try {
          read(file)
        } catch (error: InvalidSavedSessionException) {
          AtomicFile(file).delete()
          throw error
        }
    if (finishedAt != null) throw IOException("Saved active session has already been completed")
    if (state.isComplete && completedExecution(context, state.executionId) != null) {
      AtomicFile(file).delete()
      return null
    }
    return state
  }

  @Synchronized
  @Throws(IOException::class)
  fun complete(context: Context, state: StrengthSession, finishedAtMillis: Long) {
    writeCompleted(context, state, finishedAtMillis)
    val activeFile = AtomicFile(file(context, "active.json"))
    activeFile.delete()
  }

  /** Store imported history without affecting the user's independently resumable active session. */
  @Synchronized
  @Throws(IOException::class)
  fun importCompleted(context: Context, state: StrengthSession, finishedAtMillis: Long) {
    writeCompleted(context, state, finishedAtMillis)
  }

  /** Replace one completed record only when it still matches the version the user edited. */
  @Synchronized
  @Throws(IOException::class)
  fun replaceCompleted(
      context: Context,
      expected: CompletedStrengthSession,
      replacement: CompletedStrengthSession) {
    require(expected.state.executionId == replacement.state.executionId) {
      "Edited workout execution identity cannot change"
    }
    require(expected.state.scheduledEntryId == replacement.state.scheduledEntryId) {
      "Edited workout plan entry identity cannot change"
    }
    require(expected.finishedAtMillis == replacement.finishedAtMillis) {
      "Edited workout completion time cannot change"
    }
    require(replacement.state.isComplete) { "Edited workout must remain complete" }
    val completionFile = file(context, "completed-${hash(expected.state.executionId)}.json")
    if (!completionFile.exists()) throw IOException("Completed workout no longer exists")
    val (savedState, savedFinishedAt) = read(completionFile)
    if (savedState == replacement.state && savedFinishedAt == replacement.finishedAtMillis) return
    if (savedState != expected.state || savedFinishedAt != expected.finishedAtMillis) {
      throw IOException("Completed workout changed since it was opened")
    }
    write(context, completionFile.name, StrengthSessionCodec.encode(replacement.state, replacement.finishedAtMillis))
  }

  private fun writeCompleted(context: Context, state: StrengthSession, finishedAtMillis: Long) {
    require(state.isComplete) { "Session is not complete" }
    require(finishedAtMillis > 0) { "Invalid completion time" }
    val completionFile = file(context, "completed-${hash(state.executionId)}.json")
    if (completionFile.exists()) {
      val (existing, existingFinishedAt) = read(completionFile)
      if (existing == state && existingFinishedAt == finishedAtMillis) return
      throw IOException("This workout execution is already complete")
    }
    write(context, completionFile.name, StrengthSessionCodec.encode(state, finishedAtMillis))
  }

  @Synchronized
  @Throws(IOException::class)
  fun completed(context: Context, scheduledEntryId: String): CompletedStrengthSession? {
    require(scheduledEntryId.isNotBlank()) { "Missing scheduled entry identity" }
    val newest = completedSessions(context).sessions
        .firstOrNull { it.state.scheduledEntryId == scheduledEntryId }
    if (newest != null) return newest
    // Version 1 stored exactly one completion under the scheduled entry ID.
    val legacy = file(context, "completed-${hash(scheduledEntryId)}.json")
    if (!legacy.exists()) return null
    return readCompletion(legacy, scheduledEntryId)
  }

  private fun readCompletion(file: File, scheduledEntryId: String): CompletedStrengthSession {
    val (state, finishedAt) = read(file)
    if (state.scheduledEntryId != scheduledEntryId || !state.isComplete || finishedAt == null) {
      throw IOException("Completion record does not match its scheduled workout")
    }
    return CompletedStrengthSession(state, finishedAt)
  }

  private fun completedExecution(context: Context, executionId: String): CompletedStrengthSession? {
    val file = file(context, "completed-${hash(executionId)}.json")
    if (!file.exists()) return null
    val (state, finishedAt) = read(file)
    if (state.executionId != executionId || !state.isComplete || finishedAt == null) {
      throw IOException("Completion record does not match its execution")
    }
    return CompletedStrengthSession(state, finishedAt)
  }

  @Synchronized
  @Throws(IOException::class)
  fun completedSessions(context: Context): StrengthSessionHistoryLoad {
    val directory = context.getDir("relven-strength-sessions", Context.MODE_PRIVATE)
    val files =
        directory.listFiles { file ->
          file.isFile && file.name.startsWith("completed-") && file.name.endsWith(".json")
        } ?: if (!directory.exists()) emptyArray() else throw IOException("Could not list strength history")
    val candidates = files.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
    var invalidCount = 0
    var omittedCount = (candidates.size - MAX_HISTORY_SCAN).coerceAtLeast(0)
    var bytesRead = 0L
    val sessions = ArrayList<CompletedStrengthSession>()
    val scan = candidates.take(MAX_HISTORY_SCAN)
    for ((index, file) in scan.withIndex()) {
      if (bytesRead + file.length() > MAX_HISTORY_READ_BYTES) {
        omittedCount += scan.size - index
        break
      }
      bytesRead += file.length()
      try {
        val (state, finishedAt) = read(file)
        if (
            !state.isComplete ||
                finishedAt == null ||
                finishedAt <= 0 ||
                file.name != "completed-${hash(state.executionId)}.json") {
          invalidCount++
        } else {
          sessions.add(CompletedStrengthSession(state, finishedAt))
        }
      } catch (_: Exception) {
        invalidCount++
      }
    }
    return StrengthSessionHistoryLoad(
        sessions.sortedByDescending(CompletedStrengthSession::finishedAtMillis), invalidCount, omittedCount)
  }

  private fun read(file: File): Pair<StrengthSession, Long?> {
    if (file.length() > MAX_SAVED_SESSION_BYTES) {
      throw InvalidSavedSessionException("Saved strength session is too large")
    }
    val content = AtomicFile(file).openRead().use { it.readBytes().toString(StandardCharsets.UTF_8) }
    return try {
      StrengthSessionCodec.decode(content)
    } catch (error: Exception) {
      throw InvalidSavedSessionException("Saved strength session is invalid", error)
    }
  }

  private fun write(context: Context, name: String, content: String) {
    if (content.toByteArray(StandardCharsets.UTF_8).size > MAX_SAVED_SESSION_BYTES) {
      throw IOException("Strength session is too large to save")
    }
    val target = file(context, name)
    val atomic = AtomicFile(target)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val backup = File(target.path + ".bak")
      if (target.exists() || backup.exists()) {
        atomic.openRead().use { /* Recover any legacy AtomicFile backup before replacing it. */ }
      }
      val replacement = File(target.path + ".new")
      if (replacement.exists() && !replacement.delete()) {
        throw IOException("Could not clear an incomplete strength session write")
      }
      try {
        FileOutputStream(replacement).use { output ->
          output.write(content.toByteArray(StandardCharsets.UTF_8))
          output.fd.sync()
        }
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
              Files.move(
                  replacement.toPath(),
                  target.toPath(),
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
              Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
          } else {
            Os.rename(replacement.absolutePath, target.absolutePath)
          }
        } catch (error: Exception) {
          throw IOException("Could not atomically replace strength session", error)
        }
      } catch (error: Exception) {
        replacement.delete()
        if (error is IOException) throw error
        throw IOException("Could not atomically replace strength session", error)
      }
      return
    }
    var output: java.io.FileOutputStream? = null
    try {
      output = atomic.startWrite()
      output.write(content.toByteArray(StandardCharsets.UTF_8))
      atomic.finishWrite(output)
    } catch (error: IOException) {
      output?.let(atomic::failWrite)
      throw error
    }
  }

  private fun file(context: Context, name: String) = File(context.getDir("relven-strength-sessions", Context.MODE_PRIVATE), name)

  private fun hash(value: String): String =
      MessageDigest.getInstance("SHA-256")
          .digest(value.toByteArray(StandardCharsets.UTF_8))
          .joinToString("") { "%02x".format(it) }

  internal class InvalidSavedSessionException(message: String, cause: Throwable? = null) :
      IOException(message, cause)
}
