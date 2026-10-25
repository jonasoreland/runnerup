package org.runnerup.wear.ui

import org.runnerup.relven.strength.StrengthSession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stable Compose holder so countdown refreshes don't recreate the logger composition. */
class WearStrengthWorkoutUiState(initial: StrengthSession) {
  var session by mutableStateOf(initial)
    private set

  fun update(next: StrengthSession) {
    session = next
  }
}

/** Compact, glanceable values for the active Wear strength screen. */
data class WearStrengthWorkoutPresentation(
    val workoutName: String,
    val exerciseName: String,
    val exerciseIndex: Int,
    val exerciseCount: Int,
    val setNumber: Int,
    val setCount: Int,
    val phaseLabel: String,
    val secondsRemaining: Int,
    val pendingResultExerciseName: String?,
    val pendingResultSetNumber: Int?,
    val pendingResultDurationSeconds: Int?,
    val manualResultExpected: Boolean,
    val completedSetCount: Int,
    val canStart: Boolean,
    val canLogRepetitions: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canSkip: Boolean,
    val canFinish: Boolean,
    val complete: Boolean)

object WearStrengthWorkoutPresentationFactory {
  @JvmStatic
  fun from(session: StrengthSession): WearStrengthWorkoutPresentation {
    val exercise = session.workout.exercises().getOrNull(session.exerciseIndex)
    val phase = session.phase
    val phaseLabel =
        when (phase) {
          StrengthSession.Phase.PREPARING -> "Redo att starta"
          StrengthSession.Phase.REPETITIONS -> "Set ${session.setNumber} av ${exercise?.sets() ?: 0}"
          StrengthSession.Phase.TIMED_WORK -> "Arbete · set ${session.setNumber}"
          StrengthSession.Phase.REST -> "Vila · nästa set ${session.setNumber}"
          StrengthSession.Phase.AWAITING_RESULT -> "Logga resultat"
          StrengthSession.Phase.PAUSED -> "Pausat"
          StrengthSession.Phase.COMPLETE -> "Pass klart"
        }
    return WearStrengthWorkoutPresentation(
        workoutName = session.workout.name(),
        exerciseName = exercise?.name() ?: "Klart",
        exerciseIndex = session.exerciseIndex,
        exerciseCount = session.workout.exercises().size,
        setNumber = session.setNumber,
        setCount = exercise?.sets() ?: 0,
        phaseLabel = phaseLabel,
        secondsRemaining = session.secondsRemaining,
        pendingResultExerciseName = session.pendingResults.firstOrNull()?.exerciseName,
        pendingResultSetNumber = session.pendingResults.firstOrNull()?.setNumber,
        pendingResultDurationSeconds = session.pendingResults.firstOrNull()?.durationSeconds,
        manualResultExpected = phase == StrengthSession.Phase.REPETITIONS,
        completedSetCount =
            session.results.count { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED },
        canStart = phase == StrengthSession.Phase.PREPARING,
        canLogRepetitions = session.pendingResults.isNotEmpty() || phase == StrengthSession.Phase.REPETITIONS,
        canPause = phase in setOf(
            StrengthSession.Phase.REPETITIONS,
            StrengthSession.Phase.TIMED_WORK,
            StrengthSession.Phase.REST),
        canResume = phase == StrengthSession.Phase.PAUSED,
        canSkip = phase in setOf(
            StrengthSession.Phase.REPETITIONS,
            StrengthSession.Phase.TIMED_WORK,
            StrengthSession.Phase.REST),
        canFinish = phase in setOf(
            StrengthSession.Phase.REPETITIONS,
            StrengthSession.Phase.TIMED_WORK,
            StrengthSession.Phase.REST,
            StrengthSession.Phase.PAUSED) &&
            !(phase == StrengthSession.Phase.PAUSED && session.resumePhase == StrengthSession.Phase.AWAITING_RESULT),
        complete = phase == StrengthSession.Phase.COMPLETE)
  }
}
