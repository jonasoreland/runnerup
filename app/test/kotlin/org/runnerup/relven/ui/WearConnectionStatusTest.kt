package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConnectionStatusTest {
  @Test
  fun failedWearServiceQueryShowsSetupGuidance() {
    val state = wearConnectionState(supported = true, requestSucceeded = false, connectedNodes = null)

    assertEquals(WearConnectionState.ServiceUnavailable, state)
    val label = wearStateLabel(state)
    assertTrue(label.contains("kompanjon", ignoreCase = true))
    assertTrue(label.contains("Google Play-tjänster", ignoreCase = true))
  }

  @Test
  fun successfulEmptyQueryStillMeansNoConnectedWatch() {
    val state = wearConnectionState(supported = true, requestSucceeded = true, connectedNodes = emptyList())

    assertEquals(WearConnectionState.Disconnected, state)
    assertEquals("Ingen ansluten Wear OS-klocka hittades", wearStateLabel(state))
  }
}
