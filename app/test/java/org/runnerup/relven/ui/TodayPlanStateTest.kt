package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.workout.RelvenPlanImporter

class TodayPlanStateTest {
  private val plan =
      RelvenPlanImporter.parse(
          """
          {"format":"relven.plan","schemaVersion":1,"id":"plan-test","name":"Base plan",
          "timezone":"Europe/Stockholm","workouts":[{"schemaVersion":3,"id":"run-a",
          "name":"Easy run","sport":"running","steps":[{"id":"step-a","kind":"segment",
          "phase":"work","end":{"type":"time","seconds":1800}}]}],"days":[
          {"id":"today-run","date":"2026-10-20","kind":"workout","workoutId":"run-a","notes":"Keep it easy"},
          {"id":"rest-day","date":"2026-10-21","kind":"rest"}]}
          """.trimIndent())

  @Test
  fun resolvesWorkoutInPlanTimezoneAtUtcDateBoundary() {
    val state = todayPlanState(plan, 1_792_449_000_000L) // 2026-10-19 22:30 UTC

    assertTrue(state is TodayPlanState.PlannedWorkout)
    state as TodayPlanState.PlannedWorkout
    assertEquals("Base plan", state.planName)
    assertEquals("Easy run", state.workoutName)
    assertEquals("Keep it easy", state.notes)
  }

  @Test
  fun resolvesRestDayWithoutWorkoutName() {
    val state = todayPlanState(plan, 1_792_535_400_000L) // 2026-10-20 22:30 UTC

    assertTrue(state is TodayPlanState.RestDay)
    assertEquals("Base plan", (state as TodayPlanState.RestDay).planName)
  }

  @Test
  fun distinguishesDateWithoutPlanItem() {
    val state = todayPlanState(plan, 1_792_621_800_000L) // 2026-10-21 22:30 UTC

    assertTrue(state is TodayPlanState.NoPlannedItem)
  }

  @Test
  fun returnsNoActivePlanWhenSelectionIsEmpty() {
    assertEquals(TodayPlanState.NoActivePlan, todayPlanState(null, 1_792_449_000_000L))
  }
}
