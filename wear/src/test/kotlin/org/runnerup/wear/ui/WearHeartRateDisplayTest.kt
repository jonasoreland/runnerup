package org.runnerup.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.hr.WearHeartRateSample
import org.runnerup.hr.WearHeartRateSampleStore
import org.runnerup.common.tracker.TrackerState

class WearHeartRateDisplayTest {
  @Test
  fun heartRateIsVisibleOnlyDuringActiveOrPausedCapture() {
    assertTrue(shouldShowWearHeartRate(true, TrackerState.STARTED))
    assertTrue(shouldShowWearHeartRate(true, TrackerState.PAUSED))
    assertFalse(shouldShowWearHeartRate(true, TrackerState.STOPPED))
    assertFalse(shouldShowWearHeartRate(true, TrackerState.CONNECTED))
    assertFalse(shouldShowWearHeartRate(true, null))
    assertFalse(shouldShowWearHeartRate(false, TrackerState.STARTED))
  }

  @Test
  fun noSampleShowsWaitingState() {
    val display =
        wearHeartRateDisplay(
            sample = null,
            nowElapsedRealtime = 20_000L,
            captureStartedAtElapsedRealtime = 10_000L)

    assertEquals(WearHeartRateStatus.WAITING, display.status)
    assertNull(display.bpm)
  }

  @Test
  fun captureWithoutFirstReadingBecomesUnavailableAfterFifteenSeconds() {
    val display =
        wearHeartRateDisplay(
            sample = null,
            nowElapsedRealtime = 25_001L,
            captureStartedAtElapsedRealtime = 10_000L)

    assertEquals(WearHeartRateStatus.UNAVAILABLE, display.status)
    assertNull(display.bpm)
  }

  @Test
  fun sampleRemainsLiveThroughFifteenSecondFreshnessWindow() {
    val display =
        wearHeartRateDisplay(
            sample = WearHeartRateSample(142, 10_000L), nowElapsedRealtime = 25_000L)

    assertEquals(WearHeartRateStatus.LIVE, display.status)
    assertEquals(142, display.bpm)
  }

  @Test
  fun expiredSampleIsStaleAndDoesNotExposeOldBpm() {
    val display =
        wearHeartRateDisplay(
            sample = WearHeartRateSample(142, 10_000L), nowElapsedRealtime = 25_001L)

    assertEquals(WearHeartRateStatus.STALE, display.status)
    assertNull(display.bpm)
  }

  @Test
  fun rejectsFutureSampleTimestamps() {
    val display =
        wearHeartRateDisplay(
            sample = WearHeartRateSample(142, 20_001L), nowElapsedRealtime = 20_000L)

    assertEquals(WearHeartRateStatus.WAITING, display.status)
    assertNull(display.bpm)
  }

  @Test
  fun sampleStoreAcceptsOnlyPlausibleBpmAndCanClear() {
    WearHeartRateSampleStore.endCapture()
    try {
      assertFalse(WearHeartRateSampleStore.record(142, 9_000L))
      WearHeartRateSampleStore.beginCapture(10_000L)
      assertTrue(WearHeartRateSampleStore.isCaptureActive())
      assertTrue(WearHeartRateSampleStore.record(142, 10_000L))
      assertEquals(142, WearHeartRateSampleStore.getLatest().bpm)
      assertFalse(WearHeartRateSampleStore.record(29, 11_000L))
      assertEquals(142, WearHeartRateSampleStore.getLatest().bpm)
      WearHeartRateSampleStore.endCapture()
      assertFalse(WearHeartRateSampleStore.isCaptureActive())
      assertNull(WearHeartRateSampleStore.getLatest())
    } finally {
      WearHeartRateSampleStore.endCapture()
    }
  }

  @Test
  fun workoutDisplayHidesHeartRateOutsideWatchCapture() {
    val display =
        wearWorkoutDisplay(
            headers = emptyMap(),
            data = emptyMap(),
            screen = 0,
            rowCount = 0,
            isPaused = false,
            heartRateSample = WearHeartRateSample(142, 10_000L),
            nowElapsedRealtime = 10_000L,
            heartRateCaptureActive = false)

    assertNull(display.heartRate)
  }
}
