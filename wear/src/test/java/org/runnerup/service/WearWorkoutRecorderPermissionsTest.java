package org.runnerup.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class WearWorkoutRecorderPermissionsTest {
  @Test
  public void legacyWearUsesBodySensorsAndAddsBackgroundPermissionOnApi33() {
    List<String> api32 = WearWorkoutRecorderPermissions.forSdk(32);
    List<String> api33 = WearWorkoutRecorderPermissions.forSdk(33);

    assertTrue(api32.contains("android.permission.BODY_SENSORS"));
    assertFalse(api32.contains("android.permission.BODY_SENSORS_BACKGROUND"));
    assertTrue(api33.contains("android.permission.BODY_SENSORS_BACKGROUND"));
    assertTrue(api33.contains("android.permission.ACCESS_FINE_LOCATION"));
    assertTrue(api33.contains("android.permission.ACTIVITY_RECOGNITION"));
  }

  @Test
  public void wearOs6UsesHealthPlatformPermissionNames() {
    List<String> api36 = WearWorkoutRecorderPermissions.forSdk(36);

    assertTrue(api36.contains("android.permission.health.READ_HEART_RATE"));
    assertTrue(api36.contains("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"));
    assertFalse(api36.contains("android.permission.BODY_SENSORS"));
    assertFalse(api36.contains("android.permission.BODY_SENSORS_BACKGROUND"));
  }

  @Test
  public void onlyLegacyBackgroundSensorPermissionRequiresSettingsPage() {
    assertTrue(
        WearWorkoutRecorderPermissions.requiresSettingsPage(
            "android.permission.BODY_SENSORS_BACKGROUND", 35));
    assertFalse(
        WearWorkoutRecorderPermissions.requiresSettingsPage(
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND", 36));
    assertFalse(
        WearWorkoutRecorderPermissions.requiresSettingsPage(
            "android.permission.ACCESS_FINE_LOCATION", 36));
  }
}
