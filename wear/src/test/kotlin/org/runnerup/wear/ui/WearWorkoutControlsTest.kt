package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.runnerup.common.tracker.TrackerState

class WearWorkoutControlsTest {
  @Test
  fun startActionAppearsOnlyForReadyTrackerStates() {
    assertEquals(WearStartMode.GPS_READY, wearStartMode(TrackerState.INITIALIZED))
    assertEquals(WearStartMode.ACTIVITY_READY, wearStartMode(TrackerState.CONNECTED))
    assertNull(wearStartMode(TrackerState.STARTED))
    assertNull(wearStartMode(TrackerState.PAUSED))
  }

  @Test
  fun activeAndPausedStatesSelectPauseAndResumeActions() {
    assertEquals(WearPauseAction.PAUSE, wearPauseAction(TrackerState.STARTED))
    assertEquals(WearPauseAction.RESUME, wearPauseAction(TrackerState.PAUSED))
    assertEquals(WearPauseAction.DISABLED, wearPauseAction(TrackerState.STOPPED))
    assertEquals(WearPauseAction.DISABLED, wearPauseAction(TrackerState.CONNECTED))
  }

  @Test
  fun finishingRequiresAnExplicitSecondConfirmation() {
    val confirmation = WearControlPresentationState()
    confirmation.updatePauseAction(TrackerState.STARTED)

    assertEquals(false, confirmation.finishConfirmationRequested)
    confirmation.requestFinish()
    assertEquals(true, confirmation.finishConfirmationRequested)
    confirmation.cancelFinish()
    assertEquals(false, confirmation.finishConfirmationRequested)
    confirmation.requestFinish()
    assertEquals(true, confirmation.confirmFinish())
    assertEquals(false, confirmation.finishConfirmationRequested)
  }

  @Test
  fun finishConfirmationIsUnavailableWhenWorkoutIsNotActive() {
    val state = WearControlPresentationState()
    state.requestFinish()
    assertEquals(false, state.finishConfirmationRequested)

    state.updatePauseAction(TrackerState.STARTED)
    state.requestFinish()
    assertEquals(true, state.finishConfirmationRequested)
    state.updatePauseAction(TrackerState.STOPPED)
    assertEquals(false, state.finishConfirmationRequested)
    assertEquals(false, state.confirmFinish())
  }

  @Test
  fun preparedWorkoutShowsPendingThenMonotonicCountdownAndReturnsToStart() {
    val state = WearControlPresentationState()

    state.startRequestSent()
    assertEquals(true, state.preparedStartPending)
    assertNull(state.preparedCountdownSeconds)

    state.updatePreparedCountdown(13_000L, 10_000L)
    assertEquals(false, state.preparedStartPending)
    assertEquals(3, state.preparedCountdownSeconds)
    state.updatePreparedCountdown(13_000L, 12_100L)
    assertEquals(1, state.preparedCountdownSeconds)
    state.updatePreparedCountdown(null, 13_000L)
    assertNull(state.preparedCountdownSeconds)
  }
}
