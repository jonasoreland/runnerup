package org.runnerup.relven.strength

/** Derived display metrics from planned sets and explicitly stored results only. */
internal data class StrengthExercisePerformanceSummary(
    val exerciseIndex: Int,
    val exerciseName: String,
    val prescribedSetCount: Int,
    val completedSetCount: Int,
    val skippedSetCount: Int,
    val incompleteSetCount: Int,
    val unrecordedSetCount: Int,
    val totalRepetitions: Int?,
    val bestSetRepetitions: Int?,
    val averageRepetitionsPerSet: Double?,
    val totalWorkSeconds: Long?,
    val totalRestSeconds: Long?,
    val leftRepetitions: Int?,
    val rightRepetitions: Int?,
    val sideDifference: Int?,
    val sideSetCount: Int)

internal data class StrengthSessionSummary(
    val prescribedSetCount: Int,
    val completedSetCount: Int,
    val skippedSetCount: Int,
    val incompleteSetCount: Int,
    val unrecordedSetCount: Int,
    val totalRepetitions: Int?,
    val bestSetRepetitions: Int?,
    val averageRepetitionsPerSet: Double?,
    val totalWorkSeconds: Long?,
    val totalRestSeconds: Long?,
    val exercises: List<StrengthExercisePerformanceSummary>)

/**
 * Aggregates only completed, unique set rows. If a per-side result is missing either side, it is
 * excluded from bilateral repetition totals and side comparisons instead of treating the unknown
 * side as zero.
 */
internal fun strengthSessionSummary(session: StrengthSession): StrengthSessionSummary {
  val uniqueResults =
      session.results.distinctBy { Triple(it.exerciseIndex, it.exerciseStepId, it.setNumber) }
  val exercises =
      session.workout.exercises().mapIndexed { index, exercise ->
        val exerciseResults = uniqueResults.filter { it.exerciseIndex == index }
        summarizeExercise(index, exercise.name(), exercise.sets(), exerciseResults)
      }
  val all = summarizeResults(uniqueResults)
  val prescribedSets = session.workout.exercises().sumOf { it.sets() }
  return StrengthSessionSummary(
      prescribedSetCount = prescribedSets,
      completedSetCount = uniqueResults.count { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED },
      skippedSetCount = uniqueResults.count { it.status == StrengthSession.SetResult.ResultStatus.SKIPPED },
      incompleteSetCount = uniqueResults.count { it.status == StrengthSession.SetResult.ResultStatus.INCOMPLETE },
      unrecordedSetCount = (prescribedSets - uniqueResults.size).coerceAtLeast(0),
      totalRepetitions = all.totalRepetitions,
      bestSetRepetitions = all.bestSetRepetitions,
      averageRepetitionsPerSet = all.averageRepetitionsPerSet,
      totalWorkSeconds = all.totalWorkSeconds,
      totalRestSeconds = all.totalRestSeconds,
      exercises = exercises)
}

private fun summarizeExercise(
    exerciseIndex: Int,
    exerciseName: String,
    prescribedSetCount: Int,
    results: List<StrengthSession.SetResult>): StrengthExercisePerformanceSummary {
  val totals = summarizeResults(results)
  val sideResults =
      results.filter {
        it.status == StrengthSession.SetResult.ResultStatus.COMPLETED &&
            it.leftRepetitions != null && it.rightRepetitions != null
      }
  val left = sideResults.takeIf { it.isNotEmpty() }?.sumOf { it.leftRepetitions!! }
  val right = sideResults.takeIf { it.isNotEmpty() }?.sumOf { it.rightRepetitions!! }
  return StrengthExercisePerformanceSummary(
      exerciseIndex = exerciseIndex,
      exerciseName = exerciseName,
      prescribedSetCount = prescribedSetCount,
      completedSetCount = results.count { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED },
      skippedSetCount = results.count { it.status == StrengthSession.SetResult.ResultStatus.SKIPPED },
      incompleteSetCount = results.count { it.status == StrengthSession.SetResult.ResultStatus.INCOMPLETE },
      unrecordedSetCount = (prescribedSetCount - results.size).coerceAtLeast(0),
      totalRepetitions = totals.totalRepetitions,
      bestSetRepetitions = totals.bestSetRepetitions,
      averageRepetitionsPerSet = totals.averageRepetitionsPerSet,
      totalWorkSeconds = totals.totalWorkSeconds,
      totalRestSeconds = totals.totalRestSeconds,
      leftRepetitions = left,
      rightRepetitions = right,
      sideDifference = if (left != null && right != null) kotlin.math.abs(left - right) else null,
      sideSetCount = sideResults.size)
}

private data class MeasuredTotals(
    val totalRepetitions: Int?,
    val bestSetRepetitions: Int?,
    val averageRepetitionsPerSet: Double?,
    val totalWorkSeconds: Long?,
    val totalRestSeconds: Long?)

private fun summarizeResults(results: List<StrengthSession.SetResult>): MeasuredTotals {
  val completed = results.filter { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED }
  val repetitionCounts = completed.mapNotNull(::knownRepetitionCount)
  val durations = completed.mapNotNull(StrengthSession.SetResult::durationSeconds)
  val restDurations = results.mapNotNull(StrengthSession.SetResult::restDurationSeconds)
  return MeasuredTotals(
      totalRepetitions = repetitionCounts.takeIf { it.isNotEmpty() }?.sum(),
      bestSetRepetitions = repetitionCounts.maxOrNull(),
      averageRepetitionsPerSet = repetitionCounts.takeIf { it.isNotEmpty() }?.average(),
      totalWorkSeconds = durations.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() },
      totalRestSeconds = restDurations.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() })
}

private fun knownRepetitionCount(result: StrengthSession.SetResult): Int? =
    result.repetitions ?: if (result.leftRepetitions != null && result.rightRepetitions != null) {
      result.leftRepetitions + result.rightRepetitions
    } else {
      null
    }
