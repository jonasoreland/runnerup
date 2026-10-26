package org.runnerup.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.runnerup.hr.HeartRatePermissionState;

/** Runtime permissions needed for watch-origin GPS and heart-rate samples. */
public final class WearWorkoutRecorderPermissions {
  private WearWorkoutRecorderPermissions() {}

  public static List<String> forSdk(int sdkInt) {
    List<String> permissions = new ArrayList<>();
    permissions.add("android.permission.ACCESS_COARSE_LOCATION");
    permissions.add("android.permission.ACCESS_FINE_LOCATION");
    if (sdkInt >= 29) permissions.add("android.permission.ACTIVITY_RECOGNITION");
    permissions.add(HeartRatePermissionState.permissionForSdk(sdkInt));
    String background = HeartRatePermissionState.backgroundPermissionForSdk(sdkInt);
    if (background != null) permissions.add(background);
    return Collections.unmodifiableList(permissions);
  }

  public static boolean requiresSettingsPage(String permission, int sdkInt) {
    return sdkInt < 36
        && sdkInt >= 33
        && "android.permission.BODY_SENSORS_BACKGROUND".equals(permission);
  }
}
