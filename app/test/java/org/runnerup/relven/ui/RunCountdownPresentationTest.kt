package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RunCountdownPresentationTest {
  @Test
  fun countdownStartsHiddenAndShowReservesSpaceWithOptionalText() {
    val state = RunCountdownStateHolder()

    assertEquals(RunCountdownPresentation(occupiesSpace = false, text = null), state.presentation)

    state.show("")
    assertEquals(RunCountdownPresentation(occupiesSpace = true, text = null), state.presentation)

    state.show("5 sec")
    assertEquals(RunCountdownPresentation(occupiesSpace = true, text = "5 sec"), state.presentation)
  }

  @Test
  fun hideReservesTheSlotAndRemoveCollapsesIt() {
    val state = RunCountdownStateHolder()
    state.show("5 sec")

    state.hide()
    assertEquals(RunCountdownPresentation(occupiesSpace = true, text = null), state.presentation)

    state.remove()
    assertEquals(RunCountdownPresentation(occupiesSpace = false, text = null), state.presentation)
  }
}
