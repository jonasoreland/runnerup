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

  internal var preparedCountdownSeconds by mutableStateOf<Int?>(null)
    private set

  internal var preparedStartPending by mutableStateOf(false)
    private set

  internal var finishConfirmationRequested by mutableStateOf(false)
    private set

  internal var skipSupported by mutableStateOf(false)
    private set

  fun updateStartState(state: TrackerState?) {
    startMode = wearStartMode(state)
  }

  fun updatePauseAction(state: TrackerState?) {
    pauseAction = wearPauseAction(state)
    if (pauseAction == WearPauseAction.DISABLED) finishConfirmationRequested = false
  }

  fun updateSkipSupport(supported: Boolean) {
    skipSupported = supported
  }

  fun requestFinish() {
    if (pauseAction != WearPauseAction.DISABLED) finishConfirmationRequested = true
  }

  fun cancelFinish() {
    finishConfirmationRequested = false
  }

  fun confirmFinish(): Boolean {
    if (!finishConfirmationRequested || pauseAction == WearPauseAction.DISABLED) return false
    finishConfirmationRequested = false
    return true
  }

  fun startRequestSent() {
    preparedStartPending = true
    preparedCountdownSeconds = null
  }

  fun updatePreparedCountdown(deadlineElapsedRealtimeMs: Long?, nowElapsedRealtimeMs: Long) {
    preparedStartPending = false
    preparedCountdownSeconds =
        deadlineElapsedRealtimeMs?.let { deadline ->
          ((deadline - nowElapsedRealtimeMs).coerceAtLeast(0L) + 999L).div(1000L).toInt()
        }
  }
}
