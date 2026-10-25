package org.runnerup.tracker;

/** Classifies GPS readiness only from a measured, plausible horizontal accuracy value. */
public final class GpsFixQuality {
  private static final float MAX_READY_ACCURACY_METERS = 10f;

  private GpsFixQuality() {}

  public static boolean isUsableFix(boolean hasAccuracy, float accuracyMeters) {
    return hasAccuracy
        && Float.isFinite(accuracyMeters)
        && accuracyMeters > 0f
        && accuracyMeters <= MAX_READY_ACCURACY_METERS;
  }
}
