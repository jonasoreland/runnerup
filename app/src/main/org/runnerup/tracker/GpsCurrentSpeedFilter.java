package org.runnerup.tracker;

/** Keeps current pace useful between stationary GPS fixes without carrying it indefinitely. */
public final class GpsCurrentSpeedFilter {
  private static final long STATIONARY_RESET_NANOS = 10_000_000_000L;

  private double currentSpeedMetersPerSecond;
  private long lastMovementElapsedNanos;

  public double onFix(GpsFixFilter.Result result, long elapsedRealtimeNanos) {
    if (result.startsNewSegment()) {
      reset();
    } else if (result.hasDerivedSpeed()) {
      currentSpeedMetersPerSecond = result.getSpeedMetersPerSecond();
      lastMovementElapsedNanos = elapsedRealtimeNanos;
    } else if (result.isStationary()
        && (lastMovementElapsedNanos <= 0
            || elapsedRealtimeNanos - lastMovementElapsedNanos >= STATIONARY_RESET_NANOS)) {
      currentSpeedMetersPerSecond = 0.0;
    }
    return currentSpeedMetersPerSecond;
  }

  public void reset() {
    currentSpeedMetersPerSecond = 0.0;
    lastMovementElapsedNanos = 0L;
  }
}
