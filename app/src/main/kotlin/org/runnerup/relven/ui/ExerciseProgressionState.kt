package org.runnerup.relven.ui

import org.runnerup.relven.strength.StrengthSessionHistoryLoad
import org.runnerup.relven.strength.ExerciseProgressSession
import org.runnerup.relven.strength.exerciseProgressions

internal sealed interface ExerciseProgressionLoadState {
  data object Loading : ExerciseProgressionLoadState

  data class Loaded(
      val sessionsByExerciseId: Map<String, List<ExerciseProgressSession>>,
      val unavailableRecordCount: Int) : ExerciseProgressionLoadState

  data object Unavailable : ExerciseProgressionLoadState
}

internal fun exerciseProgressionLoadState(
    result: Result<StrengthSessionHistoryLoad>?): ExerciseProgressionLoadState =
    result?.fold(
        onSuccess = ::loadedExerciseProgressionState,
        onFailure = { ExerciseProgressionLoadState.Unavailable })
        ?: ExerciseProgressionLoadState.Loading

internal fun loadedExerciseProgressionState(
    history: StrengthSessionHistoryLoad): ExerciseProgressionLoadState.Loaded =
    ExerciseProgressionLoadState.Loaded(
        exerciseProgressions(history.sessions),
        history.invalidRecordCount + history.omittedRecordCount)
