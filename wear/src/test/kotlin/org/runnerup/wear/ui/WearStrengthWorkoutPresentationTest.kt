package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

class WearStrengthWorkoutPresentationTest {
  @Test
  fun preparedSessionOffersStartWithoutShowingAResultAsCompleted() {
    val presentation = WearStrengthWorkoutPresentationFactory.from(prepared())

    assertEquals("Väggarmhävning", presentation.exerciseName)
    assertEquals("Redo att starta", presentation.phaseLabel)
    assertEquals(1, presentation.setNumber)
    assertEquals(3, presentation.setCount)
    assertTrue(presentation.canStart)
    assertFalse(presentation.canLogRepetitions)
    assertEquals(0, presentation.completedSetCount)
  }

  @Test
  fun manualSetOffersExplicitRepEntryAndPause() {
    val session = prepared().begin(1_000L)
    val presentation = WearStrengthWorkoutPresentationFactory.from(session)

    assertEquals("Väggarmhävning", presentation.exerciseName)
    assertEquals("Set 1 av 3", presentation.phaseLabel)
    assertTrue(presentation.canLogRepetitions)
    assertTrue(presentation.manualResultExpected)
    assertTrue(presentation.canPause)
    assertTrue(presentation.canFinish)
    assertEquals(0, presentation.completedSetCount)
  }

  @Test
  fun timedRestKeepsItsTimerAndExposesPendingResultEntry() {
    val workout = workout(timedSeconds = 30, sets = 2, restSeconds = 30)
    val finished = StrengthSession.prepare("plan", "entry", workout).begin(1_000L).tickAt(31_000L)
    val presentation = WearStrengthWorkoutPresentationFactory.from(finished)

    assertEquals(StrengthSession.Phase.REST, finished.phase)
    assertTrue(presentation.canLogRepetitions)
    assertEquals(30, presentation.pendingResultDurationSeconds)
    assertEquals(30, presentation.secondsRemaining)
    assertEquals(0, presentation.completedSetCount)
    assertTrue(presentation.canPause)
    assertTrue(presentation.canFinish)
  }

  @Test
  fun awaitingTimedResultOffersLoggingButNotAnInertFinishAction() {
    val workout = workout(timedSeconds = 30, sets = 1)
    val awaiting = StrengthSession.prepare("plan", "entry", workout).begin(1_000L).tickAt(31_000L)
    val presentation = WearStrengthWorkoutPresentationFactory.from(awaiting)

    assertTrue(presentation.canLogRepetitions)
    assertFalse(presentation.canFinish)
  }

  private fun prepared() =
      StrengthSession.prepare("plan", "entry", workout(), 1_000L)

  private fun workout(timedSeconds: Int? = null, sets: Int = 3, restSeconds: Int = 0) =
      RelvenStrengthWorkoutParser.parse(
          """{"schemaVersion":3,"id":"test","name":"Styrka","sport":"strength","steps":[
            {"id":"push","kind":"segment","phase":"work","end":${if (timedSeconds == null) "{\"type\":\"manual\"}" else "{\"type\":\"time\",\"seconds\":$timedSeconds}"},
             "exercise":{"exerciseId":"00000000-0000-0000-0000-000000000001","libraryRevision":1,
              "exerciseRevision":1,"snapshot":{"name":"Väggarmhävning","instructions":"Kontrollerat.","equipment":[],"images":[]},
              "sets":$sets,"minRepetitions":1,"maxRepetitions":20,"restSeconds":$restSeconds}}
          ]}""")
}
