package org.runnerup.wear.ui

import org.runnerup.common.tracker.TrackerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class WearStartMode {
  GPS_READY,
  ACTIVITY_READY
}

internal enum class WearPauseAction {
  PAUSE,
  RESUME,
  DISABLED
}

internal fun wearStartMode(state: TrackerState?): WearStartMode? =
    when (state) {
      TrackerState.INITIALIZED -> WearStartMode.GPS_READY
      TrackerState.CONNECTED -> WearStartMode.ACTIVITY_READY
      else -> null
    }

internal fun wearPauseAction(state: TrackerState?): WearPauseAction =
    when (state) {
      TrackerState.STARTED -> WearPauseAction.PAUSE
      TrackerState.PAUSED -> WearPauseAction.RESUME
      else -> WearPauseAction.DISABLED
    }

class WearControlPresentationState {
  internal var startMode by mutableStateOf<WearStartMode?>(null)
    private set

  internal var pauseAction by mutableStateOf(WearPauseAction.DISABLED)
    private set

  fun updateStartState(state: TrackerState?) {
    startMode = wearStartMode(state)
  }

  fun updatePauseAction(state: TrackerState?) {
    pauseAction = wearPauseAction(state)
  }
}
