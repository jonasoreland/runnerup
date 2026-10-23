package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMapFollowPolicyTest {
  @Test
  fun followsInitiallyThenWaitsForMeaningfulDisplacement() {
    val policy = LiveMapFollowPolicy()
    val start = point(59.33, 18.06)

    assertTrue(policy.shouldFollow(start))
    assertFalse(policy.shouldFollow(point(59.33009, 18.06)))
    assertTrue(policy.shouldFollow(point(59.33013, 18.06)))
    assertFalse(policy.shouldFollow(point(59.33022, 18.06)))
  }

  @Test
  fun recenterSetsTheCameraAnchorForSubsequentFollowUpdates() {
    val policy = LiveMapFollowPolicy()
    policy.setCameraCenter(point(59.33, 18.06))

    assertFalse(policy.shouldFollow(point(59.33009, 18.06)))
    assertTrue(policy.shouldFollow(point(59.33014, 18.06)))
  }

  @Test
  fun rejectsInvalidCoordinatesWithoutMovingTheCameraAnchor() {
    val policy = LiveMapFollowPolicy()
    assertTrue(policy.shouldFollow(point(59.33, 18.06)))

    assertFalse(policy.shouldFollow(point(Double.NaN, 18.06)))
    assertFalse(policy.shouldFollow(point(59.33009, 18.06)))
  }

  private fun point(latitude: Double, longitude: Double) =
      RoutePoint(latitude, longitude, lap = 0, elapsedSeconds = 0.0, distanceMeters = 0.0)
}
