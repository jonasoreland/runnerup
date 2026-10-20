package org.runnerup.hr;

/** Maps supported Wear OS permission states for the watch's heart-rate sensor. */
public final class HeartRatePermissionState {
  public enum State {
    UNAVAILABLE,
    NEEDS_PERMISSION,
    READY
  }

  private HeartRatePermissionState() {}

  public static String permissionForSdk(int sdkInt) {
    return sdkInt >= 36
        ? "android.permission.health.READ_HEART_RATE"
        : "android.permission.BODY_SENSORS";
  }

  public static State resolve(boolean sensorAvailable, boolean permissionGranted) {
    if (!sensorAvailable) return State.UNAVAILABLE;
    return permissionGranted ? State.READY : State.NEEDS_PERMISSION;
  }
}
