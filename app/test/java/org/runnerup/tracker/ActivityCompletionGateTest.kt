package org.runnerup.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.tracker.TrackerState

class ActivityCompletionGateTest {
  @Test
  fun acceptsCompletionOnlyAfterWorkoutIsStoppedOrPaused() {
    val gate = ActivityCompletionGate()

    assertFalse(gate.tryBeginCompletion(TrackerState.STARTED))
    assertFalse(gate.tryBeginCompletion(TrackerState.CONNECTED))
    assertTrue(gate.tryBeginCompletion(TrackerState.STOPPED))
  }

  @Test
  fun acceptsOnlyOneSaveOrDiscardTransitionForAnActivity() {
    val gate = ActivityCompletionGate()

    assertTrue(gate.tryBeginCompletion(TrackerState.PAUSED))
    assertFalse(gate.tryBeginCompletion(TrackerState.PAUSED))
    assertFalse(gate.tryBeginCompletion(TrackerState.STOPPED))
  }

  @Test
  fun newActivityCanCompleteAfterThePreviousActivity() {
    val gate = ActivityCompletionGate()
    assertTrue(gate.tryBeginCompletion(TrackerState.STOPPED))

    gate.beginActivity()

    assertTrue(gate.tryBeginCompletion(TrackerState.STOPPED))
  }

  @Test
  fun failedDurableCheckpointClearAllowsCompletionToBeRetried() {
    val gate = ActivityCompletionGate()
    assertTrue(gate.tryBeginCompletion(TrackerState.PAUSED))

    gate.cancelCompletion()

    assertTrue(gate.tryBeginCompletion(TrackerState.PAUSED))
  }
}
