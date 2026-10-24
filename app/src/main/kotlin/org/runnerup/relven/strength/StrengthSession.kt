package org.runnerup.relven.strength

import org.runnerup.workout.RelvenStrengthWorkoutParser

/** Immutable phone-side execution state for a validated strength or prehab workout. */
internal data class StrengthSession(
    val planId: String,
    val scheduledEntryId: String,
    val workout: RelvenStrengthWorkoutParser.Workout,
    val exerciseIndex: Int,
    val setNumber: Int,
    val phase: Phase,
    val secondsRemaining: Int,
    val results: List<SetResult>,
    val resumePhase: Phase? = null,
    val executionId: String = scheduledEntryId) {
  enum class Phase { REPETITIONS, TIMED_WORK, REST, PAUSED, COMPLETE }

  data class SetResult(
      val exerciseIndex: Int,
      val exerciseStepId: String,
      val exerciseName: String,
      val setNumber: Int,
      val repetitions: Int?,
      val durationSeconds: Int?,
      val startedAtMillis: Long? = null,
      val completedAtMillis: Long? = null,
      val restDurationSeconds: Int? = null,
      val leftRepetitions: Int? = null,
      val rightRepetitions: Int? = null,
      val rpe: Double? = null,
      val painRating: Int? = null,
      val notes: String? = null,
      val status: ResultStatus = ResultStatus.COMPLETED,
      val exerciseCategory: String? = null) {
    enum class ResultStatus { COMPLETED, SKIPPED, INCOMPLETE }

    /** Execution identity + exercise step + set number is the stable, retry-safe result key. */
    fun hasSameSetIdentity(other: SetResult): Boolean =
        exerciseIndex == other.exerciseIndex && exerciseStepId == other.exerciseStepId && setNumber == other.setNumber
  }

  val isComplete: Boolean
    get() = phase == Phase.COMPLETE

  fun matchesScheduledEntry(planId: String, scheduledEntryId: String): Boolean =
      this.planId == planId && this.scheduledEntryId == scheduledEntryId

  fun currentExercise(): RelvenStrengthWorkoutParser.Exercise {
    check(phase != Phase.COMPLETE) { "Strength session is complete" }
    return workout.exercises()[exerciseIndex]
  }

  fun completeRepetitions(repetitions: Int): StrengthSession {
    check(phase == Phase.REPETITIONS) { "Session is not waiting for repetitions" }
    require(repetitions in 0..10_000) { "Repetitions must be from 0 to 10000" }
    return completeSet(repetitions, null)
  }

  /** Replace a confirmed result without changing its stable set identity or workout prescription. */
  fun replaceResult(updated: SetResult): StrengthSession {
    val exercise = workout.exercises().getOrNull(updated.exerciseIndex)
        ?: throw IllegalArgumentException("Invalid result exercise position")
    require(updated.exerciseStepId == exercise.stepId() && updated.exerciseName == exercise.name()) {
      "Set result does not match its exercise snapshot"
    }
    require(updated.setNumber in 1..exercise.sets()) { "Invalid result set position" }
    validateResult(updated, exercise)
    val index = results.indexOfFirst { it.hasSameSetIdentity(updated) }
    require(index >= 0) { "Set result does not exist" }
    return copy(results = results.toMutableList().also { it[index] = updated })
  }

  /** Clear a result explicitly while retaining an incomplete marker for that progressed set. */
  fun clearResult(exerciseIndex: Int, setNumber: Int): StrengthSession {
    val existing = results.firstOrNull { it.exerciseIndex == exerciseIndex && it.setNumber == setNumber }
        ?: return this
    return replaceResult(
        existing.copy(
            repetitions = null,
            durationSeconds = null,
            leftRepetitions = null,
            rightRepetitions = null,
            rpe = null,
            painRating = null,
            notes = null,
            status = SetResult.ResultStatus.INCOMPLETE))
  }

  fun tick(): StrengthSession {
    check(phase == Phase.REST || phase == Phase.TIMED_WORK) { "Session is not counting down" }
    if (secondsRemaining > 1) return copy(secondsRemaining = secondsRemaining - 1)
    return if (phase == Phase.REST) {
      beginWork(exerciseIndex, setNumber)
    } else {
      completeSet(null, currentExercise().durationSeconds())
    }
  }

  fun skipRest(): StrengthSession {
    check(phase == Phase.REST) { "Session is not resting" }
    return beginWork(exerciseIndex, setNumber)
  }

  fun pause(): StrengthSession {
    check(phase != Phase.PAUSED && phase != Phase.COMPLETE) { "Session cannot be paused" }
    return copy(phase = Phase.PAUSED, resumePhase = phase)
  }

  fun resume(): StrengthSession {
    check(phase == Phase.PAUSED && resumePhase != null) { "Session is not paused" }
    return copy(phase = requireNotNull(resumePhase), resumePhase = null)
  }

  private fun completeSet(repetitions: Int?, durationSeconds: Int?): StrengthSession {
    val exercise = currentExercise()
    val updatedResults =
        results + SetResult(exerciseIndex, exercise.stepId(), exercise.name(), setNumber, repetitions, durationSeconds)
    val hasAnotherSet = setNumber < exercise.sets()
    val nextExerciseIndex = if (hasAnotherSet) exerciseIndex else exerciseIndex + 1
    if (nextExerciseIndex >= workout.exercises().size) {
      return copy(phase = Phase.COMPLETE, secondsRemaining = 0, results = updatedResults)
    }
    val nextSet = if (hasAnotherSet) setNumber + 1 else 1
    val rest = exercise.restSeconds()
    val next = copy(
        exerciseIndex = nextExerciseIndex,
        setNumber = nextSet,
        phase = workPhase(workout.exercises()[nextExerciseIndex]),
        secondsRemaining = workDuration(workout.exercises()[nextExerciseIndex]),
        results = updatedResults)
    return if (rest > 0) next.copy(phase = Phase.REST, secondsRemaining = rest) else next
  }

  internal fun validateResult(result: SetResult, exercise: RelvenStrengthWorkoutParser.Exercise) {
    require(result.status != SetResult.ResultStatus.COMPLETED ||
        result.repetitions != null || result.durationSeconds != null ||
        result.leftRepetitions != null || result.rightRepetitions != null) {
      "Completed set result has no measured performance"
    }
    require(result.repetitions == null || result.repetitions in 0..10_000) { "Invalid repetition result" }
    require(result.leftRepetitions == null || result.leftRepetitions in 0..10_000) { "Invalid left repetition result" }
    require(result.rightRepetitions == null || result.rightRepetitions in 0..10_000) { "Invalid right repetition result" }
    require(result.repetitions == null || (result.leftRepetitions == null && result.rightRepetitions == null)) {
      "Bilateral and per-side repetitions cannot be combined"
    }
    require(result.durationSeconds == null || result.durationSeconds in 0..86_400) { "Invalid actual duration" }
    require(result.restDurationSeconds == null || result.restDurationSeconds in 0..86_400) { "Invalid rest duration" }
    require(result.rpe == null || result.rpe.isFinite() && result.rpe in 0.0..10.0) { "Invalid RPE" }
    require(result.painRating == null || result.painRating in 0..10) { "Invalid pain rating" }
    require(result.notes == null || result.notes.length <= 1_000) { "Set notes are too long" }
    require(result.exerciseCategory == null || result.exerciseCategory.isNotBlank() && result.exerciseCategory.length <= 80) {
      "Invalid exercise category snapshot"
    }
    require(result.startedAtMillis == null || result.startedAtMillis > 0) { "Invalid set start time" }
    require(result.completedAtMillis == null || result.completedAtMillis > 0) { "Invalid set completion time" }
    require(result.startedAtMillis == null || result.completedAtMillis == null ||
        result.completedAtMillis >= result.startedAtMillis) { "Set timestamps are out of order" }
    if (result.status != SetResult.ResultStatus.COMPLETED) {
      require(result.repetitions == null && result.durationSeconds == null &&
          result.leftRepetitions == null && result.rightRepetitions == null) {
        "Skipped or incomplete results cannot contain performance values"
      }
    } else if (exercise.durationSeconds() != null) {
      require(result.durationSeconds != null) { "Timed set result must contain actual duration" }
    } else {
      require(result.durationSeconds == null &&
          (result.repetitions != null || result.leftRepetitions != null || result.rightRepetitions != null)) {
        "Repetition set result must contain an actual repetition count"
      }
    }
  }

  private fun beginWork(index: Int, set: Int): StrengthSession {
    val exercise = workout.exercises()[index]
    return copy(
        exerciseIndex = index,
        setNumber = set,
        phase = workPhase(exercise),
        secondsRemaining = workDuration(exercise),
        resumePhase = null)
  }

  companion object {
    fun start(
        planId: String,
        scheduledEntryId: String,
        workout: RelvenStrengthWorkoutParser.Workout): StrengthSession {
      require(planId.isNotBlank() && scheduledEntryId.isNotBlank()) { "Missing plan entry identity" }
      require(workout.exercises().isNotEmpty()) { "Strength workout has no exercises" }
      require(workout.exercises().sumOf { it.sets().toLong() } <= 100_000) { "Strength workout has too many sets" }
      val first = workout.exercises().first()
      return StrengthSession(
          planId,
          scheduledEntryId,
          workout,
          0,
          1,
          workPhase(first),
          workDuration(first),
          emptyList(),
          executionId = java.util.UUID.randomUUID().toString())
    }

    internal fun workPhase(exercise: RelvenStrengthWorkoutParser.Exercise) =
        if (exercise.durationSeconds() == null) Phase.REPETITIONS else Phase.TIMED_WORK

    private fun workDuration(exercise: RelvenStrengthWorkoutParser.Exercise) =
        exercise.durationSeconds() ?: 0
  }
}
