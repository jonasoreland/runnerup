package org.runnerup.common.wear

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionCodec
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionAuthorityProtocolTest {
  @Test
  fun rejectsOversizedAndUnknownProtocolFields() {
    assertThrows(IllegalArgumentException::class.java) {
      StrengthSessionAuthorityProtocol.decodeCommand(
          ByteArray(StrengthSessionAuthorityProtocol.MAX_MESSAGE_BYTES + 1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      StrengthSessionAuthorityProtocol.decodeCommand(
          """{"version":1,"message":{"type":"command","sessionId":"${UUID.randomUUID()}","requestId":"${UUID.randomUUID()}","expectedRevision":0,"action":"BEGIN","extra":true}}"""
              .toByteArray())
    }
  }

  @Test
  fun watchAuthorityAppliesExpectedRevisionAndTreatsRetryAsDuplicate() {
    val prepared = prepared()
    val requestId = UUID.randomUUID().toString()
    val command = StrengthSessionAuthorityProtocol.Command(
        prepared.executionId,
        requestId,
        0L,
        StrengthSessionAuthorityProtocol.Action.BEGIN)

    val first = StrengthSessionAuthorityProtocol.applyAtWatch(prepared, command, 5_000L)
    val retry = StrengthSessionAuthorityProtocol.applyAtWatch(first.session, command, 6_000L)
    val stale = StrengthSessionAuthorityProtocol.applyAtWatch(
        prepared,
        command.copy(requestId = UUID.randomUUID().toString(), expectedRevision = 1L),
        5_000L)

    assertEquals(StrengthSessionAuthorityProtocol.Outcome.COMMITTED, first.outcome)
    assertEquals(1L, first.session.authorityRevision)
    assertEquals(StrengthSession.Phase.REPETITIONS, first.session.phase)
    assertEquals(StrengthSessionAuthorityProtocol.Outcome.DUPLICATE, retry.outcome)
    assertEquals(first.session, retry.session)
    assertEquals(StrengthSessionAuthorityProtocol.Outcome.STALE, stale.outcome)
    assertEquals(prepared, stale.session)
  }

  @Test
  fun watchAuthorityRejectsInvalidTransitionWithoutChangingState() {
    val prepared = prepared()
    val command = StrengthSessionAuthorityProtocol.Command(
        prepared.executionId,
        UUID.randomUUID().toString(),
        0L,
        StrengthSessionAuthorityProtocol.Action.RESUME)

    val rejected = StrengthSessionAuthorityProtocol.applyAtWatch(prepared, command, 5_000L)

    assertEquals(StrengthSessionAuthorityProtocol.Outcome.INVALID, rejected.outcome)
    assertEquals(prepared, rejected.session)
  }

  @Test
  fun commandAndAuthoritativeProjectionRoundTripWithStableRevision() {
    val requestId = UUID.randomUUID().toString()
    val session = prepared().begin(30_000L).copy(
      authorityRevision = 1L,
      lastAuthorityCommandId = requestId)
    val command = StrengthSessionAuthorityProtocol.Command(
      session.executionId,
      requestId,
      0L,
      StrengthSessionAuthorityProtocol.Action.BEGIN)

    val decodedCommand = StrengthSessionAuthorityProtocol.decodeCommand(
      StrengthSessionAuthorityProtocol.encodeCommand(command))
    val projection = StrengthSessionAuthorityProtocol.decodeProjection(
      StrengthSessionAuthorityProtocol.encodeProjection(session, requestId, 31_000L))

    assertEquals(command, decodedCommand)
    assertEquals(session, StrengthSessionCodec.decode(projection.sessionJson).first)
    assertEquals(session.executionId, projection.sessionId)
    assertEquals(requestId, projection.requestId)
    assertEquals(1L, projection.revision)
  }

  @Test
  fun acknowledgementCorrelatesSessionRequestRevisionAndOutcome() {
    val sessionId = UUID.randomUUID().toString()
    val requestId = UUID.randomUUID().toString()
    val ack = StrengthSessionAuthorityProtocol.Acknowledgement(
      sessionId,
      requestId,
      4L,
      StrengthSessionAuthorityProtocol.Outcome.COMMITTED)

    assertEquals(ack, StrengthSessionAuthorityProtocol.decodeAcknowledgement(
      StrengthSessionAuthorityProtocol.encodeAcknowledgement(ack)))
  }

  private fun prepared(): StrengthSession =
    StrengthSession.prepare("plan", UUID.randomUUID().toString(), workout(), 1_000L)

  private fun workout() = RelvenStrengthWorkoutParser.parse(
    """{"schemaVersion":3,"id":"test","name":"Styrka","sport":"strength","steps":[
      {"id":"push","kind":"segment","phase":"work","end":{"type":"manual"},
       "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
        "exerciseRevision":1,"snapshot":{"name":"Armhävning","instructions":"Kontrollerat.","equipment":[],"images":[]},
        "sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":30}}
    ]}""")
}
