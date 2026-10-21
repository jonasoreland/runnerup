package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunControlsPresentationTest {
  @Test
  fun presentationTracksPauseAndButtonLockState() {
    val controls = RunControlsStateHolder(Runnable {}, Runnable {}, Runnable {})

    controls.update(isPaused = true, buttonsLocked = true)

    assertTrue(controls.presentation.isPaused)
    assertTrue(controls.presentation.buttonsLocked)
  }

  @Test
  fun controlActionsForwardToRunnerUpCallbacks() {
    var lapActions = 0
    var pauseActions = 0
    var stopActions = 0
    val controls =
      RunControlsStateHolder(
        Runnable { lapActions++ },
        Runnable { pauseActions++ },
        Runnable { stopActions++ },
      )

    controls.onLap()
    controls.onTogglePause()
    controls.onStop()

    assertEquals(1, lapActions)
    assertEquals(1, pauseActions)
    assertEquals(1, stopActions)
    assertFalse(controls.presentation.isPaused)
  }
}
