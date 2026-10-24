package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.tracker.TrackerState

class WorkoutPreparationStateHolderTest {
  @Test
  fun trackerConnectsDuringPreparationOnlyFromNonRecordingStates() {
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INIT))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZING))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZED))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CLEANUP))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CONNECTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STARTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.PAUSED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STOPPED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.ERROR))
  }

  @Test
  fun startIsUnavailableUntilPreparationFinishes() {
    val state = WorkoutPreparationStateHolder("session-1", "Intervaller", "Uppvärmning")

    assertEquals(WorkoutPreparationPhase.PREPARING, state.value.phase)
    assertFalse(state.requestStart())
  }

  @Test
  fun phoneOnlyFallbackCanStartOnlyOnceAfterPreparation() {
    val state = WorkoutPreparationStateHolder("session-1", "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertFalse(state.value.watchReady)
    assertTrue(state.requestStart())
    assertFalse(state.requestStart())
    assertEquals(WorkoutPreparationPhase.STARTING, state.value.phase)
  }

  @Test
  fun failedStartCanReturnToReadyWithoutChangingSession() {
    val state = WorkoutPreparationStateHolder("session-1", "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")

    assertTrue(state.requestStart())
    state.onStartFailed()

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertEquals("session-1", state.value.sessionId)
    assertTrue(state.value.watchReady)
  }
}
