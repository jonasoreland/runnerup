package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayPlanActionTest {
  @Test
  fun emptyPlanCardOpensPlanLibrary() {
    assertEquals(TodayPlanAction.CreateOrManagePlans, todayPlanAction(TodayPlanState.NoActivePlan))
  }

  @Test
  fun activePlanCardOpensItsCalendar() {
    assertEquals(
        TodayPlanAction.ManageActivePlan,
        todayPlanAction(TodayPlanState.NoPlannedItem("Base plan")))
    assertEquals(
        TodayPlanAction.ManageActivePlan,
        todayPlanAction(TodayPlanState.RestDay("Base plan", "Easy day")))
    assertEquals(
        TodayPlanAction.ManageActivePlan,
        todayPlanAction(TodayPlanState.PlannedWorkout("Base plan", "Easy run", "")))
  }

  @Test
  fun loadingAndUnavailablePlanCardsDoNotOfferNavigation() {
    assertNull(todayPlanAction(TodayPlanState.Loading))
    assertNull(todayPlanAction(TodayPlanState.Unavailable))
  }
}
