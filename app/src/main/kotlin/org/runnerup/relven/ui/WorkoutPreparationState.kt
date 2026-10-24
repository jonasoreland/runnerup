package org.runnerup.relven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class WorkoutPreparationPhase {
  PREPARING,
  READY,
  STARTING
}

internal data class WorkoutPreparationUiState(
    val sessionId: String,
    val workoutName: String,
    val upcomingStep: String,
    val phase: WorkoutPreparationPhase,
    val watchReady: Boolean,
    val status: String
)

internal class WorkoutPreparationStateHolder(
    sessionId: String,
    workoutName: String,
    upcomingStep: String
) {
  var value by
      mutableStateOf(
          WorkoutPreparationUiState(
              sessionId,
              workoutName,
              upcomingStep,
              WorkoutPreparationPhase.PREPARING,
              watchReady = false,
              status = "Ansluter till klockan"))
    private set

  fun onWatchPreparationFinished(watchReady: Boolean, status: String) {
    if (value.phase != WorkoutPreparationPhase.PREPARING) return
    value = value.copy(phase = WorkoutPreparationPhase.READY, watchReady = watchReady, status = status)
  }

  fun requestStart(): Boolean {
    if (value.phase != WorkoutPreparationPhase.READY) return false
    value = value.copy(phase = WorkoutPreparationPhase.STARTING)
    return true
  }

  fun onStartFailed() {
    if (value.phase != WorkoutPreparationPhase.STARTING) return
    value = value.copy(phase = WorkoutPreparationPhase.READY)
  }
}
