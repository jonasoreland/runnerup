package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartWorkoutAvailabilityTest {
  @Test
  fun startDoesNotRequireGpsPermissionFixOrProvider() {
    assertTrue(availability())
    assertFalse(availability(trackerAvailable = false))
  }

  @Test
  fun advancedTabRequiresSelectedWorkout() {
    assertFalse(availability(advancedSelected = true, advancedWorkoutAvailable = false))
    assertTrue(availability(advancedSelected = true, advancedWorkoutAvailable = true))
  }

  private fun availability(
    trackerAvailable: Boolean = true,
    advancedSelected: Boolean = false,
    advancedWorkoutAvailable: Boolean = false,
  ) =
    StartWorkoutAvailability.isAvailable(
      trackerAvailable,
      advancedSelected,
      advancedWorkoutAvailable,
    )
}
