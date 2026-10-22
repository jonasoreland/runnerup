package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingPlanRecoveryTest {
  @Test
  fun savesWhenTheSubmittedPlanIsNotInTheLibraryYet() {
    assertEquals(
        PendingPlanSaveAction.Save,
        pendingPlanSaveAction("plan-1", "{source}", null, null))
  }

  @Test
  fun reusesOnlyTheExactPreviouslySavedPackage() {
    assertEquals(
        PendingPlanSaveAction.Reuse,
        pendingPlanSaveAction("plan-1", "{source}", "plan-1", "{source}"))
  }

  @Test
  fun rejectsDifferentContentWithTheSamePlanId() {
    assertEquals(
        PendingPlanSaveAction.Conflict,
        pendingPlanSaveAction("plan-1", "{source}", "plan-1", "{other}"))
  }
}
