package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMapFollowPolicyTest {
  @Test
  fun firstLocationRequestsInitialCenterAndRunningZoom() {
    val policy = LiveMapFollowPolicy()

    assertEquals(LiveMapFollowAction.INITIAL_CENTER, policy.nextAction(point(59.33, 18.06)))
  }

  @Test
  fun waitsForMeaningfulDisplacementThenRequestsSmoothFollow() {
    val policy = LiveMapFollowPolicy()
    val start = point(59.33, 18.06)

    assertEquals(LiveMapFollowAction.INITIAL_CENTER, policy.nextAction(start))
    assertEquals(LiveMapFollowAction.NONE, policy.nextAction(point(59.33009, 18.06)))
    assertEquals(LiveMapFollowAction.ANIMATE, policy.nextAction(point(59.33013, 18.06)))
    assertEquals(LiveMapFollowAction.NONE, policy.nextAction(point(59.33022, 18.06)))
  }

  @Test
  fun recenterSetsTheCameraAnchorForSubsequentFollowUpdates() {
    val policy = LiveMapFollowPolicy()
    policy.setCameraCenter(point(59.33, 18.06))

    assertEquals(LiveMapFollowAction.NONE, policy.nextAction(point(59.33009, 18.06)))
    assertEquals(LiveMapFollowAction.ANIMATE, policy.nextAction(point(59.33014, 18.06)))
  }

  @Test
  fun rejectsInvalidCoordinatesWithoutMovingTheCameraAnchor() {
    val policy = LiveMapFollowPolicy()
    assertEquals(LiveMapFollowAction.INITIAL_CENTER, policy.nextAction(point(59.33, 18.06)))

    assertEquals(LiveMapFollowAction.NONE, policy.nextAction(point(Double.NaN, 18.06)))
    assertEquals(LiveMapFollowAction.NONE, policy.nextAction(point(59.33009, 18.06)))
  }

  private fun point(latitude: Double, longitude: Double) =
      RoutePoint(latitude, longitude, lap = 0, elapsedSeconds = 0.0, distanceMeters = 0.0)
}
