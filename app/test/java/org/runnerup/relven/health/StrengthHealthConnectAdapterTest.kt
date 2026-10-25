package org.runnerup.relven.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser
import java.time.Instant

class StrengthHealthConnectAdapterTest {
  private val workout = RelvenStrengthWorkoutParser.parse(
      """{"schemaVersion":3,"id":"strength","name":"Prehab","sport":"strength","steps":[
        {"id":"pushup","kind":"segment","phase":"work","end":{"type":"manual"},"exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,"exerciseRevision":1,"snapshot":{"name":"Wall push-up","instructions":"Push with control.","equipment":[],"images":[]},"sets":1,"minRepetitions":1,"maxRepetitions":20,"restSeconds":0}}]}""")

  @Test
  fun exportsOnlyActualStrengthResultsWithinRealSessionBounds() {
    val startMillis = 1_800_000_000_000L
    val state = StrengthSession.prepare("plan", "entry", workout)
        .begin(1_000L, startMillis)
        .completeResult(
            StrengthSession.RepetitionResultInput(
                leftRepetitions = 8, rightRepetitions = 7, rpe = 7.5, painRating = 1,
                notes = "Vänster sida trött"),
            nowElapsedRealtimeMillis = 9_000L,
            nowWallClockMillis = startMillis + 8_000L)
    val record = StrengthHealthConnectAdapter.toRecordedWorkout(
        CompletedStrengthSession(state, startMillis + 10_000L))!!

    assertEquals("strength-${state.executionId}", record.sessionId)
    assertEquals(HealthConnectActivity.Strength, record.activity)
    assertEquals(HealthConnectSessionDisposition.Completed, record.disposition)
    assertEquals(Instant.ofEpochMilli(startMillis), record.startTime)
    assertEquals(Instant.ofEpochMilli(startMillis + 10_000L), record.endTime)
    val notes = requireNotNull(record.notes)
    assertTrue(notes.contains("left 8, right 7"))
    assertTrue(notes.contains("RPE 7.5"))
    assertTrue(notes.contains("Vänster sida trött"))
    assertTrue(record.segments.isEmpty())
  }

  @Test
  fun omitsUntrustworthySessionBoundsAndDoesNotPromoteSkippedSetsToResults() {
    val legacy = StrengthSession.start("plan", "entry", workout).copy(phase = StrengthSession.Phase.COMPLETE)
    assertNull(StrengthHealthConnectAdapter.toRecordedWorkout(CompletedStrengthSession(legacy, 1_800_000_000_000L)))

    val start = 1_800_000_000_000L
    val completed = StrengthSession.prepare("plan", "entry", workout)
        .begin(1_000L, start)
        .completeRepetitions(0, 2_000L, start + 1_000L)
    val skipped = completed.copy(
        results = completed.results.map { it.copy(status = StrengthSession.SetResult.ResultStatus.SKIPPED) })
    val record = StrengthHealthConnectAdapter.toRecordedWorkout(CompletedStrengthSession(skipped, start + 2_000L))!!

    assertEquals(HealthConnectSessionDisposition.Partial, record.disposition)
    assertNull(record.notes)
    assertTrue(record.segments.isEmpty())
    assertFalse(record.notes?.contains("0 reps") == true)
  }

  @Test
  fun mapsTimestampedBilateralRepetitionsAndKeepsUnsupportedRpeInNotes() {
    val startMillis = 1_800_000_000_000L
    val state = StrengthSession.prepare("plan", "entry", workout)
        .begin(1_000L, startMillis)
        .completeResult(
            StrengthSession.RepetitionResultInput(repetitions = 12, rpe = 8.0),
            nowElapsedRealtimeMillis = 5_000L,
            nowWallClockMillis = startMillis + 4_000L)
    val record = StrengthHealthConnectAdapter.toRecordedWorkout(
        CompletedStrengthSession(state, startMillis + 5_000L))!!

    assertEquals(1, record.segments.size)
    assertEquals(12, record.segments.single().repetitions)
    assertEquals(Instant.ofEpochMilli(startMillis), record.segments.single().startTime)
    assertEquals(Instant.ofEpochMilli(startMillis + 4_000L), record.segments.single().endTime)
    assertEquals(1, record.segments.single().setNumber)
    assertTrue(requireNotNull(record.notes).contains("RPE 8.0"))
  }
}
