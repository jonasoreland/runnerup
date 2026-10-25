package org.runnerup.relven.strength

import kotlin.math.ceil
import java.util.UUID
import org.runnerup.workout.RelvenStrengthWorkoutParser
import org.runnerup.common.wear.StrengthResultProtocol

/** Immutable phone-side execution state for a validated strength or prehab workout. */
data class StrengthSession(
    val planId: String,
    val scheduledEntryId: String,
    val workout: RelvenStrengthWorkoutParser.Workout,
    val exerciseIndex: Int,
    val setNumber: Int,
    val phase: Phase,
    val secondsRemaining: Int,
    val results: List<SetResult>,
    val resumePhase: Phase? = null,
    val executionId: String = scheduledEntryId,
    val pendingResults: List<PendingSetResult> = emptyList(),
    val timerDeadlineElapsedRealtimeMillis: Long? = null,
    val timerAnchorElapsedRealtimeMillis: Long? = null,
    val timerClockObservedElapsedRealtimeMillis: Long? = null,
    val authorityRevision: Long = 0L,
    val lastAuthorityCommandId: String? = null) {
  enum class Phase { PREPARING, REPETITIONS, TIMED_WORK, REST, AWAITING_RESULT, PAUSED, COMPLETE }

  /** Identity and measured duration are retained until the user explicitly logs the timed result. */
  data class PendingSetResult(
      val exerciseIndex: Int,
      val exerciseStepId: String,
      val exerciseName: String,
      val setNumber: Int,
      val durationSeconds: Int,
      val restDurationSeconds: Int? = null)

  /** Explicitly confirmed measured values from the fast logger. Null fields remain unknown. */
  data class RepetitionResultInput(
      val repetitions: Int? = null,
      val leftRepetitions: Int? = null,
      val rightRepetitions: Int? = null,
      val rpe: Double? = null,
      val painRating: Int? = null,
      val notes: String? = null)

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
      val exerciseCategory: String? = null,
      val sourceResultId: String? = null) {
    enum class ResultStatus { COMPLETED, SKIPPED, INCOMPLETE }

    /** Execution identity + exercise step + set number is the stable, retry-safe result key. */
    fun hasSameSetIdentity(other: SetResult): Boolean =
        exerciseIndex == other.exerciseIndex && exerciseStepId == other.exerciseStepId && setNumber == other.setNumber
  }

  val isComplete: Boolean
    get() = phase == Phase.COMPLETE

  /** Begins the first set only after the user accepts the prepared session. */
  fun begin(nowElapsedRealtimeMillis: Long): StrengthSession {
    check(phase == Phase.PREPARING) { "Session is not ready to begin" }
    require(nowElapsedRealtimeMillis >= 0) { "Invalid monotonic time" }
    return beginWork(0, 1, nowElapsedRealtimeMillis)
  }

  /** Assigns deterministic transport identities to confirmed legacy/new results exactly once. */
  fun withStableResultIds(): StrengthSession {
    val identified =
        results.map { result ->
          if (result.status != SetResult.ResultStatus.COMPLETED || result.sourceResultId != null) {
            result
          } else {
            result.copy(
                sourceResultId =
                    StrengthResultProtocol.stableResultId(
                        executionId, result.exerciseIndex, result.exerciseStepId, result.setNumber))
          }
        }
    return if (identified == results) this else copy(results = identified)
  }

  fun syncableResults(): List<StrengthResultProtocol.Result> =
      results.mapNotNull { result ->
        val resultId = result.sourceResultId ?: return@mapNotNull null
        if (result.status != SetResult.ResultStatus.COMPLETED) return@mapNotNull null
        StrengthResultProtocol.Result(
            executionId,
            resultId,
            result.exerciseIndex,
            result.exerciseStepId,
            result.exerciseName,
            result.setNumber,
            result.repetitions,
            result.durationSeconds,
            result.startedAtMillis,
            result.completedAtMillis,
            result.restDurationSeconds,
            result.leftRepetitions,
            result.rightRepetitions,
            result.rpe,
            result.painRating,
            result.notes)
      }

  fun matchesScheduledEntry(planId: String, scheduledEntryId: String): Boolean =
      this.planId == planId && this.scheduledEntryId == scheduledEntryId

  fun currentExercise(): RelvenStrengthWorkoutParser.Exercise {
    check(phase != Phase.COMPLETE) { "Strength session is complete" }
    return workout.exercises()[exerciseIndex]
  }

  /** Logs the oldest pending timed test first; otherwise completes the current manual-rep set. */
  fun completeRepetitions(
      repetitions: Int,
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    return completeResult(RepetitionResultInput(repetitions = repetitions), nowElapsedRealtimeMillis)
  }

  /** Confirms bilateral or per-side reps plus optional advanced details for the associated set. */
  fun completeResult(
      input: RepetitionResultInput,
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    validateInput(input)
    val refreshed =
        if ((phase == Phase.REST || phase == Phase.TIMED_WORK) && timerDeadlineElapsedRealtimeMillis != null) {
          tickAt(nowElapsedRealtimeMillis)
        } else {
          this
        }
    val pending = refreshed.pendingResults.firstOrNull()
    if (pending != null) {
      val result =
          SetResult(
              pending.exerciseIndex,
              pending.exerciseStepId,
              pending.exerciseName,
              pending.setNumber,
              input.repetitions,
              pending.durationSeconds,
              restDurationSeconds = pending.restDurationSeconds,
              leftRepetitions = input.leftRepetitions,
              rightRepetitions = input.rightRepetitions,
              rpe = input.rpe,
              painRating = input.painRating,
              notes = input.notes)
      return resolvePendingResult(refreshed, result, nowElapsedRealtimeMillis)
    }
    check(phase == Phase.REPETITIONS) { "Session is not waiting for repetitions" }
    return completeManualSet(input, nowElapsedRealtimeMillis)
  }

  private fun validateInput(input: RepetitionResultInput) {
    require((input.repetitions != null) xor
        (input.leftRepetitions != null && input.rightRepetitions != null)) {
      "Enter either a total count or both side counts"
    }
    require(input.leftRepetitions == null || input.rightRepetitions != null) {
      "Both side counts are required"
    }
    require(input.rightRepetitions == null || input.leftRepetitions != null) {
      "Both side counts are required"
    }
    require(input.repetitions == null || input.repetitions in 0..10_000) { "Invalid repetition count" }
    require(input.leftRepetitions == null || input.leftRepetitions in 0..10_000) { "Invalid left repetition count" }
    require(input.rightRepetitions == null || input.rightRepetitions in 0..10_000) { "Invalid right repetition count" }
    require(input.rpe == null || input.rpe.isFinite() && input.rpe in 0.0..10.0) { "Invalid RPE" }
    require(input.painRating == null || input.painRating in 0..10) { "Invalid discomfort rating" }
    require(input.notes == null || input.notes.length <= 1_000) { "Set notes are too long" }
  }

  /** Skip a timed repetition count without inventing a result or interrupting an active rest timer. */
  fun skipPendingTimedResult(
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    val refreshed =
        if ((phase == Phase.REST || phase == Phase.TIMED_WORK) && timerDeadlineElapsedRealtimeMillis != null) {
          tickAt(nowElapsedRealtimeMillis)
        } else {
          this
        }
    val pending = refreshed.pendingResults.firstOrNull()
        ?: throw IllegalStateException("Session has no pending timed result")
    val skipped =
        SetResult(
            pending.exerciseIndex,
            pending.exerciseStepId,
            pending.exerciseName,
            pending.setNumber,
            repetitions = null,
            durationSeconds = null,
            restDurationSeconds = pending.restDurationSeconds,
            status = SetResult.ResultStatus.SKIPPED)
    return resolvePendingResult(refreshed, skipped, nowElapsedRealtimeMillis)
  }

  /** Skip the current exercise and its remaining sets without fabricating completed performance. */
  fun skipCurrentExercise(
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    check(phase != Phase.PAUSED && phase != Phase.AWAITING_RESULT && phase != Phase.COMPLETE) {
      "Session cannot skip an exercise in its current phase"
    }
    val session = if (phase == Phase.REST) recordCurrentRest(nowElapsedRealtimeMillis) else this
    val exercise = session.currentExercise()
    val skippedResults =
        (session.setNumber..exercise.sets()).map { skippedSet ->
          SetResult(
              session.exerciseIndex,
              exercise.stepId(),
              exercise.name(),
              skippedSet,
              repetitions = null,
              durationSeconds = null,
              status = SetResult.ResultStatus.SKIPPED)
        }
    val updatedResults = sortedResults(session.results + skippedResults)
    val nextIndex = session.exerciseIndex + 1
    if (nextIndex >= session.workout.exercises().size) {
      val nextPhase = if (session.pendingResults.isEmpty()) Phase.COMPLETE else Phase.AWAITING_RESULT
      return session.clearTimer(nowElapsedRealtimeMillis).copy(
          phase = nextPhase,
          secondsRemaining = 0,
          results = updatedResults)
    }
    return session.beginWork(nextIndex, 1, nowElapsedRealtimeMillis).copy(results = updatedResults)
  }

  private fun resolvePendingResult(
      refreshed: StrengthSession,
      result: SetResult,
      nowElapsedRealtimeMillis: Long): StrengthSession {
    val remainingPending = refreshed.pendingResults.drop(1)
    val updatedResults = sortedResults(refreshed.results + result)
    val nextPhase =
        if (refreshed.phase == Phase.AWAITING_RESULT && remainingPending.isEmpty()) Phase.COMPLETE else refreshed.phase
    return refreshed.copy(
        phase = nextPhase,
        secondsRemaining = if (nextPhase == Phase.COMPLETE) 0 else refreshed.secondsRemaining,
        results = updatedResults,
        pendingResults = remainingPending,
        timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
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

  /** Adds a confirmed remote result and advances the active session from that exact set. */
  fun withSynchronizedResult(
      result: SetResult,
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    val exercise = workout.exercises().getOrNull(result.exerciseIndex)
        ?: throw IllegalArgumentException("Invalid result exercise position")
    require(result.exerciseStepId == exercise.stepId() && result.exerciseName == exercise.name()) {
      "Set result does not match its exercise snapshot"
    }
    require(result.status == SetResult.ResultStatus.COMPLETED) { "Only completed results can sync" }
    require(result.sourceResultId != null && runCatching { UUID.fromString(result.sourceResultId) }.isSuccess) {
      "Synchronized result is missing its source identity"
    }
    validateResult(result, exercise)
    val pendingIndex = pendingResults.indexOfFirst { it.exerciseIndex == result.exerciseIndex && it.setNumber == result.setNumber }
    if (pendingIndex >= 0) {
      require(pendingIndex == 0) { "Synchronized result is out of pending order" }
      val refreshed =
          if ((phase == Phase.REST || phase == Phase.TIMED_WORK) && timerDeadlineElapsedRealtimeMillis != null) {
            tickAt(nowElapsedRealtimeMillis)
          } else {
            this
          }
      return resolvePendingResult(refreshed, result, nowElapsedRealtimeMillis)
    }
    require(phase != Phase.COMPLETE && phase != Phase.AWAITING_RESULT) { "Session cannot accept this result now" }
    require(result.exerciseIndex == exerciseIndex && result.setNumber == setNumber) {
      "Synchronized result is not for the current set"
    }
    val wasPaused = phase == Phase.PAUSED
    val updatedResults = sortedResults(results + result)
    val next = nextSetPosition(exerciseIndex, setNumber)
    var advanced =
        if (next == null) {
          val nextPhase = if (pendingResults.isEmpty()) Phase.COMPLETE else Phase.AWAITING_RESULT
          clearTimer(nowElapsedRealtimeMillis).copy(
              phase = nextPhase, secondsRemaining = 0, results = updatedResults)
        } else {
          val nextWork = beginWork(next.first, next.second, nowElapsedRealtimeMillis).copy(results = updatedResults)
          val rest = exercise.restSeconds()
          if (rest > 0) nextWork.withPhaseTimer(Phase.REST, rest, nowElapsedRealtimeMillis) else nextWork
        }
    if (wasPaused && advanced.phase != Phase.COMPLETE && advanced.phase != Phase.AWAITING_RESULT) {
      advanced = advanced.pause(nowElapsedRealtimeMillis)
    }
    return advanced
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

  /** Refresh a timer from its monotonic deadline; delayed UI frames do not accumulate drift. */
  fun tickAt(nowElapsedRealtimeMillis: Long): StrengthSession {
    check(phase == Phase.REST || phase == Phase.TIMED_WORK) { "Session is not counting down" }
    require(nowElapsedRealtimeMillis >= 0) { "Invalid monotonic time" }
    var current =
        when {
          timerDeadlineElapsedRealtimeMillis == null || timerAnchorElapsedRealtimeMillis == null ->
              withTimer(secondsRemaining, nowElapsedRealtimeMillis)
          nowElapsedRealtimeMillis < timerAnchorElapsedRealtimeMillis ->
              withTimer(secondsRemaining, nowElapsedRealtimeMillis)
          else -> this
        }
    var elapsedTransitions = 0
    while (true) {
      val deadline = requireNotNull(current.timerDeadlineElapsedRealtimeMillis)
      val remaining = ceil((deadline - nowElapsedRealtimeMillis).coerceAtLeast(0L) / 1_000.0).toInt()
      if (remaining > 0) {
        return current.copy(
            secondsRemaining = remaining,
            timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
      }
      val expired =
          current.copy(secondsRemaining = 0, timerClockObservedElapsedRealtimeMillis = deadline)
      current =
          if (current.phase == Phase.REST) {
            expired.recordCurrentRest(deadline).beginWork(current.exerciseIndex, current.setNumber, deadline)
          } else {
            expired.finishTimedSet(deadline)
          }
      elapsedTransitions++
      if (current.phase != Phase.REST && current.phase != Phase.TIMED_WORK) {
        return current.copy(timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
      }
      // Bound catch-up after a long suspension. The next refresh continues from the stored deadlines.
      if (elapsedTransitions >= 10_000) {
        return current.copy(
            secondsRemaining = 0,
            timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
      }
    }
  }

  /** Legacy deterministic one-second step, retained for existing state-machine callers/tests. */
  fun tick(): StrengthSession {
    val deadline = timerDeadlineElapsedRealtimeMillis
    val now =
        if (deadline == null) {
          (timerClockObservedElapsedRealtimeMillis ?: 0L) + 1_000L
        } else {
          deadline - (secondsRemaining - 1).coerceAtLeast(0) * 1_000L
        }
    return tickAt(now)
  }

  fun skipRest(
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    check(phase == Phase.REST) { "Session is not resting" }
    return recordCurrentRest(nowElapsedRealtimeMillis).beginWork(exerciseIndex, setNumber, nowElapsedRealtimeMillis)
  }

  /** Associates elapsed active rest with the immediately preceding set, logged or still pending. */
  private fun recordCurrentRest(nowElapsedRealtimeMillis: Long): StrengthSession {
    val previous = previousSetPosition() ?: return this
    val (previousExerciseIndex, previousSetNumber) = previous
    val previousExercise = workout.exercises()[previousExerciseIndex]
    val plannedRest = previousExercise.restSeconds()
    val elapsedRest = (plannedRest - remainingSeconds(nowElapsedRealtimeMillis)).coerceIn(0, plannedRest)
    val resultIndex = results.indexOfFirst {
      it.exerciseIndex == previousExerciseIndex && it.setNumber == previousSetNumber
    }
    if (resultIndex >= 0) {
      val updated = results.toMutableList()
      updated[resultIndex] = updated[resultIndex].copy(restDurationSeconds = elapsedRest)
      return copy(results = sortedResults(updated))
    }
    val pendingIndex = pendingResults.indexOfFirst {
      it.exerciseIndex == previousExerciseIndex && it.setNumber == previousSetNumber
    }
    if (pendingIndex >= 0) {
      val updated = pendingResults.toMutableList()
      updated[pendingIndex] = updated[pendingIndex].copy(restDurationSeconds = elapsedRest)
      return copy(pendingResults = updated)
    }
    return this
  }

  private fun previousSetPosition(): Pair<Int, Int>? =
      if (setNumber > 1) {
        exerciseIndex to setNumber - 1
      } else if (exerciseIndex > 0) {
        val previousExerciseIndex = exerciseIndex - 1
        previousExerciseIndex to workout.exercises()[previousExerciseIndex].sets()
      } else {
        null
      }

  fun pause(
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    check(phase != Phase.PAUSED && phase != Phase.COMPLETE) { "Session cannot be paused" }
    val remaining =
        if (phase == Phase.REST || phase == Phase.TIMED_WORK) {
          remainingSeconds(nowElapsedRealtimeMillis)
        } else {
          secondsRemaining
        }
    return copy(
        phase = Phase.PAUSED,
        secondsRemaining = remaining,
        resumePhase = phase,
        timerDeadlineElapsedRealtimeMillis = null,
        timerAnchorElapsedRealtimeMillis = null,
        timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
  }

  fun resume(
      nowElapsedRealtimeMillis: Long = timerClockObservedElapsedRealtimeMillis ?: 0L): StrengthSession {
    check(phase == Phase.PAUSED && resumePhase != null) { "Session is not paused" }
    val resumedPhase = requireNotNull(resumePhase)
    val resumed = copy(phase = resumedPhase, resumePhase = null)
    return if (resumedPhase == Phase.REST || resumedPhase == Phase.TIMED_WORK) {
      resumed.withTimer(secondsRemaining, nowElapsedRealtimeMillis)
    } else {
      resumed.copy(timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
    }
  }

  private fun completeManualSet(input: RepetitionResultInput, nowElapsedRealtimeMillis: Long): StrengthSession {
    check(phase == Phase.REPETITIONS) { "Session is not waiting for repetitions" }
    val exercise = currentExercise()
    val next = nextSetPosition(exerciseIndex, setNumber)
    val rest = exercise.restSeconds()
    val result =
        SetResult(
            exerciseIndex,
            exercise.stepId(),
            exercise.name(),
            setNumber,
            input.repetitions,
            null,
            restDurationSeconds = if (next != null && rest == 0) 0 else null,
            leftRepetitions = input.leftRepetitions,
            rightRepetitions = input.rightRepetitions,
            rpe = input.rpe,
            painRating = input.painRating,
            notes = input.notes)
    val updatedResults = sortedResults(results + result)
    if (next == null) {
      val nextPhase = if (pendingResults.isEmpty()) Phase.COMPLETE else Phase.AWAITING_RESULT
      return clearTimer(nowElapsedRealtimeMillis).copy(
          phase = nextPhase, secondsRemaining = 0, results = updatedResults)
    }
    val advanced = beginWork(next.first, next.second, nowElapsedRealtimeMillis).copy(results = updatedResults)
    return if (rest > 0) advanced.withPhaseTimer(Phase.REST, rest, nowElapsedRealtimeMillis) else advanced
  }

  private fun finishTimedSet(nowElapsedRealtimeMillis: Long): StrengthSession {
    val exercise = currentExercise()
    val duration = requireNotNull(exercise.durationSeconds())
    if (exercise.maxRepetitions() == 1) {
      val next = nextSetPosition(exerciseIndex, setNumber)
      val rest = exercise.restSeconds()
      val updatedResults =
          sortedResults(
              results +
                  SetResult(
                      exerciseIndex,
                      exercise.stepId(),
                      exercise.name(),
                      setNumber,
                      repetitions = null,
                      durationSeconds = duration,
                      restDurationSeconds = if (next != null && rest == 0) 0 else null))
      if (next == null) {
        val nextPhase = if (pendingResults.isEmpty()) Phase.COMPLETE else Phase.AWAITING_RESULT
        return clearTimer(nowElapsedRealtimeMillis).copy(
            phase = nextPhase, secondsRemaining = 0, results = updatedResults)
      }
      val advanced = beginWork(next.first, next.second, nowElapsedRealtimeMillis).copy(results = updatedResults)
      return if (rest > 0) advanced.withPhaseTimer(Phase.REST, rest, nowElapsedRealtimeMillis) else advanced
    }
    val next = nextSetPosition(exerciseIndex, setNumber)
    val rest = exercise.restSeconds()
    val pending =
        PendingSetResult(
            exerciseIndex, exercise.stepId(), exercise.name(), setNumber, duration,
            restDurationSeconds = if (next != null && rest == 0) 0 else null)
    val nextPending = pendingResults + pending
    if (next == null) {
      return copy(
          phase = Phase.AWAITING_RESULT,
          secondsRemaining = 0,
          pendingResults = nextPending,
          timerDeadlineElapsedRealtimeMillis = null,
          timerAnchorElapsedRealtimeMillis = null,
          timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
    }
    val advanced = beginWork(next.first, next.second, nowElapsedRealtimeMillis).copy(pendingResults = nextPending)
    return if (rest > 0) advanced.withPhaseTimer(Phase.REST, rest, nowElapsedRealtimeMillis) else advanced
  }

  private fun nextSetPosition(index: Int, set: Int): Pair<Int, Int>? {
    val exercise = workout.exercises()[index]
    if (set < exercise.sets()) return index to set + 1
    val nextIndex = index + 1
    return if (nextIndex < workout.exercises().size) nextIndex to 1 else null
  }

  private fun beginWork(index: Int, set: Int, nowElapsedRealtimeMillis: Long): StrengthSession {
    val exercise = workout.exercises()[index]
    val duration = workDuration(exercise)
    val phase = workPhase(exercise)
    val next = copy(
        exerciseIndex = index,
        setNumber = set,
        phase = phase,
        secondsRemaining = duration,
        resumePhase = null,
        timerDeadlineElapsedRealtimeMillis = null,
        timerAnchorElapsedRealtimeMillis = null,
        timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
    return if (phase == Phase.TIMED_WORK) next.withTimer(duration, nowElapsedRealtimeMillis) else next
  }

  private fun withPhaseTimer(phase: Phase, seconds: Int, nowElapsedRealtimeMillis: Long): StrengthSession =
      copy(phase = phase, secondsRemaining = seconds).withTimer(seconds, nowElapsedRealtimeMillis)

  private fun withTimer(seconds: Int, nowElapsedRealtimeMillis: Long): StrengthSession =
      copy(
          secondsRemaining = seconds,
          timerDeadlineElapsedRealtimeMillis = nowElapsedRealtimeMillis + seconds * 1_000L,
          timerAnchorElapsedRealtimeMillis = nowElapsedRealtimeMillis,
          timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)

  private fun clearTimer(nowElapsedRealtimeMillis: Long): StrengthSession =
      copy(
          timerDeadlineElapsedRealtimeMillis = null,
          timerAnchorElapsedRealtimeMillis = null,
          timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)

  private fun remainingSeconds(nowElapsedRealtimeMillis: Long): Int {
    val deadline = timerDeadlineElapsedRealtimeMillis ?: return secondsRemaining
    if (timerAnchorElapsedRealtimeMillis != null && nowElapsedRealtimeMillis < timerAnchorElapsedRealtimeMillis) {
      return secondsRemaining
    }
    return ceil((deadline - nowElapsedRealtimeMillis).coerceAtLeast(0L) / 1_000.0).toInt()
  }

  fun validateResult(result: SetResult, exercise: RelvenStrengthWorkoutParser.Exercise) {
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

  private fun sortedResults(values: List<SetResult>): List<SetResult> =
      values.sortedWith(compareBy(SetResult::exerciseIndex, SetResult::setNumber))

  companion object {
    fun prepare(
        planId: String,
        scheduledEntryId: String,
        workout: RelvenStrengthWorkoutParser.Workout,
        nowElapsedRealtimeMillis: Long = 0L): StrengthSession {
      require(planId.isNotBlank() && scheduledEntryId.isNotBlank()) { "Missing plan entry identity" }
      require(workout.exercises().isNotEmpty()) { "Strength workout has no exercises" }
      require(workout.exercises().sumOf { it.sets().toLong() } <= 100_000) { "Strength workout has too many sets" }
      require(nowElapsedRealtimeMillis >= 0) { "Invalid monotonic time" }
      return StrengthSession(
          planId,
          scheduledEntryId,
          workout,
          0,
          1,
          Phase.PREPARING,
          0,
          emptyList(),
          executionId = UUID.randomUUID().toString(),
          timerClockObservedElapsedRealtimeMillis = nowElapsedRealtimeMillis)
    }

    fun start(
        planId: String,
        scheduledEntryId: String,
        workout: RelvenStrengthWorkoutParser.Workout,
        nowElapsedRealtimeMillis: Long = 0L): StrengthSession {
      require(planId.isNotBlank() && scheduledEntryId.isNotBlank()) { "Missing plan entry identity" }
      require(workout.exercises().isNotEmpty()) { "Strength workout has no exercises" }
      require(workout.exercises().sumOf { it.sets().toLong() } <= 100_000) { "Strength workout has too many sets" }
      require(nowElapsedRealtimeMillis >= 0) { "Invalid monotonic time" }
      val first = workout.exercises().first()
      val phase = workPhase(first)
      val duration = workDuration(first)
      val timerDeadline = if (phase == Phase.TIMED_WORK) nowElapsedRealtimeMillis + duration * 1_000L else null
      return StrengthSession(
          planId,
          scheduledEntryId,
          workout,
          0,
          1,
          phase,
          duration,
          emptyList(),
          executionId = java.util.UUID.randomUUID().toString(),
          timerDeadlineElapsedRealtimeMillis = timerDeadline,
          timerAnchorElapsedRealtimeMillis = if (timerDeadline != null) nowElapsedRealtimeMillis else null,
          timerClockObservedElapsedRealtimeMillis = if (timerDeadline != null) nowElapsedRealtimeMillis else null)
    }

    internal fun workPhase(exercise: RelvenStrengthWorkoutParser.Exercise) =
        if (exercise.durationSeconds() == null) Phase.REPETITIONS else Phase.TIMED_WORK

    private fun workDuration(exercise: RelvenStrengthWorkoutParser.Exercise) =
        exercise.durationSeconds() ?: 0
  }
}
