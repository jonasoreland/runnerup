package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearHandshakePresentationTest {
  @Test
  fun newerConnectionTestInvalidatesOlderCallbacks() {
    val gate = WearHandshakeRequestGate()
    val firstRequest = gate.begin()
    val secondRequest = gate.begin()

    assertFalse(gate.isCurrent(firstRequest))
    assertTrue(gate.isCurrent(secondRequest))
  }

  @Test
  fun connectedNodeIsOnlyTransportPresenceUntilApplicationHandshakeSucceeds() {
    assertEquals(
        WearConnectionState.TransportConnected,
        wearConnectionState(supported = true, requestSucceeded = true, connectedNodes = listOf("Watch")))
    assertEquals(
        WearHandshakeState.NotTested,
        wearHandshakeState(status = null))
    assertEquals(
        WearHandshakeState.Verified,
        wearHandshakeState(status = "VERIFIED"))
  }

  @Test
  fun handshakeFailureStatesRemainDistinct() {
    assertEquals(
        WearHandshakeState.CompanionUnavailable,
        wearHandshakeState(status = "COMPANION_UNAVAILABLE"))
    assertTrue(
        handshakeStateLabel(WearHandshakeState.CompanionUnavailable, null)
            .contains("installera", ignoreCase = true))
    assertEquals(
        WearHandshakeState.TimedOut,
        wearHandshakeState(status = "TIMEOUT"))
    assertEquals(
        WearHandshakeState.ProtocolMismatch,
        wearHandshakeState(status = "PROTOCOL_MISMATCH"))
    assertEquals(WearHandshakeState.NoWatch, wearHandshakeState(status = "NO_WATCH"))
    assertEquals(WearHandshakeState.Failed, wearHandshakeState(status = "TRANSPORT_ERROR"))
  }
}
