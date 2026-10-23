package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialRouteFitGateTest {
  @Test
  fun fitsSavedRouteOnlyAfterStyleAndViewportAreReady() {
    val gate = InitialRouteFitGate()

    assertFalse(gate.onStyleLoaded(routePointCount = 3))
    assertFalse(gate.onViewportChanged(width = 0, height = 224))
    assertTrue(gate.onViewportChanged(width = 360, height = 224))
    gate.markFitIssued()
    assertFalse(gate.onViewportChanged(width = 360, height = 224))
  }

  @Test
  fun fitsWhenViewportArrivesBeforeMapStyle() {
    val gate = InitialRouteFitGate()

    assertFalse(gate.onViewportChanged(width = 360, height = 224))
    assertTrue(gate.onStyleLoaded(routePointCount = 2))
    gate.markFitIssued()
    assertFalse(gate.onStyleLoaded(routePointCount = 2))
  }

  @Test
  fun keepsFitPendingUntilCameraRequestIsIssued() {
    val gate = InitialRouteFitGate()
    gate.onStyleLoaded(routePointCount = 2)

    assertTrue(gate.onViewportChanged(width = 360, height = 224))
    assertTrue(gate.onViewportChanged(width = 360, height = 224))

    gate.markFitIssued()

    assertFalse(gate.onViewportChanged(width = 360, height = 224))
  }

  @Test
  fun doesNotFitWhenThereIsNoUsableRoute() {
    val gate = InitialRouteFitGate()

    assertFalse(gate.onViewportChanged(width = 360, height = 224))
    assertFalse(gate.onStyleLoaded(routePointCount = 1))
    assertFalse(gate.onStyleLoaded(routePointCount = 0))
  }
}
