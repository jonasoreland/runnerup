package org.runnerup.tracker;

import android.location.Location;

/** Receives immutable copies of fixes the Tracker has accepted and persisted. */
public interface TrackerLocationObserver {
  void onAcceptedLocation(
      Location location,
      int persistedType,
      double totalDistanceMeters,
      long activeTimeNanos,
      Integer heartRate,
      Double elevationMeters);
}
