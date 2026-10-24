package org.runnerup.relven.ui

import org.runnerup.workout.RelvenStrengthWorkoutParser
import kotlin.math.roundToLong

internal data class DurationEstimate(
    val minimumSeconds: Long,
    val maximumSeconds: Long,
    val approximate: Boolean = false) {
  init {
    require(minimumSeconds >= 0 && maximumSeconds >= minimumSeconds)
  }
}

internal enum class WorkoutCategory {
  RUNNING,
  STRENGTH,
  PREHAB
}

internal enum class PlanWorkoutStatus {
  UPCOMING,
  TODAY,
  PAST,
  COMPLETED
}

internal data class PlanWorkoutPreview(
    val category: WorkoutCategory,
    val duration: DurationEstimate?,
    val status: PlanWorkoutStatus)

/** Returns a duration only when all running segments have a finite, defensible estimate. */
internal fun estimateRunningWorkoutDuration(
    segments: List<RunningSegmentSummary>
): DurationEstimate? {
  if (segments.isEmpty()) return null
  var minimumTotalSeconds = 0.0
  var maximumTotalSeconds = 0.0
  var approximate = false
  for (segment in segments) {
    val endValue = segment.end.value ?: return null
    if (!endValue.isFinite() || endValue <= 0.0 || segment.repeatCount <= 0) return null
    val (minimumSegmentSeconds, maximumSegmentSeconds) =
        when (segment.end.type) {
          "time" -> endValue to endValue
          "distance" -> {
            val target = segment.target ?: return null
            if (
                !target.minimum.isFinite() || !target.maximum.isFinite() ||
                    target.minimum <= 0.0 || target.maximum < target.minimum) {
              return null
            }
            when (target.type) {
              "pace" -> {
                approximate = true
                (endValue / 1_000.0 * target.minimum) to
                    (endValue / 1_000.0 * target.maximum)
              }
              "speed" -> {
                approximate = true
                (endValue / target.maximum) to (endValue / target.minimum)
              }
              else -> return null
            }
          }
          else -> return null
        }
    minimumTotalSeconds += minimumSegmentSeconds * segment.repeatCount
    maximumTotalSeconds += maximumSegmentSeconds * segment.repeatCount
    if (
        !minimumTotalSeconds.isFinite() || !maximumTotalSeconds.isFinite() ||
            maximumTotalSeconds > MAX_ESTIMATED_SECONDS) {
      return null
    }
  }
  val minimum = minimumTotalSeconds.roundToLong()
  val maximum = maximumTotalSeconds.roundToLong()
  if (minimum <= 0L || maximum < minimum) return null
  return DurationEstimate(minimum, maximum, approximate)
}

/** Estimates a strength session from its prescription; repetition work is intentionally a range. */
internal fun estimateStrengthWorkoutDuration(
    workout: RelvenStrengthWorkoutParser.Workout
): DurationEstimate? {
  val exercises = workout.exercises()
  if (exercises.isEmpty()) return null

  var minimumWorkSeconds = 0.0
  var maximumWorkSeconds = 0.0
  var restSeconds = 0.0
  for (exercise in exercises) {
    val sets = exercise.sets().toDouble()
    val durationSeconds = exercise.durationSeconds()
    if (durationSeconds != null) {
      val timedWork = sets * durationSeconds
      minimumWorkSeconds += timedWork
      maximumWorkSeconds += timedWork
    } else {
      val minimumRepetitions = exercise.minRepetitions()
      val maximumRepetitions = exercise.maxRepetitions()
      if (minimumRepetitions <= 0 || maximumRepetitions < minimumRepetitions) return null
      minimumWorkSeconds += sets * minimumRepetitions * SECONDS_PER_REPETITION_MIN
      maximumWorkSeconds += sets * maximumRepetitions * SECONDS_PER_REPETITION_MAX
    }
    restSeconds += sets * exercise.restSeconds()
  }

  // StrengthSession rests after every set except the final set of the workout.
  restSeconds -= exercises.last().restSeconds().toDouble()
  val transitions = (exercises.size - 1).coerceAtLeast(0) * EXERCISE_TRANSITION_SECONDS
  val minimum = minimumWorkSeconds + restSeconds + transitions
  val maximum = maximumWorkSeconds + restSeconds + transitions
  if (
      !minimum.isFinite() || !maximum.isFinite() || minimum <= 0.0 ||
          maximum < minimum || maximum > MAX_ESTIMATED_SECONDS) {
    return null
  }
  return DurationEstimate(minimum.roundToLong(), maximum.roundToLong(), approximate = true)
}

internal fun planWorkoutPreview(
    entry: PlanCalendarEntry.Workout,
    scheduledDate: String,
    todayDate: String
): PlanWorkoutPreview {
  val category =
      when (entry.strengthDetails?.sport()) {
        "strength" -> WorkoutCategory.STRENGTH
        "prehab" -> WorkoutCategory.PREHAB
        else -> WorkoutCategory.RUNNING
      }
  val duration =
      when (category) {
        WorkoutCategory.RUNNING -> estimateRunningWorkoutDuration(entry.runningSegments)
        WorkoutCategory.STRENGTH, WorkoutCategory.PREHAB ->
            entry.strengthDetails?.let(::estimateStrengthWorkoutDuration)
      }
  val status =
      when {
        entry.completion != null -> PlanWorkoutStatus.COMPLETED
        scheduledDate > todayDate -> PlanWorkoutStatus.UPCOMING
        scheduledDate < todayDate -> PlanWorkoutStatus.PAST
        else -> PlanWorkoutStatus.TODAY
      }
  return PlanWorkoutPreview(category, duration, status)
}

private const val SECONDS_PER_REPETITION_MIN = 2.5
private const val SECONDS_PER_REPETITION_MAX = 4.0
private const val EXERCISE_TRANSITION_SECONDS = 20
private const val MAX_ESTIMATED_SECONDS = 7 * 24 * 60 * 60.0
