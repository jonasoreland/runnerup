package org.runnerup.relven.ui

/** Limits live map snapshots while leaving Tracker events and route-state ingestion untouched. */
internal class LiveRouteRenderGate(private val minimumIntervalMs: Long = 500L) {
  private var lastRenderAtMs: Long? = null

  fun shouldRender(nowMs: Long): Boolean {
    if (nowMs < 0L) return false
    val last = lastRenderAtMs
    if (last == null) {
      lastRenderAtMs = nowMs
      return true
    }
    if (nowMs < last || nowMs - last < minimumIntervalMs) return false
    lastRenderAtMs = nowMs
    return true
  }

  fun reset() {
    lastRenderAtMs = null
  }
}
