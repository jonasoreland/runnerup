package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearWorkoutDisplayTest {
  @Test
  fun newHeaderSnapshotRemovesPhaseThatIsNoLongerPresent() {
    val previous = mutableMapOf("HEADER/0.0" to "time", "CURRENT_STEP" to "uppvärmning")
    val current = mapOf("HEADER/0.0" to "time")

    replaceWearHeaderSnapshot(previous, current)

    assertEquals(current, previous)
  }

  @Test
  fun mapsRunnerUpMetricsInConfiguredOrder() {
    val display =
        wearWorkoutDisplay(
            headers =
                mapOf(
                    "HEADER/0.0" to "time",
                    "HEADER/0.1" to "distance",
                    "HEADER/0.2" to "Lap pace",
                    "CURRENT_STEP" to "uppvärmning"),
            data =
                mapOf(
                    "DATA/0.0" to "04:57",
                    "DATA/0.1" to "0.12 km",
                    "DATA/0.2" to "15′32″/km"),
            screen = 0,
            rowCount = 3,
            isPaused = false)

    assertEquals("uppvärmning", display.phaseLabel)
    assertEquals(
        listOf(
            WearMetric("time", "04:57"),
            WearMetric("distance", "0.12 km"),
            WearMetric("Lap pace", "15′32″/km")),
        display.metrics)
    assertFalse(display.isPaused)
  }

  @Test
  fun missingMetricValueUsesPlaceholderAndPausedStateIsRetained() {
    val display =
        wearWorkoutDisplay(
            headers = mapOf("HEADER/1.0" to "time", "CURRENT_STEP" to "vila"),
            data = emptyMap(),
            screen = 1,
            rowCount = 1,
            isPaused = true)

    assertEquals(listOf(WearMetric("time", "—")), display.metrics)
    assertEquals("vila", display.phaseLabel)
    assertTrue(display.isPaused)
  }
}
