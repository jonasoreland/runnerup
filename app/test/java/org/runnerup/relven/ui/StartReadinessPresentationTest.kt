package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartReadinessPresentationTest {
  private val gps = StartReadinessItem(10, "GPS", "GPS ready")
  private val gpsDetails = StartReadinessItem(10, "GPS", "5 satellites · 4 m")
  private val heartRate = StartReadinessItem(11, "Heart rate", "Waiting for watch")
  private val watch = StartReadinessItem(12, "Wear OS", "?")

  @Test
  fun collapsedSummaryShowsConfiguredDevicesAndGpsWithoutDetails() {
    val presentation =
      StartReadinessPresentationFactory.create(
        gps, gpsDetails, heartRate, watch, detailsExpanded = false, noDevicesMessage = "No devices",
      )

    assertEquals(listOf(heartRate, watch, gps), presentation.summaryItems)
    assertNull(presentation.gpsDetails)
  }

  @Test
  fun expandedSummaryShowsGpsDetailAndNoDeviceMessageWhenNoSensorsAreConfigured() {
    val presentation =
      StartReadinessPresentationFactory.create(
        gps, gpsDetails, null, null, detailsExpanded = true, noDevicesMessage = "No devices",
      )

    assertEquals("No devices", presentation.summaryItems.single().message)
    assertEquals(gpsDetails, presentation.gpsDetails)
  }

  @Test
  fun expandedSummaryKeepsConfiguredDeviceMessagesAndAddsGpsDetail() {
    val presentation =
      StartReadinessPresentationFactory.create(
        gps, gpsDetails, heartRate, null, detailsExpanded = true, noDevicesMessage = "No devices",
      )

    assertEquals(listOf(heartRate), presentation.summaryItems)
    assertEquals(gpsDetails, presentation.gpsDetails)
  }

  @Test
  fun expandedSummaryKeepsGpsOffMessageWithoutAnIcon() {
    val gpsOff = StartReadinessItem(null, null, "GPS off")
    val presentation =
      StartReadinessPresentationFactory.create(
        gpsOff, gpsOff, null, null, detailsExpanded = true, noDevicesMessage = "No devices",
      )

    assertEquals("GPS off", presentation.gpsDetails?.message)
    assertNull(presentation.gpsDetails?.iconResource)
  }
}
