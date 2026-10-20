package org.runnerup.relven.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser

class StrengthSessionCodecTest {
  private val workout =
      RelvenStrengthWorkoutParser.parse(
          """{"schemaVersion":3,"id":"bodyweight","name":"Bodyweight","sport":"strength","steps":[
            {"id":"plank","kind":"segment","phase":"work","end":{"type":"time","seconds":30},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Plank","instructions":"Hold steady.","equipment":[],"images":[]},"sets":2,"minRepetitions":1,"maxRepetitions":1,"restSeconds":15}},
            {"id":"squat","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000002","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Squat","instructions":"Move slowly.","equipment":[],"images":[]},"sets":1,"minRepetitions":8,"maxRepetitions":12,"restSeconds":0}}
          ]}""")

  @Test
  fun roundTripsPausedAndCompletedSessionsWithTheirPlanEntryIdentity() {
    val paused = StrengthSession.start("plan-a", "entry-a", workout).pause()
    val decodedPaused = StrengthSessionCodec.decode(StrengthSessionCodec.encode(paused, null))

    assertEquals(paused, decodedPaused.first)
    assertNull(decodedPaused.second)

    var complete = paused.resume()
    repeat(30) { complete = complete.tick() }
    repeat(15) { complete = complete.tick() }
    repeat(30) { complete = complete.tick() }
    repeat(15) { complete = complete.tick() }
    complete = complete.completeRepetitions(9)
    assertTrue(complete.isComplete)
    val decodedComplete = StrengthSessionCodec.decode(StrengthSessionCodec.encode(complete, 1234L))
    assertEquals("entry-a", decodedComplete.first.scheduledEntryId)
    assertEquals(1234L, decodedComplete.second)
    assertEquals(3, decodedComplete.first.results.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsUnknownFieldsAndUnsupportedVersion() {
    val json = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    json.put("schemaVersion", 2)
    json.put("completed", true)
    StrengthSessionCodec.decode(json.toString())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsACompletionTimestampOnAnActiveSession() {
    val json = JSONObject(StrengthSessionCodec.encode(StrengthSession.start("plan", "entry", workout), null))
    json.put("finishedAtMillis", 1234L)
    StrengthSessionCodec.decode(json.toString())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsCompletedStateWithoutAllSetResults() {
    val state =
        StrengthSession.start("plan", "entry", workout)
            .copy(phase = StrengthSession.Phase.COMPLETE)
    StrengthSessionCodec.decode(StrengthSessionCodec.encode(state, 1234L))
  }
}
