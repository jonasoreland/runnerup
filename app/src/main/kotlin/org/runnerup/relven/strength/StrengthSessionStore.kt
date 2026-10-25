package org.runnerup.relven.strength

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser
import org.runnerup.common.wear.StrengthResultProtocol
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

internal object StrengthSessionCodec {
  fun encode(state: StrengthSession, finishedAtMillis: Long?): String {
    val results = JSONArray()
    state.results.forEach { result ->
      val item =
          JSONObject()
              .put("exerciseIndex", result.exerciseIndex)
              .put("exerciseStepId", result.exerciseStepId)
              .put("exerciseName", result.exerciseName)
              .put("setNumber", result.setNumber)
      result.repetitions?.let { item.put("repetitions", it) }
      result.durationSeconds?.let { item.put("durationSeconds", it) }
      result.startedAtMillis?.let { item.put("startedAtMillis", it) }
      result.completedAtMillis?.let { item.put("completedAtMillis", it) }
      result.restDurationSeconds?.let { item.put("restDurationSeconds", it) }
      result.leftRepetitions?.let { item.put("leftRepetitions", it) }
      result.rightRepetitions?.let { item.put("rightRepetitions", it) }
      result.rpe?.let { item.put("rpe", it) }
      result.painRating?.let { item.put("painRating", it) }
      result.notes?.let { item.put("notes", it) }
      item.put("status", result.status.name)
      result.exerciseCategory?.let { item.put("exerciseCategory", it) }
      result.sourceResultId?.let { item.put("sourceResultId", it) }
      results.put(item)
    }
    val pendingResults = JSONArray()
    state.pendingResults.forEach { pending ->
      pendingResults.put(
          JSONObject()
              .put("exerciseIndex", pending.exerciseIndex)
              .put("exerciseStepId", pending.exerciseStepId)
              .put("exerciseName", pending.exerciseName)
              .put("setNumber", pending.setNumber)
              .put("durationSeconds", pending.durationSeconds)
              .putOpt("restDurationSeconds", pending.restDurationSeconds))
    }
    val root =
        JSONObject()
            .put("schemaVersion", VERSION)
            .put("planId", state.planId)
            .put("scheduledEntryId", state.scheduledEntryId)
            .put("executionId", state.executionId)
            .put("workoutJson", state.workout.sourceJson())
            .put("exerciseIndex", state.exerciseIndex)
            .put("setNumber", state.setNumber)
            .put("phase", state.phase.name)
            .put("secondsRemaining", state.secondsRemaining)
            .put("results", results)
            .put("pendingResults", pendingResults)
    state.resumePhase?.let { root.put("resumePhase", it.name) }
    state.timerDeadlineElapsedRealtimeMillis?.let { root.put("timerDeadlineElapsedRealtimeMillis", it) }
    state.timerAnchorElapsedRealtimeMillis?.let { root.put("timerAnchorElapsedRealtimeMillis", it) }
    state.timerClockObservedElapsedRealtimeMillis?.let { root.put("timerClockObservedElapsedRealtimeMillis", it) }
    finishedAtMillis?.let { root.put("finishedAtMillis", it) }
    return root.toString()
  }

