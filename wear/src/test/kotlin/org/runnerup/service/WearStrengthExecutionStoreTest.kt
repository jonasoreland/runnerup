package org.runnerup.service

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

class WearStrengthExecutionStoreTest {
  @Test
  fun acceptedTransitionIsDurableAndReplayedUntilPhoneAcknowledges() {
    val storage = MemoryStorage()
    var now = 15_000L
    val store = WearStrengthExecutionStore(storage) { now }
    val prepared = prepared()
    assertTrue(store.savePreparedSession(prepared))
    val commandId = UUID.randomUUID().toString()
    val started = StrengthSessionAuthorityProtocol.applyAtWatch(
        prepared,
        StrengthSessionAuthorityProtocol.Command(
            prepared.executionId,
            commandId,
            0L,
            StrengthSessionAuthorityProtocol.Action.BEGIN),
        5_000L).session

    assertEquals(WearStrengthExecutionStore.CommitResult.COMMITTED, store.commit(started))
    val reopened = WearStrengthExecutionStore(storage) { now }
    assertEquals(started, reopened.load())
    val firstDelivery = StrengthSessionAuthorityProtocol.decodeProjection(
        reopened.pendingProjection() ?: throw AssertionError("Projection should remain pending"))
    now = 20_000L
    val retryDelivery = StrengthSessionAuthorityProtocol.decodeProjection(
        reopened.pendingProjection() ?: throw AssertionError("Lost ACK must leave projection pending"))
    assertEquals(firstDelivery.sessionId, retryDelivery.sessionId)
    assertEquals(firstDelivery.requestId, retryDelivery.requestId)
    assertEquals(firstDelivery.revision, retryDelivery.revision)
    assertNotNull(retryDelivery.sessionJson)

    assertTrue(reopened.acknowledge(
        StrengthSessionAuthorityProtocol.Acknowledgement(
            prepared.executionId,
            commandId,
            1L,
            StrengthSessionAuthorityProtocol.Outcome.COMMITTED)))
    assertNull(reopened.pendingProjection())
  }

  @Test
  fun staleRevisionCannotReplaceDurableExecution() {
    val storage = MemoryStorage()
    val store = WearStrengthExecutionStore(storage) { 15_000L }
    val prepared = prepared()
    assertTrue(store.savePreparedSession(prepared))
    val begun = StrengthSessionAuthorityProtocol.applyAtWatch(
        prepared,
        StrengthSessionAuthorityProtocol.Command(
            prepared.executionId,
            UUID.randomUUID().toString(),
            0L,
            StrengthSessionAuthorityProtocol.Action.BEGIN),
        5_000L).session
    assertEquals(WearStrengthExecutionStore.CommitResult.COMMITTED, store.commit(begun))

    assertEquals(WearStrengthExecutionStore.CommitResult.STALE, store.commit(prepared))
    assertEquals(begun, store.load())
  }

  private fun prepared() =
      StrengthSession.prepare("plan", UUID.randomUUID().toString(), workout(), 1_000L)

  private fun workout() = RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"test","name":"Styrka","sport":"strength","steps":[
        {"id":"push","kind":"segment","phase":"work","end":{"type":"manual"},
         "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
          "exerciseRevision":1,"snapshot":{"name":"Armhävning","instructions":"Kontrollerat.","equipment":[],"images":[]},
          "sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":30}}
      ]}""")

  private class MemoryStorage : WearStrengthExecutionStore.Storage {
    var state: String? = null
    var acknowledged = 0L
    override fun readState() = state
    override fun readAcknowledgedRevision() = acknowledged
    override fun write(stateJson: String?, acknowledgedRevision: Long): Boolean {
      state = stateJson
      acknowledged = acknowledgedRevision
      return true
    }
  }
}
