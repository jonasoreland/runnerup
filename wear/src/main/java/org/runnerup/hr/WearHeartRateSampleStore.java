package org.runnerup.hr;

/** Keeps the latest watch sensor sample available to the active workout glance in this process. */
public final class WearHeartRateSampleStore {
  private static WearHeartRateSample latest;
  private static boolean captureActive;
  private static long captureStartedAtElapsedRealtime;

  private WearHeartRateSampleStore() {}

  public static synchronized boolean record(int bpm, long capturedAtElapsedRealtime) {
    if (bpm < 30 || bpm > 240 || capturedAtElapsedRealtime < 0) {
      return false;
    }
    if (!captureActive) {
      return false;
    }
    latest = new WearHeartRateSample(bpm, capturedAtElapsedRealtime);
    return true;
  }

  public static synchronized WearHeartRateSample getLatest() {
    return latest;
  }

  public static synchronized boolean isCaptureActive() {
    return captureActive;
  }

  public static synchronized void beginCapture(long capturedAtElapsedRealtime) {
    latest = null;
    captureStartedAtElapsedRealtime = Math.max(0L, capturedAtElapsedRealtime);
    captureActive = true;
  }

  public static synchronized void endCapture() {
    captureActive = false;
    captureStartedAtElapsedRealtime = 0L;
    latest = null;
  }

  public static synchronized long getCaptureStartedAtElapsedRealtime() {
    return captureStartedAtElapsedRealtime;
  }
}
