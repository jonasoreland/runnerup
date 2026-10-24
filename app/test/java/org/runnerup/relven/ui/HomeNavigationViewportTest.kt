package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationViewportTest {
  @Test
  fun contentInsetTracksMeasuredNavigationAndSystemInsetTogether() {
    assertEquals(136, homeNavigationContentObstructionPx(rootHeightPx = 2_400, navigationTopPx = 2_264))
  }

  @Test
  fun obstructionUpdatesWhenAvailableWindowHeightChanges() {
    assertEquals(112, homeNavigationContentObstructionPx(rootHeightPx = 2_000, navigationTopPx = 1_888))
    assertEquals(152, homeNavigationContentObstructionPx(rootHeightPx = 1_700, navigationTopPx = 1_548))
  }

  @Test
  fun hiddenNavigationHasNoObstructionAndInvalidCoordinatesClampToZero() {
    assertEquals(0, homeNavigationContentObstructionPx(rootHeightPx = 2_000, navigationTopPx = null))
    assertEquals(0, homeNavigationContentObstructionPx(rootHeightPx = 2_000, navigationTopPx = 2_100))
  }
}
