package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.relven.strength.StrengthSession

class StrengthTimerFeedbackTest {
  @Test
  fun signalsOnceWhenTimedWorkMovesIntoAnyNonWorkPhase() {
    assertTrue(shouldSignalTimedWorkFinished(StrengthSession.Phase.TIMED_WORK, StrengthSession.Phase.REST))
    assertTrue(shouldSignalTimedWorkFinished(StrengthSession.Phase.TIMED_WORK, StrengthSession.Phase.AWAITING_RESULT))
    assertFalse(shouldSignalTimedWorkFinished(StrengthSession.Phase.TIMED_WORK, StrengthSession.Phase.TIMED_WORK))
    assertFalse(shouldSignalTimedWorkFinished(StrengthSession.Phase.TIMED_WORK, StrengthSession.Phase.PAUSED))
    assertFalse(shouldSignalTimedWorkFinished(StrengthSession.Phase.REST, StrengthSession.Phase.TIMED_WORK))
    assertFalse(shouldSignalTimedWorkFinished(StrengthSession.Phase.REPETITIONS, StrengthSession.Phase.REST))
  }
}
