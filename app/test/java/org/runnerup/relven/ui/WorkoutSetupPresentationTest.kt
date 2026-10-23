package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetupPresentationTest {
  @Test
  fun simpleGoalModesPreserveLegacyPaceAndSeparateEndConditions() {
    assertEquals("none", WorkoutSetupPresentation.goalTypeFromPreferences(null, -1))
    assertEquals("pace", WorkoutSetupPresentation.goalTypeFromPreferences(null, 4))
    assertEquals("heart_rate", WorkoutSetupPresentation.goalTypeFromPreferences(null, 6))
    assertEquals("time", WorkoutSetupPresentation.goalTypeFromPreferences("time", 4))
    assertEquals("distance", WorkoutSetupPresentation.goalTypeFromPreferences("distance", -1))

    assertEquals(-1, WorkoutSetupPresentation.targetDimensionForGoal("none"))
    assertEquals(4, WorkoutSetupPresentation.targetDimensionForGoal("pace"))
    assertEquals(6, WorkoutSetupPresentation.targetDimensionForGoal("heart_rate"))
    assertEquals(-1, WorkoutSetupPresentation.targetDimensionForGoal("time"))
    assertEquals(-1, WorkoutSetupPresentation.targetDimensionForGoal("distance"))
  }

  @Test
  fun simpleGoalControlsSelectOnlyTheirRelevantValue() {
    assertTrue(WorkoutSetupPresentation.showsGoalTime("time"))
    assertFalse(WorkoutSetupPresentation.showsGoalTime("pace"))
    assertTrue(WorkoutSetupPresentation.showsGoalDistance("distance"))
    assertFalse(WorkoutSetupPresentation.showsGoalDistance("none"))
    assertTrue(WorkoutSetupPresentation.showsPace("pace"))
    assertFalse(WorkoutSetupPresentation.showsPace("time"))
    assertTrue(WorkoutSetupPresentation.showsHeartRate("heart_rate", zonesConfigured = true))
    assertFalse(WorkoutSetupPresentation.showsHeartRate("heart_rate", zonesConfigured = false))
  }

  @Test
  fun targetControlsFollowRunnerUpDimensionValues() {
    assertFalse(WorkoutSetupPresentation.showsPace(-1))
    assertTrue(WorkoutSetupPresentation.showsPace(4))
    assertFalse(WorkoutSetupPresentation.showsPace(6))
    assertTrue(WorkoutSetupPresentation.showsHeartRate(6, zonesConfigured = true))
    assertFalse(WorkoutSetupPresentation.showsHeartRate(6, zonesConfigured = false))
    assertFalse(WorkoutSetupPresentation.showsHeartRate(4, zonesConfigured = true))
  }

  @Test
  fun intervalControlsFollowRunnerUpTimeDistanceValues() {
    assertTrue(WorkoutSetupPresentation.showsActiveTime(0))
    assertFalse(WorkoutSetupPresentation.showsActiveTime(1))
    assertTrue(WorkoutSetupPresentation.showsRestTime(0))
    assertFalse(WorkoutSetupPresentation.showsRestTime(1))
  }

  @Test
  fun setupInputsAcceptOnlyPositiveValuesAndWellFormedTimes() {
    assertTrue(WorkoutSetupInput.isPositive("0.25"))
    assertTrue(WorkoutSetupInput.isPositive("5,5"))
    assertFalse(WorkoutSetupInput.isPositive("0"))
    assertFalse(WorkoutSetupInput.isPositive("-5"))
    assertFalse(WorkoutSetupInput.isPositive("distance"))
    assertTrue(WorkoutSetupInput.isTime("00:04:00"))
    assertTrue(WorkoutSetupInput.isTime("4:00"))
    assertFalse(WorkoutSetupInput.isTime("00:00:00"))
    assertFalse(WorkoutSetupInput.isTime("00:00"))
    assertFalse(WorkoutSetupInput.isTime("00:71:00"))
    assertFalse(WorkoutSetupInput.isTime("not a time"))
  }

  @Test
  fun targetRangeShowsBothDifferentEndpoints() {
    assertEquals("5:00", WorkoutSetupPresentation.formatRange("5:00", "5:00"))
    assertEquals("4:45–5:00", WorkoutSetupPresentation.formatRange("4:45", "5:00"))
  }
}
