package org.runnerup.wear.ui

import org.runnerup.service.WearConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConnectWaitingLayoutTest {
  @Test
  fun compactLayoutIsSelectedForSmallRoundAndShortDisplays() {
    assertTrue(isCompactWearConnectLayout(192f, 192f))
    assertTrue(isCompactWearConnectLayout(220f, 180f))
    assertFalse(isCompactWearConnectLayout(280f, 280f))
  }

  @Test
  fun transportStatesUseLocalizedCopyThatDistinguishesTheAppHandshake() {
    assertEquals(
        org.runnerup.common.R.string.Wear_connection_connecting,
        wearTransportStatusResourceId(WearConnectionStatus.CONNECTING))
    assertEquals(
        org.runnerup.common.R.string.Wear_connection_connected,
        wearTransportStatusResourceId(WearConnectionStatus.CONNECTED))
    assertEquals(
        org.runnerup.common.R.string.Wear_connection_disconnected,
        wearTransportStatusResourceId(WearConnectionStatus.DISCONNECTED))
  }
}
