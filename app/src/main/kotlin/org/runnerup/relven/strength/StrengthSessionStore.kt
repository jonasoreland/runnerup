package org.runnerup.relven.strength

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class CompletedStrengthSession(val state: StrengthSession, val finishedAtMillis: Long)

internal data class StrengthSessionHistoryLoad(
    val sessions: List<CompletedStrengthSession>,
    val invalidRecordCount: Int,
    val omittedRecordCount: Int = 0)

/** Private, offline persistence for the single resumable strength session and its completions. */
internal object StrengthSessionStore {
  private const val VERSION = 1
  private const val MAX_SAVED_SESSION_BYTES = 16_777_216
  private const val MAX_HISTORY_SCAN = 500
  private const val MAX_HISTORY_READ_BYTES = 33_554_432L

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
    if (state.isComplete && completed(context, state.scheduledEntryId) != null) {
      AtomicFile(file).delete()
      return null
    }
    return state
  }

  @Synchronized
  @Throws(IOException::class)
  fun complete(context: Context, state: StrengthSession, finishedAtMillis: Long) {
    require(state.isComplete) { "Session is not complete" }
    require(finishedAtMillis > 0) { "Invalid completion time" }
    val completionFile = file(context, "completed-${hash(state.scheduledEntryId)}.json")
    if (completionFile.exists()) throw IOException("Scheduled workout is already complete")
    write(context, completionFile.name, StrengthSessionCodec.encode(state, finishedAtMillis))
    val activeFile = AtomicFile(file(context, "active.json"))
    activeFile.delete()
  }

  @Synchronized
  @Throws(IOException::class)
  fun completed(context: Context, scheduledEntryId: String): CompletedStrengthSession? {
    require(scheduledEntryId.isNotBlank()) { "Missing scheduled entry identity" }
    val file = file(context, "completed-${hash(scheduledEntryId)}.json")
    if (!file.exists()) return null
    val (state, finishedAt) = read(file)
    if (state.scheduledEntryId != scheduledEntryId || !state.isComplete || finishedAt == null) {
      throw IOException("Completion record does not match its scheduled workout")
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
                file.name != "completed-${hash(state.scheduledEntryId)}.json") {
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
      results.put(item)
    }
    val root =
        JSONObject()
            .put("schemaVersion", VERSION)
            .put("planId", state.planId)
            .put("scheduledEntryId", state.scheduledEntryId)
            .put("workoutJson", state.workout.sourceJson())
            .put("exerciseIndex", state.exerciseIndex)
            .put("setNumber", state.setNumber)
            .put("phase", state.phase.name)
            .put("secondsRemaining", state.secondsRemaining)
            .put("results", results)
    state.resumePhase?.let { root.put("resumePhase", it.name) }
    finishedAtMillis?.let { root.put("finishedAtMillis", it) }
    return root.toString()
  }

  fun decode(json: String): Pair<StrengthSession, Long?> {
    val root = JSONObject(json)
    requireKeys(
        root,
        setOf(
            "schemaVersion", "planId", "scheduledEntryId", "workoutJson", "exerciseIndex",
            "setNumber", "phase", "secondsRemaining", "results", "resumePhase", "finishedAtMillis"))
    require(root.optInt("schemaVersion", -1) == VERSION) { "Unknown strength session version" }
    val planId = root.getString("planId")
    val scheduledId = root.getString("scheduledEntryId")
    require(planId.isNotBlank() && scheduledId.isNotBlank()) { "Missing session identity" }
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
      requireKeys(item, setOf("exerciseIndex", "exerciseStepId", "exerciseName", "setNumber", "repetitions", "durationSeconds"))
      val resultExerciseIndex = item.getInt("exerciseIndex")
      require(resultExerciseIndex in workout.exercises().indices) { "Invalid result exercise position" }
      val stepId = item.getString("exerciseStepId")
      val exercise = workout.exercises()[resultExerciseIndex]
      require(exercise.stepId() == stepId && item.getString("exerciseName") == exercise.name()) {
        "Set result does not match its exercise snapshot"
      }
      val repetitions = item.optionalInt("repetitions")
      val duration = item.optionalInt("durationSeconds")
      require((repetitions != null) xor (duration != null)) { "Set result must contain repetitions or duration" }
      require(repetitions == null || repetitions in 1..500) { "Invalid repetition result" }
      require(
          (repetitions != null && exercise.durationSeconds() == null) ||
              (duration != null && exercise.durationSeconds() != null && duration == exercise.durationSeconds())) {
        "Set result does not match its exercise type"
      }
      val resultSet = item.getInt("setNumber")
      require(resultSet in 1..exercise.sets()) { "Invalid completed set number" }
      results.add(StrengthSession.SetResult(resultExerciseIndex, stepId, item.getString("exerciseName"), resultSet, repetitions, duration))
    }
    require(results.map { it.exerciseIndex to it.setNumber }.toSet().size == results.size) {
      "Duplicate saved set result"
    }
    val resumePhase = if (root.has("resumePhase")) StrengthSession.Phase.valueOf(root.getString("resumePhase")) else null
    require((phase == StrengthSession.Phase.PAUSED) == (resumePhase != null)) { "Invalid paused state" }
    require(resumePhase != StrengthSession.Phase.PAUSED && resumePhase != StrengthSession.Phase.COMPLETE) {
      "Invalid resume phase"
    }
    val finishedAt = root.optionalLong("finishedAtMillis")
    val state =
        StrengthSession(
            planId, scheduledId, workout, exerciseIndex, setNumber, phase, seconds, results, resumePhase)
    val expectedResults = buildSet {
      workout.exercises().forEachIndexed { index, exercise ->
        val completedSets =
            when {
              phase == StrengthSession.Phase.COMPLETE -> exercise.sets()
              index < exerciseIndex -> exercise.sets()
              index == exerciseIndex -> setNumber - 1
              else -> 0
            }
        for (set in 1..completedSets) add(index to set)
      }
    }
    require(results.map { it.exerciseIndex to it.setNumber }.toSet() == expectedResults) {
      "Saved set results do not match session progress"
    }
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
    } else {
      require(seconds == 0) { "Unexpected timer value" }
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

  private const val VERSION = 1
}
