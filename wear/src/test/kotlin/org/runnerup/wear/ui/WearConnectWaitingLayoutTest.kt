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
  fun transportStatusDoesNotClaimThatTheRelvenHandshakeWasVerified() {
    assertEquals("Ansluter till telefonen", wearTransportStatusLabel(WearConnectionStatus.CONNECTING))
    assertEquals(
        "Data Layer ansluten · Relven-kontroll ej testad",
        wearTransportStatusLabel(WearConnectionStatus.CONNECTED))
    assertEquals(
        "Telefonens Data Layer frånkopplad",
        wearTransportStatusLabel(WearConnectionStatus.DISCONNECTED))
  }
}
