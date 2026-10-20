package org.runnerup.hr;

/** Maps supported Wear OS permission states for the watch's heart-rate sensor. */
public final class HeartRatePermissionState {
  public enum State {
    UNAVAILABLE,
    NEEDS_PERMISSION,
    NEEDS_BACKGROUND_PERMISSION,
    READY
  }

  private HeartRatePermissionState() {}

  public static String permissionForSdk(int sdkInt) {
    return sdkInt >= 36
        ? "android.permission.health.READ_HEART_RATE"
        : "android.permission.BODY_SENSORS";
  }

  public static String backgroundPermissionForSdk(int sdkInt) {
    if (sdkInt >= 36) return "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";
    if (sdkInt >= 33) return "android.permission.BODY_SENSORS_BACKGROUND";
    return null;
  }

  public static State resolve(boolean sensorAvailable, boolean permissionGranted) {
    return resolve(sensorAvailable, permissionGranted, true, 32);
  }

  public static State resolve(
      boolean sensorAvailable,
      boolean permissionGranted,
      boolean backgroundPermissionGranted,
      int sdkInt) {
    if (!sensorAvailable) return State.UNAVAILABLE;
    if (!permissionGranted) return State.NEEDS_PERMISSION;
    if (backgroundPermissionForSdk(sdkInt) != null && !backgroundPermissionGranted) {
      return State.NEEDS_BACKGROUND_PERMISSION;
    }
    return State.READY;
  }
}
