package org.runnerup.relven.ui

/** Waits for the route style and measured map viewport before issuing one saved-route fit. */
internal class InitialRouteFitGate {
  private var styleReady = false
  private var viewportReady = false
  private var routeAvailable = false
  private var fitIssued = false

  fun onStyleLoaded(routePointCount: Int): Boolean {
    styleReady = true
    routeAvailable = routePointCount >= 2
    return takeFitIfReady()
  }

  fun onViewportChanged(width: Int, height: Int): Boolean {
    viewportReady = width > 0 && height > 0
    return takeFitIfReady()
  }

  fun markFitIssued() {
    if (canFit()) fitIssued = true
  }

  private fun takeFitIfReady(): Boolean {
    return canFit()
  }

  private fun canFit(): Boolean =
      !fitIssued && styleReady && viewportReady && routeAvailable
}
