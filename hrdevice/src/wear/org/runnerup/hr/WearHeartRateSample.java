package org.runnerup.hr;

/** Keeps the last plausible watch measurement for at most fifteen seconds. */
final class WearHeartRateSample {
  private static final long MAX_AGE_NANOS = 15_000_000_000L;

  private int bpm;
  private long receivedAtNanos;

  synchronized boolean update(String payload, long nowNanos) {
    final int parsed;
    try {
      parsed = Integer.parseInt(payload);
    } catch (NumberFormatException e) {
      return false;
    }
    if (parsed < 30 || parsed > 240 || nowNanos <= 0) {
      return false;
    }
    bpm = parsed;
    receivedAtNanos = nowNanos;
    return true;
  }

  synchronized int valueAt(long nowNanos) {
    long age = nowNanos - receivedAtNanos;
    return bpm > 0 && age >= 0 && age <= MAX_AGE_NANOS ? bpm : 0;
  }

  synchronized void clear() {
    bpm = 0;
    receivedAtNanos = 0;
  }
}
