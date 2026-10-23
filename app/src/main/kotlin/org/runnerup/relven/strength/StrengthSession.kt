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
      val durationSeconds: Int?)

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
    require(repetitions in 1..500) { "Repetitions must be from 1 to 500" }
    return completeSet(repetitions, null)
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
