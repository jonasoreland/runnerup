package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityReviewStateTest {
  @Test
  fun shortActivityDistanceAxisRetainsUsefulPrecision() {
    assertEquals("0.00–0.03", activityChartDistanceRangeLabel(0.0, 0.033, java.util.Locale.US))
    assertEquals("0,00–0,03", activityChartDistanceRangeLabel(0.0, 0.033, java.util.Locale.forLanguageTag("sv-SE")))
  }

  @Test
  fun subTenMeterDistanceAxisUsesMeterScalePrecision() {
    assertEquals("0.000–0.005", activityChartDistanceRangeLabel(0.0, 0.005, java.util.Locale.US))
  }

  @Test
  fun longerActivityDistanceAxisUsesCompactPrecision() {
    assertEquals("0.0–5.2", activityChartDistanceRangeLabel(0.0, 5.234, java.util.Locale.US))
  }

  @Test
  fun onlyUnfinishedReviewOffersResumeSaveAndDiscard() {
    val unfinished = activityReviewActions(ActivityReviewMode.UNFINISHED)
    val saved = activityReviewActions(ActivityReviewMode.SAVED)

    assertTrue(unfinished.canResume)
    assertTrue(unfinished.canSave)
    assertTrue(unfinished.canDiscard)
    assertFalse(saved.canResume)
    assertFalse(saved.canSave)
    assertFalse(saved.canDiscard)
    assertTrue(saved.canShare)
    assertTrue(saved.canEdit)
  }

  @Test
  fun kilometerSplitUsesChronologicalSameSegmentSamples() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0),
            point(distance = 600.0, elapsed = 300.0),
            point(distance = 1_200.0, elapsed = 600.0))

    assertEquals(
        listOf(KilometerSplit(number = 1, durationSeconds = 500.0, paceSecondsPerKm = 500.0)),
        kilometerSplits(route))
  }

  @Test
  fun kilometerSplitDoesNotBridgePausedOrMissingRouteSegments() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0, segment = 0),
            point(distance = 600.0, elapsed = 300.0, segment = 0),
            point(distance = 1_600.0, elapsed = 800.0, segment = 1))

    assertTrue(kilometerSplits(route).isEmpty())
  }

  @Test
  fun kilometerSplitRestartsOneFullKilometerAfterPauseAtPartialDistance() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0, segment = 0),
            point(distance = 600.0, elapsed = 300.0, segment = 0),
            point(distance = 600.0, elapsed = 400.0, segment = 1),
            point(distance = 1_600.0, elapsed = 900.0, segment = 1))

    assertEquals(
        listOf(KilometerSplit(number = 1, durationSeconds = 500.0, paceSecondsPerKm = 500.0)),
        kilometerSplits(route))
  }

  @Test
  fun kilometerSplitDoesNotEmitShortRemainderAtGlobalKilometerMarkerAfterPause() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0, segment = 0),
            point(distance = 600.0, elapsed = 300.0, segment = 0),
            point(distance = 1_600.0, elapsed = 400.0, segment = 1),
            point(distance = 2_000.0, elapsed = 600.0, segment = 1),
            point(distance = 2_600.0, elapsed = 900.0, segment = 1))

    assertEquals(
        listOf(KilometerSplit(number = 1, durationSeconds = 500.0, paceSecondsPerKm = 500.0)),
        kilometerSplits(route))
  }

  @Test
  fun routeLegendIgnoresStationaryPaceOutlierWithoutChangingRoutePoints() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0),
            point(distance = 100.0, elapsed = 30.0),
            point(distance = 101.0, elapsed = 110.0),
            point(distance = 201.0, elapsed = 140.0))

    val range = requireNotNull(routeMetricRange(route, RouteMetric.PACE))

    assertEquals(2, range.count)
    assertEquals(5.0, range.minimum, 0.001)
    assertEquals(5.0, range.maximum, 0.001)
    assertEquals("#808080", routeMetricColor(RouteMetric.PACE, 1_333.3, range))
    assertEquals(listOf(RouteMetric.PACE), activityChartMetrics(route))
    assertEquals(2, activityChartPoints(route, RouteMetric.PACE).size)
    assertEquals(4, route.size)
  }

  @Test
  fun cadenceChartIncludesRecordedValuesAndOmitsImplausibleSpikes() {
    val route =
        listOf(
            point(distance = 0.0, elapsed = 0.0).copy(cadencePerMinute = 174.0),
            point(distance = 100.0, elapsed = 30.0).copy(cadencePerMinute = 176.0),
            point(distance = 200.0, elapsed = 60.0).copy(cadencePerMinute = 500.0))

    assertTrue(RouteMetric.CADENCE in activityChartMetrics(route))
    assertEquals(2, activityChartPoints(route, RouteMetric.CADENCE).size)
    assertEquals(174.0, requireNotNull(routeMetricRange(route, RouteMetric.CADENCE)).minimum, 0.01)
  }

  private fun point(distance: Double, elapsed: Double, segment: Int = 0) =
      RoutePoint(
          latitude = 59.3,
          longitude = 18.0 + distance / 100_000.0,
          lap = 0,
          elapsedSeconds = elapsed,
          distanceMeters = distance,
          segment = segment)
}
