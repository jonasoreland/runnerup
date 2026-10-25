package org.runnerup.service

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.wear.StrengthResultProtocol
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

class WearStrengthExecutionControllerTest {
  @Test
  fun beginAndPauseCommitWatchAuthorityRevisionBeforeReturning() {
    val store = WearStrengthExecutionStore(MemoryStorage()) { 1_000L }
    val prepared = prepared()
    assertTrue(store.savePreparedSession(prepared))
    val controller = WearStrengthExecutionController(store, elapsedRealtimeMillis = { 1_000L })

    val begun = controller.update { it.begin(1_000L) }
    val paused = controller.update { it.pause(2_000L) }

    assertEquals(StrengthSession.Phase.REPETITIONS, begun.session?.phase)
    assertEquals(1L, begun.session?.authorityRevision)
    assertEquals(StrengthSession.Phase.PAUSED, paused.session?.phase)
    assertEquals(2L, paused.session?.authorityRevision)
    assertNotNull(paused.session?.lastAuthorityCommandId)
    assertEquals(paused.session, store.load())
  }

  @Test
  fun confirmedRepetitionsAreStableAndReturnedForDurableSync() {
    val store = WearStrengthExecutionStore(MemoryStorage()) { 1_000L }
    val prepared = prepared()
    assertTrue(store.savePreparedSession(prepared))
    val controller = WearStrengthExecutionController(store, elapsedRealtimeMillis = { 1_000L })
    controller.update { it.begin(1_000L) }

    val logged = controller.update {
      it.completeResult(StrengthSession.RepetitionResultInput(repetitions = 8), 2_000L)
    }
    val result = logged.syncableResults.single()

    assertEquals(8, result.repetitions)
    assertEquals(
        StrengthResultProtocol.stableResultId(
            prepared.executionId, 0, "push", 1),
        result.resultId)
    assertEquals(StrengthSession.Phase.REST, logged.session?.phase)
    assertEquals(2L, logged.session?.authorityRevision)
  }

  @Test
  fun earlyFinishMarksUnperformedSetsSkippedWithoutInventingResults() {
    val store = WearStrengthExecutionStore(MemoryStorage()) { 1_000L }
    assertTrue(store.savePreparedSession(prepared()))
    val controller = WearStrengthExecutionController(store, elapsedRealtimeMillis = { 1_000L })
    controller.update { it.begin(1_000L) }

    val finished = controller.update { it.finishEarly(2_000L) }

    assertEquals(StrengthSession.Phase.COMPLETE, finished.session?.phase)
    assertEquals(2, finished.session?.results?.size)
    assertTrue(finished.session!!.results.all { it.status == StrengthSession.SetResult.ResultStatus.SKIPPED })
    assertTrue(finished.session!!.results.all { it.repetitions == null && it.durationSeconds == null })
  }

  @Test
  fun earlyFinishKeepsTimedResultPendingForExplicitLogging() {
    val store = WearStrengthExecutionStore(MemoryStorage()) { 31_000L }
    val prepared = StrengthSession.prepare("plan", "timed-entry", timedWorkout(), 1_000L)
    assertTrue(store.savePreparedSession(prepared))
    val controller = WearStrengthExecutionController(store, elapsedRealtimeMillis = { 31_000L })
    controller.update { it.begin(1_000L) }

    val ended = controller.update { it.finishEarly(31_000L) }.session!!

    assertEquals(StrengthSession.Phase.AWAITING_RESULT, ended.phase)
    assertEquals(1, ended.pendingResults.size)
    assertEquals(StrengthSession.SetResult.ResultStatus.SKIPPED, ended.results.single().status)
    val logged = controller.update { it.completeRepetitions(7, 32_000L) }.session!!
    assertEquals(StrengthSession.Phase.COMPLETE, logged.phase)
    assertEquals(7, logged.results.first { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED }.repetitions)
  }

  private fun prepared() =
      StrengthSession.prepare("plan", UUID.randomUUID().toString(), workout(), 0L)

  private fun workout() = RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"test","name":"Styrka","sport":"strength","steps":[
        {"id":"push","kind":"segment","phase":"work","end":{"type":"manual"},
         "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
          "exerciseRevision":1,"snapshot":{"name":"Armhävning","instructions":"Kontrollerat.","equipment":[],"images":[]},
          "sets":2,"minRepetitions":1,"maxRepetitions":20,"restSeconds":30}}
      ]}""")

  private fun timedWorkout() = RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"timed-test","name":"Test","sport":"strength","steps":[
        {"id":"timed-push","kind":"segment","phase":"work","end":{"type":"time","seconds":30},
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
