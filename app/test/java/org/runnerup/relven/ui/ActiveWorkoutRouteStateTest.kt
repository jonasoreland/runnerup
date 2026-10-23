package org.runnerup.relven.ui

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants.DB

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActiveWorkoutRouteStateTest {
  @Test
  fun keepsAcceptedPointsInChronologicalOrder() {
    val route = ActiveWorkoutRouteState()

    route.onLocation(fix(1, 0.0), DB.LOCATION.TYPE_START, 0.0, 0.0)
    route.onLocation(fix(2, 0.0001), DB.LOCATION.TYPE_GPS, 1.0, 11.0)
    route.onLocation(fix(3, 0.0002), DB.LOCATION.TYPE_GPS, 2.0, 22.0)

    assertEquals(listOf(0.0, 0.0001, 0.0002), route.points.map { it.longitude })
    assertEquals(listOf(0.0, 11.0, 22.0), route.points.map { it.distanceMeters })
  }

  @Test
  fun resumeMarkerStartsNewSegmentWithoutConnectingTheGap() {
    val route = ActiveWorkoutRouteState()
    route.onLocation(fix(1, 0.0), DB.LOCATION.TYPE_START, 0.0, 0.0)
    route.onLocation(fix(2, 0.0001), DB.LOCATION.TYPE_GPS, 1.0, 11.0)

    route.onLocation(fix(3, 0.0010), DB.LOCATION.TYPE_RESUME, 1.0, 11.0)
    route.onLocation(fix(4, 0.0011), DB.LOCATION.TYPE_GPS, 2.0, 22.0)

    assertEquals(listOf(0, 0, 1, 1), route.points.map { it.segment })
    assertFalse(route.points[1].segment == route.points[2].segment)
  }

  @Test
  fun pauseMarkerBreaksRouteWithoutAddingSyntheticPoint() {
    val route = ActiveWorkoutRouteState()
    route.onLocation(fix(1, 0.0), DB.LOCATION.TYPE_START, 0.0, 0.0)
    route.onLocation(fix(2, 0.0001), DB.LOCATION.TYPE_GPS, 1.0, 11.0)

    route.onLocation(fix(3, 0.0001), DB.LOCATION.TYPE_PAUSE, 1.0, 11.0)
    route.onLocation(fix(4, 0.0002), DB.LOCATION.TYPE_GPS, 2.0, 22.0)

    assertEquals(3, route.points.size)
    assertEquals(1, route.points.last().segment)
  }

  @Test
  fun pauseAndResumeMarkersCreateSeparateSegments() {
    val route = ActiveWorkoutRouteState()
    route.onLocation(fix(1, 0.0), DB.LOCATION.TYPE_START, 0.0, 0.0)

    route.onPause()
    route.onResume()
    route.onLocation(fix(2, 0.0001), DB.LOCATION.TYPE_GPS, 2.0, 22.0)

    assertEquals(listOf(0, 2), route.points.map { it.segment })
  }

  @Test
  fun persistedRouteMergesLivePointsWithoutResettingItsSegment() {
    val persisted = RoutePoint(59.0, 18.0, 0, 10.0, 40.0, segment = 3)
    val next = RoutePoint(59.001, 18.001, 0, 20.0, 80.0, segment = 0)

    val merged =
        mergePersistedAndLiveRoute(listOf(persisted), listOf(persisted.copy(), next))

    assertEquals(2, merged.size)
    assertEquals(listOf(3, 3), merged.map { it.segment })
    assertEquals(80.0, merged.last().distanceMeters, 0.0)
  }

  @Test
  fun invalidCoordinatesAndNonIncreasingTimesAreIgnored() {
    val route = ActiveWorkoutRouteState()
    route.onLocation(fix(10, 0.0), DB.LOCATION.TYPE_START, 0.0, 0.0)
    route.onLocation(fix(11, 0.0001), DB.LOCATION.TYPE_GPS, 1.0, 11.0)
    route.onLocation(fix(11, 0.0002), DB.LOCATION.TYPE_GPS, 2.0, 22.0)
    route.onLocation(fix(12, 181.0), DB.LOCATION.TYPE_GPS, 3.0, 33.0)

    assertEquals(2, route.points.size)
    assertTrue(route.points.zipWithNext().all { (a, b) -> b.elapsedSeconds > a.elapsedSeconds })
  }

  private fun fix(elapsedSeconds: Long, longitude: Double): Location =
      Location("gps").apply {
        latitude = 59.33
        this.longitude = longitude
        elapsedRealtimeNanos = elapsedSeconds * 1_000_000_000L
      }
}
