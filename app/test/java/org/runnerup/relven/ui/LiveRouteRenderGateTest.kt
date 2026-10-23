package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRouteRenderGateTest {
  @Test
  fun rendersImmediatelyThenCoalescesSnapshotsToTwoPerSecond() {
    val gate = LiveRouteRenderGate()

    assertTrue(gate.shouldRender(1_000L))
    assertFalse(gate.shouldRender(1_100L))
    assertFalse(gate.shouldRender(1_499L))
    assertTrue(gate.shouldRender(1_500L))
    assertFalse(gate.shouldRender(1_750L))
    assertTrue(gate.shouldRender(2_000L))
  }

  @Test
  fun ignoresClockRegressionWithoutMovingTheLastRenderAnchor() {
    val gate = LiveRouteRenderGate()

    assertTrue(gate.shouldRender(1_000L))
    assertFalse(gate.shouldRender(900L))
    assertFalse(gate.shouldRender(1_499L))
    assertTrue(gate.shouldRender(1_500L))
  }

  @Test
  fun resetAllowsImmediateRenderAfterLifecycleRefresh() {
    val gate = LiveRouteRenderGate()
    assertTrue(gate.shouldRender(1_000L))
    assertFalse(gate.shouldRender(1_100L))

    gate.reset()

    assertTrue(gate.shouldRender(1_100L))
  }
}
