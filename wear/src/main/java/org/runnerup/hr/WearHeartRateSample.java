package org.runnerup.hr;

/** A validated heart-rate sample captured by this watch's sensor. */
public final class WearHeartRateSample {
  private final int bpm;
  private final long capturedAtElapsedRealtime;

  public WearHeartRateSample(int bpm, long capturedAtElapsedRealtime) {
    if (bpm < 30 || bpm > 240) {
      throw new IllegalArgumentException("Heart rate must be between 30 and 240 BPM");
    }
    if (capturedAtElapsedRealtime < 0) {
      throw new IllegalArgumentException("Sample time must not be negative");
    }
    this.bpm = bpm;
    this.capturedAtElapsedRealtime = capturedAtElapsedRealtime;
  }

  public int getBpm() {
    return bpm;
  }

  public long getCapturedAtElapsedRealtime() {
    return capturedAtElapsedRealtime;
  }
}
