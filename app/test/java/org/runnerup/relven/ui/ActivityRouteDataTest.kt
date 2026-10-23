package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRouteDataTest {
  @Test
  fun buildsSegmentsWithoutCrossingLapBoundariesAndUsesGeoJsonCoordinateOrder() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, lap = 1, elapsedSeconds = 0.0, distanceMeters = 0.0),
            RoutePoint(59.1, 18.1, lap = 1, elapsedSeconds = 60.0, distanceMeters = 200.0),
            RoutePoint(59.2, 18.2, lap = 2, elapsedSeconds = 120.0, distanceMeters = 400.0))

    val geoJson = buildRouteGeoJson(route, RouteMetric.PACE)

    assertTrue(geoJson.contains("\"coordinates\":[[18.0000000,59.0000000],[18.1000000,59.1000000]]"))
    assertFalse(geoJson.contains("[18.1000000,59.1000000],[18.2000000,59.2000000]"))
  }

  @Test
  fun buildsSegmentsWithoutConnectingAcrossPauseBoundaries() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, 1, 0.0, 0.0, segment = 0),
            RoutePoint(59.1, 18.1, 1, 60.0, 200.0, segment = 0),
            RoutePoint(59.2, 18.2, 1, 120.0, 400.0, segment = 1))

    val geoJson = buildRouteGeoJson(route, RouteMetric.PACE)

    assertTrue(geoJson.contains("[18.0000000,59.0000000],[18.1000000,59.1000000]"))
    assertFalse(geoJson.contains("[18.1000000,59.1000000],[18.2000000,59.2000000]"))
  }

  @Test
  fun paceIsDerivedFromActiveTimeAndCumulativeDistance() {
    val first = RoutePoint(59.0, 18.0, 1, 0.0, 0.0)
    val second = RoutePoint(59.0, 18.1, 1, 300.0, 1000.0)
    assertEquals(5.0, RouteMetric.PACE.value(second, first)!!, 0.0001)
  }

  @Test
  fun reportsMetricUnavailableWhenFewerThanTwoSamplesHaveValues() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, 1, 0.0, 0.0, heartRate = 120.0),
            RoutePoint(59.1, 18.1, 1, 10.0, 20.0),
            RoutePoint(59.2, 18.2, 1, 20.0, 40.0))
    assertFalse(RouteMetric.HEART_RATE.isAvailable(route))
    assertTrue(RouteMetric.PACE.isAvailable(route))
  }

  @Test
  fun standardMetricUsesStitchBlueAndNeedsTwoRoutePoints() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, 1, 0.0, 0.0),
            RoutePoint(59.1, 18.1, 1, 60.0, 200.0),
            RoutePoint(59.2, 18.2, 1, 120.0, 400.0))

    assertFalse(RouteMetric.STANDARD.isAvailable(route.take(1)))
    assertTrue(RouteMetric.STANDARD.isAvailable(route))
    assertFalse(
        RouteMetric.STANDARD.isAvailable(
            route.mapIndexed { index, point -> point.copy(segment = index) }))
    assertEquals("#2979FF", routeMetricColor(RouteMetric.STANDARD, null, null))
    assertTrue(buildRouteGeoJson(route, RouteMetric.STANDARD).contains("\"color\":\"#2979FF\""))
  }

  @Test
  fun emitsDistinctStartAndFinishRouteMarkers() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, 1, 0.0, 0.0),
            RoutePoint(59.1, 18.1, 1, 60.0, 200.0),
            RoutePoint(59.2, 18.2, 1, 120.0, 400.0))

    val markers = buildRouteMarkersGeoJson(route)

    assertTrue(markers.contains("\"kind\":\"start\""))
    assertTrue(markers.contains("\"kind\":\"finish\""))
    assertTrue(markers.contains("[18.0000000,59.0000000]"))
    assertTrue(markers.contains("[18.2000000,59.2000000]"))
    assertEquals(2, "\"type\":\"Feature\"".toRegex().findAll(markers).count())
  }

  @Test
  fun marksCoincidentEndpointsSoBothClosedRouteMarkersRemainVisible() {
    val route =
        listOf(
            RoutePoint(59.0, 18.0, 1, 0.0, 0.0),
            RoutePoint(59.1, 18.1, 1, 60.0, 200.0),
            RoutePoint(59.0, 18.0, 1, 120.0, 400.0))

    val markers = buildRouteMarkersGeoJson(route)

    assertEquals(2, "\"type\":\"Feature\"".toRegex().findAll(markers).count())
    assertEquals(2, "\"coincident\":true".toRegex().findAll(markers).count())
    assertTrue(markers.contains("\"kind\":\"start\""))
    assertTrue(markers.contains("\"kind\":\"finish\""))
  }

  @Test
  fun routeMarkersSkipInvalidCoordinatesAndRequireTwoPoints() {
    val route =
        listOf(
            RoutePoint(Double.NaN, 18.0, 1, 0.0, 0.0),
            RoutePoint(59.1, 18.1, 1, 60.0, 200.0),
            RoutePoint(59.2, 18.2, 1, 120.0, 400.0),
            RoutePoint(91.0, 18.3, 1, 180.0, 600.0))

    val markers = buildRouteMarkersGeoJson(route)

    assertTrue(markers.contains("[18.1000000,59.1000000]"))
    assertTrue(markers.contains("[18.2000000,59.2000000]"))
    assertEquals(
        "{\"type\":\"FeatureCollection\",\"features\":[]}",
        buildRouteMarkersGeoJson(route.take(1)))
  }
}
