package org.runnerup.wear.ui

import org.runnerup.hr.WearHeartRateSample
import org.runnerup.common.tracker.TrackerState

internal enum class WearHeartRateStatus {
  WAITING,
  LIVE,
  STALE,
  UNAVAILABLE
}

internal data class WearHeartRateDisplay(val status: WearHeartRateStatus, val bpm: Int?)

private const val HEART_RATE_FRESHNESS_MILLIS = 15_000L

internal fun shouldShowWearHeartRate(captureActive: Boolean, trackerState: TrackerState?): Boolean =
    captureActive && (trackerState == TrackerState.STARTED || trackerState == TrackerState.PAUSED)

internal fun wearHeartRateDisplay(
    sample: WearHeartRateSample?,
    nowElapsedRealtime: Long,
    captureStartedAtElapsedRealtime: Long = nowElapsedRealtime
): WearHeartRateDisplay {
  if (sample == null) {
    val captureAge = nowElapsedRealtime - captureStartedAtElapsedRealtime
    val status =
        if (captureStartedAtElapsedRealtime <= nowElapsedRealtime &&
            captureAge > HEART_RATE_FRESHNESS_MILLIS) {
          WearHeartRateStatus.UNAVAILABLE
        } else {
          WearHeartRateStatus.WAITING
        }
    return WearHeartRateDisplay(status, null)
  }

  if (sample.capturedAtElapsedRealtime > nowElapsedRealtime) {
    return WearHeartRateDisplay(WearHeartRateStatus.WAITING, null)
  }

  val age = nowElapsedRealtime - sample.capturedAtElapsedRealtime
  if (age > HEART_RATE_FRESHNESS_MILLIS) {
    return WearHeartRateDisplay(WearHeartRateStatus.STALE, null)
  }

  return WearHeartRateDisplay(WearHeartRateStatus.LIVE, sample.bpm)
}