  fun decode(json: String): Pair<StrengthSession, Long?> {
    val root = JSONObject(json)
    requireKeys(
        root,
        setOf(
            "schemaVersion", "planId", "scheduledEntryId", "executionId", "workoutJson", "exerciseIndex",
            "setNumber", "phase", "secondsRemaining", "results", "resumePhase", "finishedAtMillis",
            "pendingResults", "timerDeadlineElapsedRealtimeMillis", "timerAnchorElapsedRealtimeMillis",
            "timerClockObservedElapsedRealtimeMillis"))
    val version = root.optInt("schemaVersion", -1)
    require(version in 1..VERSION) { "Unknown strength session version" }
    val planId = root.getString("planId")
    val scheduledId = root.getString("scheduledEntryId")
    val executionId = root.optString("executionId", scheduledId)
    require(planId.isNotBlank() && scheduledId.isNotBlank()) { "Missing session identity" }
    require(executionId.isNotBlank()) { "Missing execution identity" }
    val workout = RelvenStrengthWorkoutParser.parse(root.getString("workoutJson"))
    val exerciseIndex = root.getInt("exerciseIndex")
    val setNumber = root.getInt("setNumber")
    val phase = StrengthSession.Phase.valueOf(root.getString("phase"))
    val seconds = root.getInt("secondsRemaining")
    require(exerciseIndex in workout.exercises().indices) { "Invalid exercise position" }
    require(setNumber in 1..workout.exercises()[exerciseIndex].sets()) { "Invalid set position" }
    require(seconds in 0..86_400) { "Invalid timer value" }
    val resultsJson = root.getJSONArray("results")
    require(resultsJson.length() <= 100_000) { "Too many saved set results" }
    val results = ArrayList<StrengthSession.SetResult>(resultsJson.length())
    repeat(resultsJson.length()) { index ->
      val item = resultsJson.getJSONObject(index)
      requireKeys(
          item,
          setOf(
              "exerciseIndex", "exerciseStepId", "exerciseName", "setNumber", "repetitions", "durationSeconds",
              "startedAtMillis", "completedAtMillis", "restDurationSeconds", "leftRepetitions", "rightRepetitions",
              "rpe", "painRating", "notes", "status", "exerciseCategory", "sourceResultId"))
      val resultExerciseIndex = item.getInt("exerciseIndex")
      require(resultExerciseIndex in workout.exercises().indices) { "Invalid result exercise position" }
      val stepId = item.getString("exerciseStepId")
      val exercise = workout.exercises()[resultExerciseIndex]
      require(exercise.stepId() == stepId && item.getString("exerciseName") == exercise.name()) {
        "Set result does not match its exercise snapshot"
      }
      val repetitions = item.optionalInt("repetitions")
      val duration = item.optionalInt("durationSeconds")
      val startedAt = item.optionalLong("startedAtMillis")
      val completedAt = item.optionalLong("completedAtMillis")
      val restDuration = item.optionalInt("restDurationSeconds")
      val leftRepetitions = item.optionalInt("leftRepetitions")
      val rightRepetitions = item.optionalInt("rightRepetitions")
      val rpe = item.optionalDouble("rpe")
      val painRating = item.optionalInt("painRating")
      val notes = if (item.has("notes")) item.getString("notes") else null
      val exerciseCategory = if (item.has("exerciseCategory")) item.getString("exerciseCategory") else null
      val sourceResultId = if (item.has("sourceResultId")) item.getString("sourceResultId") else null
      val status =
          if (version == 1) StrengthSession.SetResult.ResultStatus.COMPLETED
          else StrengthSession.SetResult.ResultStatus.valueOf(item.getString("status"))
      if (version == 1) {
        require((repetitions != null) xor (duration != null)) { "Set result must contain repetitions or duration" }
        require(repetitions == null || repetitions in 1..500) { "Invalid repetition result" }
        require(
            (repetitions != null && exercise.durationSeconds() == null) ||
                (duration != null && exercise.durationSeconds() != null && duration == exercise.durationSeconds())) {
          "Set result does not match its exercise type"
        }
      }
      val resultSet = item.getInt("setNumber")
      require(resultSet in 1..exercise.sets()) { "Invalid completed set number" }
      results.add(
          StrengthSession.SetResult(
              resultExerciseIndex,
              stepId,
              item.getString("exerciseName"),
              resultSet,
              repetitions,
              duration,
              startedAt,
              completedAt,
              restDuration,
              leftRepetitions,
              rightRepetitions,
              rpe,
              painRating,
              notes,
              status,
              exerciseCategory,
              sourceResultId))
    }
    require(results.map { it.exerciseIndex to it.setNumber }.toSet().size == results.size) {
      "Duplicate saved set result"
    }
    val pendingJson = if (version >= 3) root.getJSONArray("pendingResults") else JSONArray()
    require(pendingJson.length() <= 100_000) { "Too many pending timed results" }
    val pendingResults = ArrayList<StrengthSession.PendingSetResult>(pendingJson.length())
    repeat(pendingJson.length()) { index ->
      val item = pendingJson.getJSONObject(index)
      requireKeys(
          item,
          setOf("exerciseIndex", "exerciseStepId", "exerciseName", "setNumber", "durationSeconds", "restDurationSeconds"))
      val pendingExerciseIndex = item.getInt("exerciseIndex")
      require(pendingExerciseIndex in workout.exercises().indices) { "Invalid pending exercise position" }
      val exercise = workout.exercises()[pendingExerciseIndex]
      val stepId = item.getString("exerciseStepId")
      require(stepId == exercise.stepId() && item.getString("exerciseName") == exercise.name()) {
        "Pending result does not match its exercise snapshot"
      }
      require(exercise.durationSeconds() != null) { "Pending result is not a timed set" }
      val setNumber = item.getInt("setNumber")
      require(setNumber in 1..exercise.sets()) { "Invalid pending set position" }
      val duration = item.getInt("durationSeconds")
      require(duration in 0..86_400) { "Invalid pending set duration" }
      val restDuration = item.optionalInt("restDurationSeconds")
      require(restDuration == null || restDuration in 0..86_400) { "Invalid pending rest duration" }
      pendingResults.add(
          StrengthSession.PendingSetResult(
              pendingExerciseIndex, stepId, item.getString("exerciseName"), setNumber, duration, restDuration))
    }
    val pendingKeys = pendingResults.map { it.exerciseIndex to it.setNumber }
    require(pendingKeys.toSet().size == pendingKeys.size) { "Duplicate pending timed result" }
    require((results.map { it.exerciseIndex to it.setNumber }.toSet() intersect pendingKeys.toSet()).isEmpty()) {
      "Set cannot be both logged and pending"
    }
    val resumePhase = if (root.has("resumePhase")) StrengthSession.Phase.valueOf(root.getString("resumePhase")) else null
    require((phase == StrengthSession.Phase.PAUSED) == (resumePhase != null)) { "Invalid paused state" }
    require(resumePhase != StrengthSession.Phase.PAUSED && resumePhase != StrengthSession.Phase.COMPLETE) {
      "Invalid resume phase"
    }
    val finishedAt = root.optionalLong("finishedAtMillis")
    val timerDeadline = root.optionalLong("timerDeadlineElapsedRealtimeMillis")
    val timerAnchor = root.optionalLong("timerAnchorElapsedRealtimeMillis")
    val timerObserved = root.optionalLong("timerClockObservedElapsedRealtimeMillis")
    val state =
        StrengthSession(
            planId,
            scheduledId,
            workout,
            exerciseIndex,
            setNumber,
            phase,
            seconds,
            results,
            resumePhase,
            executionId,
            pendingResults,
            timerDeadline,
            timerAnchor,
            timerObserved)
    if (version >= 2) {
      results.forEach { result -> state.validateResult(result, workout.exercises()[result.exerciseIndex]) }
    }
    val expectedResults = buildSet {
      workout.exercises().forEachIndexed { index, exercise ->
        val completedSets =
            when {
              phase == StrengthSession.Phase.COMPLETE || phase == StrengthSession.Phase.AWAITING_RESULT ->
                  exercise.sets()
              index < exerciseIndex -> exercise.sets()
              index == exerciseIndex -> setNumber - 1
              else -> 0
            }
        for (set in 1..completedSets) add(index to set)
      }
    }
    val actualResults =
        (results.map { it.exerciseIndex to it.setNumber } + pendingKeys).toSet()
    require(actualResults == expectedResults) { "Saved set results do not match session progress" }
    if (phase == StrengthSession.Phase.COMPLETE) require(exerciseIndex == workout.exercises().lastIndex) {
      "Completed session has invalid exercise position"
    }
    val effectivePhase = if (phase == StrengthSession.Phase.PAUSED) resumePhase else phase
    if (effectivePhase == StrengthSession.Phase.TIMED_WORK) require(workout.exercises()[exerciseIndex].durationSeconds() != null) {
      "Timed state is not a timed exercise"
    }
    if (effectivePhase == StrengthSession.Phase.REPETITIONS) require(workout.exercises()[exerciseIndex].durationSeconds() == null) {
      "Repetition state is not a repetition exercise"
    }
    if (effectivePhase == StrengthSession.Phase.TIMED_WORK || effectivePhase == StrengthSession.Phase.REST) {
      require(seconds > 0) { "Active timer has no remaining time" }
      if (version >= 3 && timerDeadline != null) {
        require(timerAnchor != null && timerDeadline >= timerAnchor) { "Invalid monotonic timer anchor" }
      }
    } else {
      require(seconds == 0) { "Unexpected timer value" }
      require(timerDeadline == null && timerAnchor == null) { "Inactive timer has a deadline" }
    }
    require(timerDeadline == null || timerDeadline >= 0) { "Invalid timer deadline" }
    require(timerAnchor == null || timerAnchor >= 0) { "Invalid timer anchor" }
    require(timerObserved == null || timerObserved >= 0) { "Invalid timer observation" }
    if (phase == StrengthSession.Phase.AWAITING_RESULT) {
      require(pendingResults.isNotEmpty()) { "Awaiting result has no pending timed set" }
    }
    require(finishedAt == null || state.isComplete) { "Completion timestamp does not match session state" }
    return state to finishedAt
  }

  private fun requireKeys(objectValue: JSONObject, allowed: Set<String>) {
    val keys = objectValue.keys().asSequence().toSet()
    require(keys.all(allowed::contains)) { "Unexpected saved strength session fields" }
  }

  private fun JSONObject.optionalInt(key: String): Int? = if (has(key)) getInt(key) else null
  private fun JSONObject.optionalLong(key: String): Long? = if (has(key)) getLong(key) else null
  private fun JSONObject.optionalDouble(key: String): Double? = if (has(key)) getDouble(key) else null

  private const val VERSION = 4
}
