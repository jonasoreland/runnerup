package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDestinationTest {
  @Test
  fun restoresNewAndExistingDestinationsAndDefaultsUnknownValues() {
    assertEquals(HomeDestination.Today, homeDestinationFromSaved(null))
    assertEquals(HomeDestination.Plan, homeDestinationFromSaved("Plan"))
    assertEquals(HomeDestination.Exercises, homeDestinationFromSaved("Exercises"))
    assertEquals(HomeDestination.Progress, homeDestinationFromSaved("Progress"))
    assertEquals(HomeDestination.History, homeDestinationFromSaved("History"))
    assertEquals(HomeDestination.Today, homeDestinationFromSaved("unknown"))
  }
}
