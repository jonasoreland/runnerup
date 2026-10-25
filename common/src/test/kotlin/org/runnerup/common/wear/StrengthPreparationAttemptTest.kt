package org.runnerup.common.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.util.Constants
import java.util.UUID

class StrengthPreparationAttemptTest {
  @Test
  fun acceptsOnlyOneCorrelatedAcknowledgementFromVerifiedNode() {
    val sessionId = UUID.randomUUID().toString()
    val requestId = UUID.randomUUID().toString()
    val attempt = StrengthPreparationAttempt("watch-node", sessionId, requestId)
    val ack = StrengthExecutionProtocol.encodeAcknowledgement(sessionId, requestId, true)

    assertEquals(StrengthPreparationAttempt.Result.IGNORE,
      attempt.onMessage("other-node", Constants.Wear.Path.MSG_STRENGTH_PREPARED, ack))
    assertEquals(StrengthPreparationAttempt.Result.IGNORE,
      attempt.onMessage("watch-node", Constants.Wear.Path.MSG_STRENGTH_PREPARE, ack))
    assertEquals(StrengthPreparationAttempt.Result.PREPARED,
      attempt.onMessage("watch-node", Constants.Wear.Path.MSG_STRENGTH_PREPARED, ack))
    assertEquals(StrengthPreparationAttempt.Result.IGNORE,
      attempt.onMessage("watch-node", Constants.Wear.Path.MSG_STRENGTH_PREPARED, ack))
    assertTrue(attempt.isCompleted)
    assertEquals(StrengthPreparationAttempt.Result.IGNORE, attempt.onTimeout())
  }

  @Test
  fun rejectedAndTimeoutAttemptsAreTerminal() {
    val rejectedSessionId = UUID.randomUUID().toString()
    val rejectedRequestId = UUID.randomUUID().toString()
    val rejected = StrengthPreparationAttempt("watch", rejectedSessionId, rejectedRequestId)
    val timeout = StrengthPreparationAttempt("watch", UUID.randomUUID().toString(), UUID.randomUUID().toString())

    assertEquals(StrengthPreparationAttempt.Result.REJECTED,
      rejected.onMessage("watch", Constants.Wear.Path.MSG_STRENGTH_PREPARED,
        StrengthExecutionProtocol.encodeAcknowledgement(rejectedSessionId, rejectedRequestId, false)))
    assertTrue(rejected.isCompleted)
    assertEquals(StrengthPreparationAttempt.Result.IGNORE, rejected.onTimeout())
    assertEquals(StrengthPreparationAttempt.Result.TIMEOUT, timeout.onTimeout())
    assertEquals(StrengthPreparationAttempt.Result.IGNORE, timeout.onTransportError())
  }
}
