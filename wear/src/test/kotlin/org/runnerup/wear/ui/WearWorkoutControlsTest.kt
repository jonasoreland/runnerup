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
}
