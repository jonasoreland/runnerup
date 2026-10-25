package org.runnerup.relven.health

import android.os.Build
import androidx.annotation.RequiresApi
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession
import java.time.Instant
import java.util.Locale

/** Adapts only finalized, wall-clock-bounded strength sessions and confirmed set results. */
@RequiresApi(Build.VERSION_CODES.O)
internal object StrengthHealthConnectAdapter {
  fun toRecordedWorkout(completion: CompletedStrengthSession): RecordedWorkoutForHealthConnect? {
    val state = completion.state
    val startedAtMillis = state.sessionStartedAtMillis ?: return null
    val finishedAtMillis = completion.finishedAtMillis
    if (!state.isComplete || startedAtMillis <= 0L || finishedAtMillis <= startedAtMillis) return null
    val start = Instant.ofEpochMilli(startedAtMillis)
    val end = Instant.ofEpochMilli(finishedAtMillis)
    val completedResults = state.results.filter { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED }
    val segments = completedResults.mapNotNull { result ->
      val setStart = result.startedAtMillis ?: return@mapNotNull null
      val setEnd = result.completedAtMillis ?: return@mapNotNull null
      val repetitions = result.repetitions ?: return@mapNotNull null
      if (setStart < startedAtMillis || setEnd > finishedAtMillis || setEnd <= setStart) return@mapNotNull null
      HealthConnectExerciseSegment(
          startTime = Instant.ofEpochMilli(setStart),
          endTime = Instant.ofEpochMilli(setEnd),
          exerciseName = result.exerciseName,
          repetitions = repetitions,
          durationSeconds = result.durationSeconds,
          leftRepetitions = result.leftRepetitions,
          rightRepetitions = result.rightRepetitions,
          rpe = result.rpe,
          setNumber = result.setNumber)
    }
    val notes = buildNotes(completedResults)
    val hasUncompletedSet = state.results.any { it.status != StrengthSession.SetResult.ResultStatus.COMPLETED }
    return RecordedWorkoutForHealthConnect(
        sessionId = "strength-${state.executionId}",
        version = 0L,
        activity = HealthConnectActivity.Strength,
        title = state.workout.name(),
        notes = notes,
        startTime = start,
        endTime = end,
        disposition = if (hasUncompletedSet) HealthConnectSessionDisposition.Partial
        else HealthConnectSessionDisposition.Completed,
        segments = segments)
  }

  private fun buildNotes(results: List<StrengthSession.SetResult>): String? {
    if (results.isEmpty()) return null
    val lines = results.map { result ->
      buildString {
        append(result.exerciseName.take(100))
        append(" · set ")
        append(result.setNumber)
        result.repetitions?.let { append(": $it reps") }
        if (result.leftRepetitions != null && result.rightRepetitions != null) {
          append(" (left ${result.leftRepetitions}, right ${result.rightRepetitions})")
        }
        result.durationSeconds?.let { append(" · ${it}s") }
        result.rpe?.let { append(" · RPE ${String.format(Locale.ROOT, "%.1f", it)}") }
        result.notes?.takeIf(String::isNotBlank)?.let { append(" · ").append(it.take(120)) }
      }
    }
    return lines.joinToString("\n").take(1_000).takeIf(String::isNotBlank)
  }
}
