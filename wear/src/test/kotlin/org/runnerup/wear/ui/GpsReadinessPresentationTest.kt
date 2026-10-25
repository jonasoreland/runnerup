package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.runnerup.common.wear.GpsPreparationProtocol

class GpsReadinessPresentationTest {
  @Test
  fun missingReadinessNeverClaimsGpsIsReady() {
    assertEquals("Inväntar GPS-status", WearGpsReadinessPresentation.label(null))
  }

  @Test
  fun everyPhoneReadinessStateHasAnHonestWatchLabel() {
    val labels =
      GpsPreparationProtocol.Status.values().associateWith { status ->
        WearGpsReadinessPresentation.label(readiness(status))
      }

    assertEquals("GPS behövs inte", labels[GpsPreparationProtocol.Status.NOT_REQUIRED])
    assertEquals("Platsåtkomst saknas", labels[GpsPreparationProtocol.Status.PERMISSION_REQUIRED])
    assertEquals("GPS otillgängligt", labels[GpsPreparationProtocol.Status.UNAVAILABLE])
    assertEquals("Plats är avstängd", labels[GpsPreparationProtocol.Status.DISABLED])
    assertEquals("Söker GPS-signal", labels[GpsPreparationProtocol.Status.SEARCHING])
    assertEquals("GPS-position mottagen", labels[GpsPreparationProtocol.Status.FIX_ACQUIRED])
    assertEquals("GPS svag · ±18 m", labels[GpsPreparationProtocol.Status.ACCURACY_INSUFFICIENT])
    assertEquals("GPS redo · ±8 m", labels[GpsPreparationProtocol.Status.READY])
  }

  private fun readiness(status: GpsPreparationProtocol.Status) =
    GpsPreparationProtocol.decode(
      GpsPreparationProtocol.encode(
        "11111111-1111-4111-8111-111111111111",
        1L,
        status,
        if (status == GpsPreparationProtocol.Status.ACCURACY_INSUFFICIENT) 18f
        else if (status == GpsPreparationProtocol.Status.READY) 8f
        else null,
      ),
    )
}
