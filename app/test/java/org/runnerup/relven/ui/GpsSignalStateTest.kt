package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsSignalStateTest {
  @Test
  fun nearZeroOrImplausibleSpeedDoesNotShowCurrentPace() {
    assertEquals("--:--", currentPaceDisplay(0.0, "66:34"))
    assertEquals("--:--", currentPaceDisplay(0.49, "34:00"))
    assertEquals("--:--", currentPaceDisplay(12.01, "1:23"))
    assertEquals("6:00", currentPaceDisplay(2.78, "6:00"))
  }

  @Test
  fun missingOrStaleFixIsReportedAsWaiting() {
    assertEquals("Väntar på GPS", gpsSignalState(null, null, false).label)
    assertEquals("Väntar på GPS", gpsSignalState(3f, 15_001L, true).label)
  }

  @Test
  fun accuracyIsGroupedIntoUsefulSignalLevels() {
    assertEquals("GPS stark · ±6 m", gpsSignalState(6.9f, 1_000L, true).label)
    assertEquals("GPS okej · ±12 m", gpsSignalState(12f, 1_000L, true).label)
    assertEquals("GPS svag · ±18 m", gpsSignalState(18f, 1_000L, true).label)
  }

  @Test
  fun missingAccuracyDoesNotClaimStrongSignal() {
    assertEquals("GPS söker", gpsSignalState(null, 1_000L, true).label)
    assertEquals("GPS söker", gpsSignalState(Float.NaN, 1_000L, true).label)
  }
}
