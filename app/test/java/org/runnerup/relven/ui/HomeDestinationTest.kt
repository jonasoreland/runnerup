package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDestinationTest {
  @Test
  fun restoresNewAndExistingDestinationsAndDefaultsUnknownValues() {
    assertEquals(HomeDestination.Today, homeDestinationFromSaved(null))
    assertEquals(HomeDestination.Start, homeDestinationFromSaved("Start"))
    assertEquals(HomeDestination.Plan, homeDestinationFromSaved("Plan"))
    assertEquals(HomeDestination.Exercises, homeDestinationFromSaved("Exercises"))
    assertEquals(HomeDestination.Progress, homeDestinationFromSaved("Progress"))
    assertEquals(HomeDestination.History, homeDestinationFromSaved("History"))
    assertEquals(HomeDestination.Today, homeDestinationFromSaved("unknown"))
  }

  @Test
  fun explicitPlanLibraryRequestOpensPlanEvenWhenAnotherDestinationWasSaved() {
    assertEquals(HomeDestination.Plan, homeDestinationFromRequest("Start", openPlanLibrary = true))
    assertEquals(HomeDestination.Start, homeDestinationFromRequest("Start", openPlanLibrary = false))
  }
}
