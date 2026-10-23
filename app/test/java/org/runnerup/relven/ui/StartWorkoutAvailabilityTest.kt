package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartWorkoutAvailabilityTest {
  @Test
  fun gpsWorkoutRequiresStartedLoggingAndFixedGpsAndConnectedTracker() {
    assertTrue(
      StartWorkoutAvailability.isAvailable(
        gpsStarted = true,
        sportWithoutGps = false,
        gpsLogging = true,
        gpsFixed = true,
        trackerConnected = true,
        advancedSelected = false,
        advancedWorkoutAvailable = false,
      ),
    )
    assertFalse(availability(gpsStarted = false))
    assertFalse(availability(gpsLogging = false))
    assertFalse(availability(gpsFixed = false))
    assertFalse(availability(trackerConnected = false))
  }

  @Test
  fun gpsFreeSportDoesNotRequireGpsLoggingOrFix() {
    assertTrue(
      StartWorkoutAvailability.isAvailable(
        gpsStarted = true,
        sportWithoutGps = true,
        gpsLogging = false,
        gpsFixed = false,
        trackerConnected = true,
        advancedSelected = false,
        advancedWorkoutAvailable = false,
      ),
    )
  }

  @Test
  fun advancedTabRequiresSelectedWorkout() {
    assertFalse(availability(advancedSelected = true, advancedWorkoutAvailable = false))
    assertTrue(availability(advancedSelected = true, advancedWorkoutAvailable = true))
  }

  private fun availability(
    gpsStarted: Boolean = true,
    sportWithoutGps: Boolean = false,
    gpsLogging: Boolean = true,
    gpsFixed: Boolean = true,
    trackerConnected: Boolean = true,
    advancedSelected: Boolean = false,
    advancedWorkoutAvailable: Boolean = false,
  ) =
    StartWorkoutAvailability.isAvailable(
      gpsStarted,
      sportWithoutGps,
      gpsLogging,
      gpsFixed,
      trackerConnected,
      advancedSelected,
      advancedWorkoutAvailable,
    )
}
