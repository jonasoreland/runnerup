package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetupPresentationTest {
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
    assertFalse(WorkoutSetupInput.isPositive("0"))
    assertFalse(WorkoutSetupInput.isPositive("-5"))
    assertFalse(WorkoutSetupInput.isPositive("distance"))
    assertTrue(WorkoutSetupInput.isTime("00:04:00"))
    assertTrue(WorkoutSetupInput.isTime("4:00"))
    assertFalse(WorkoutSetupInput.isTime("00:71:00"))
    assertFalse(WorkoutSetupInput.isTime("not a time"))
  }
}
