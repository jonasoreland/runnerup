package org.runnerup.common.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionCodec
import org.runnerup.workout.RelvenStrengthWorkoutParser
import java.util.UUID

class StrengthExecutionProtocolTest {
  @Test
  fun preparedSessionRoundTripsWithItsStableIdentityAndWorkoutSnapshot() {
    val session = preparedSession()
    val requestId = UUID.randomUUID().toString()

    val decoded = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(session, requestId))
    val restored = StrengthSessionCodec.decode(decoded.sessionJson).first

    assertEquals(session.executionId, decoded.sessionId)
    assertEquals(requestId, decoded.requestId)
    assertEquals(StrengthSession.Phase.PREPARING, restored.phase)
    assertEquals(session, restored)
  }

  @Test
  fun acknowledgementMatchesTheExactPreparationAttempt() {
    val session = preparedSession()
    val requestId = UUID.randomUUID().toString()
    val request = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(session, requestId))

    val accepted = StrengthExecutionProtocol.decodeAcknowledgement(
      StrengthExecutionProtocol.encodeAcknowledgement(session.executionId, requestId, true))
    val rejected = StrengthExecutionProtocol.decodeAcknowledgement(
      StrengthExecutionProtocol.encodeAcknowledgement(session.executionId, requestId, false))

    assertTrue(accepted.matches(request))
    assertTrue(accepted.isAccepted)
    assertFalse(rejected.isAccepted)
    assertFalse(StrengthExecutionProtocol.decodeAcknowledgement(
      StrengthExecutionProtocol.encodeAcknowledgement(UUID.randomUUID().toString(), requestId, true))
      .matches(request))
  }

  @Test
  fun onlyAnUntouchedPreparingSessionCanBeTransferred() {
    val active = StrengthSession.start("plan", "entry", workout(), 10_000L)

    runCatching { StrengthExecutionProtocol.encodePrepare(active, UUID.randomUUID().toString()) }
      .onSuccess { error("active sessions must not be accepted as preparation") }
  }

  @Test
  fun decoderRejectsPayloadsOverTheProtocolLimit() {
    val oversized = ByteArray(StrengthExecutionProtocol.MAX_ENCODED_BYTES + 1)

    val failure = runCatching { StrengthExecutionProtocol.decodePrepare(oversized) }.exceptionOrNull()

    assertNotNull(failure)
    assertEquals("Invalid strength preparation payload size", failure?.message)
  }

  @Test
  fun sameIdentityRequiresSameSessionPayloadAndNewIdentityCanReplace() {
    val first = preparedSession()
    val exactRetry = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(first, UUID.randomUUID().toString()))
    val sameSessionRetry = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(first, UUID.randomUUID().toString()))
    val conflictingState = first.copy(workout = workout("Different"))
    val conflict = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(conflictingState, UUID.randomUUID().toString()))
    val replacement = StrengthExecutionProtocol.decodePrepare(
      StrengthExecutionProtocol.encodePrepare(preparedSession(), UUID.randomUUID().toString()))

    assertEquals(StrengthExecutionProtocol.StorageDecision.DUPLICATE,
      StrengthExecutionProtocol.assessReplacement(exactRetry, sameSessionRetry))
    assertEquals(StrengthExecutionProtocol.StorageDecision.CONFLICT,
      StrengthExecutionProtocol.assessReplacement(exactRetry, conflict))
    assertEquals(StrengthExecutionProtocol.StorageDecision.REPLACE,
      StrengthExecutionProtocol.assessReplacement(exactRetry, replacement))
  }

  private fun preparedSession(name: String = "Knäböj") =
    StrengthSession.prepare("plan", UUID.randomUUID().toString(), workout(name), 1234L)

  private fun workout(name: String = "Knäböj") =
    RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"test","name":"Gym","sport":"strength","steps":[
        {"id":"squat","kind":"segment","phase":"work","end":{"type":"manual"},
         "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
          "exerciseRevision":1,"snapshot":{"name":"$name","instructions":"Kontrollerat.","equipment":[],"images":[]},
          "sets":3,"minRepetitions":6,"maxRepetitions":10,"restSeconds":60}}
      ]}""")
}
