package org.runnerup.tracker;

import android.location.Location;
import androidx.annotation.Nullable;
import androidx.core.location.LocationCompat;

/** Shared quality check for GPS altitude used in workout summaries and exports. */
public final class GpsAltitudeQuality {
  private static final float MAX_VERTICAL_ACCURACY_METERS = 5.0f;

  private GpsAltitudeQuality() {}

  public static boolean isReliable(@Nullable Location location) {
    if (location == null
        || !location.hasAltitude()
        || !Double.isFinite(location.getAltitude())
        || !LocationCompat.hasVerticalAccuracy(location)) {
      return false;
    }
    float verticalAccuracy = LocationCompat.getVerticalAccuracyMeters(location);
    if (!(Float.isFinite(verticalAccuracy)
        && verticalAccuracy > 0.0f
        && verticalAccuracy <= MAX_VERTICAL_ACCURACY_METERS)) {
      return false;
    }
    if (!LocationCompat.hasMslAltitude(location)) return true;

    double mslAltitude = LocationCompat.getMslAltitudeMeters(location);
    if (!Double.isFinite(mslAltitude)) return false;
    if (!LocationCompat.hasMslAltitudeAccuracy(location)) return true;

    float mslAccuracy = LocationCompat.getMslAltitudeAccuracyMeters(location);
    return Float.isFinite(mslAccuracy)
        && mslAccuracy > 0.0f
        && mslAccuracy <= MAX_VERTICAL_ACCURACY_METERS;
  }
}
