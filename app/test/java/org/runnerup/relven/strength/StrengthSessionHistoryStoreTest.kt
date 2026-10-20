package org.runnerup.relven.strength

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    0, exercise.stepId(), exercise.name(), 1, 10, null)))
    StrengthSessionStore.complete(context, state, finishedAt)
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
