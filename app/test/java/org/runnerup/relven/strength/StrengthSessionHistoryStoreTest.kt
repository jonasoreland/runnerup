package org.runnerup.relven.strength

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.runnerup.workout.RelvenStrengthWorkoutParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StrengthSessionHistoryStoreTest {
  private val context: Context = RuntimeEnvironment.getApplication()
  private val workout =
      RelvenStrengthWorkoutParser.parse(
          """{"schemaVersion":3,"id":"bodyweight","name":"Bodyweight","sport":"strength","steps":[
            {"id":"squat-step","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Squat","instructions":"Move with control.","equipment":[],"images":[]},"sets":1,"minRepetitions":8,"maxRepetitions":12,"restSeconds":0}}
          ]}""")

  @Before
  fun clearStoredHistory() {
    historyDirectory().deleteRecursively()
  }

  @Test
  fun loadsValidCompletedRecordsNewestFirst() {
    save("older", 100)
    save("newer", 200)

    val loaded = StrengthSessionStore.completedSessions(context)

    assertEquals(listOf("newer", "older"), loaded.sessions.map { it.state.scheduledEntryId })
    assertEquals(0, loaded.invalidRecordCount)
  }

  @Test
  fun storesRepeatedRunsOfOnePlanEntryIndependentlyAndReturnsLatestCompletion() {
    val first = completeSession("scheduled", "run-1", 100)
    val second = completeSession("scheduled", "run-2", 200)

    assertEquals(listOf("run-2", "run-1"), StrengthSessionStore.completedSessions(context).sessions.map { it.state.executionId })
    assertEquals(second.executionId, StrengthSessionStore.completed(context, "scheduled")?.state?.executionId)
    assertEquals(first.scheduledEntryId, second.scheduledEntryId)
  }

  @Test
  fun skipsAndCountsInvalidCompletionRecords() {
    save("valid", 100)
    completionFile("damaged").writeText("not json")

    val loaded = StrengthSessionStore.completedSessions(context)

    assertEquals(listOf("valid"), loaded.sessions.map { it.state.scheduledEntryId })
    assertEquals(1, loaded.invalidRecordCount)
  }

  @Test
  fun ignoresActiveSessionFile() {
    save("valid", 100)
    File(historyDirectory(), "active.json").writeText("not json")

    val loaded = StrengthSessionStore.completedSessions(context)

    assertEquals(listOf("valid"), loaded.sessions.map { it.state.scheduledEntryId })
    assertEquals(0, loaded.invalidRecordCount)
  }

  @Test
  fun savingConfirmedProgressIsReplaceableAndCompletionRetryIsIdempotent() {
    val initial = StrengthSession.start("plan", "entry", workout).completeRepetitions(10)
    StrengthSessionStore.saveInProgress(context, initial)
    val corrected = initial.replaceResult(initial.results.single().copy(repetitions = 9))
    assertEquals(9, corrected.results.single().repetitions)
    assertEquals(
        9,
        org.json.JSONObject(StrengthSessionCodec.encode(corrected, null))
            .getJSONArray("results").getJSONObject(0).getInt("repetitions"))
    StrengthSessionStore.saveInProgress(context, corrected)
    val activeJson = File(historyDirectory(), "active.json").readText()
    assertTrue(activeJson, activeJson.contains("\"repetitions\":9"))

    assertEquals(corrected, StrengthSessionStore.loadInProgress(context))

    val completed = corrected.copy(phase = StrengthSession.Phase.COMPLETE)
    StrengthSessionStore.complete(context, completed, 200)
    StrengthSessionStore.complete(context, completed, 200)

    assertEquals(completed, StrengthSessionStore.completedSessions(context).sessions.single().state)
  }

  @Test
  fun editingACompletedSetPersistsAtomicallyAndRejectsAStaleOverwrite() {
    val originalState = completeSession("scheduled", "run-1", 500)
    val original = CompletedStrengthSession(originalState, 500)
    val correctedState = originalState.replaceResult(originalState.results.single().copy(repetitions = 9))
    val corrected = CompletedStrengthSession(correctedState, 500)

    StrengthSessionStore.replaceCompleted(context, original, corrected)

    val restored = StrengthSessionStore.completedSessions(context).sessions.single()
    assertEquals(9, restored.state.results.single().repetitions)
    assertEquals(500, restored.finishedAtMillis)
    assertThrows(java.io.IOException::class.java) {
      StrengthSessionStore.replaceCompleted(context, original, originalState.replaceResult(
          originalState.results.single().copy(repetitions = 8)).let { CompletedStrengthSession(it, 500) })
    }
  }

  @Test
  fun portableExportImportPersistsToHistoryAndIsIdempotent() {
    val active = StrengthSession.start("active-plan", "active-entry", workout)
    StrengthSessionStore.saveInProgress(context, active)
    val exercise = workout.exercises().single()
    val original = StrengthSession.start("portable-plan", "portable-entry", workout).copy(
        executionId = "portable-run",
        phase = StrengthSession.Phase.COMPLETE,
        secondsRemaining = 0,
        results = listOf(StrengthSession.SetResult(0, exercise.stepId(), exercise.name(), 1, 10, null)),
        timerDeadlineElapsedRealtimeMillis = null,
        timerAnchorElapsedRealtimeMillis = null,
        timerClockObservedElapsedRealtimeMillis = null)
    val exported = StrengthSessionExportCodec.encode(CompletedStrengthSession(original, 500))
    val imported = StrengthSessionExportCodec.decode(exported)

    StrengthSessionStore.importCompleted(context, imported.state, imported.finishedAtMillis)
    StrengthSessionStore.importCompleted(context, imported.state, imported.finishedAtMillis)

    val restored = StrengthSessionStore.completedSessions(context).sessions
        .single { it.state.executionId == "portable-run" }
    assertEquals(original.results, restored.state.results)
    assertEquals(500, restored.finishedAtMillis)
    assertEquals(active, StrengthSessionStore.loadInProgress(context))
  }

  @Test
  fun boundsTheFileScanToFiveHundredRecentRecords() {
    repeat(501) { index -> save("entry-$index", index + 1L) }

    val loaded = StrengthSessionStore.completedSessions(context)

    assertEquals(500, loaded.sessions.size)
    assertEquals(1, loaded.omittedRecordCount)
  }

  private fun save(entryId: String, finishedAt: Long) {
    val exercise = workout.exercises().single()
    val state =
        StrengthSession(
            "plan",
            entryId,
            workout,
            0,
            1,
            StrengthSession.Phase.COMPLETE,
            0,
            listOf(
                StrengthSession.SetResult(
                    0, exercise.stepId(), exercise.name(), 1, 10, null)),
            executionId = entryId)
    StrengthSessionStore.complete(context, state, finishedAt)
  }

  private fun completeSession(entryId: String, executionId: String, finishedAt: Long): StrengthSession {
    val exercise = workout.exercises().single()
    val state = StrengthSession(
        "plan", entryId, workout, 0, 1, StrengthSession.Phase.COMPLETE, 0,
        listOf(StrengthSession.SetResult(0, exercise.stepId(), exercise.name(), 1, 10, null)),
        executionId = executionId)
    StrengthSessionStore.complete(context, state, finishedAt)
    return state
  }

  private fun completionFile(entryId: String): File =
      File(historyDirectory(), "completed-${sha256(entryId)}.json")

  private fun historyDirectory(): File =
      context.getDir("relven-strength-sessions", Context.MODE_PRIVATE)

  private fun sha256(value: String): String =
      MessageDigest.getInstance("SHA-256")
          .digest(value.toByteArray())
          .joinToString("") { "%02x".format(it) }
}
