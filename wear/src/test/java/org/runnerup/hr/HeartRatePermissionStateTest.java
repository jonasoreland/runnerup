package org.runnerup.hr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HeartRatePermissionStateTest {
  @Test
  public void mapsWearOsVersionsToTheirHeartRatePermission() {
    assertEquals("android.permission.BODY_SENSORS", HeartRatePermissionState.permissionForSdk(35));
    assertEquals(
        "android.permission.health.READ_HEART_RATE", HeartRatePermissionState.permissionForSdk(36));
    assertEquals(
        "android.permission.health.READ_HEART_RATE", HeartRatePermissionState.permissionForSdk(37));
  }

  @Test
  public void distinguishesMissingSensorPermissionFromReadyState() {
    assertEquals(
        HeartRatePermissionState.State.UNAVAILABLE, HeartRatePermissionState.resolve(false, false));
    assertEquals(
        HeartRatePermissionState.State.NEEDS_PERMISSION,
        HeartRatePermissionState.resolve(true, false));
    assertEquals(
        HeartRatePermissionState.State.READY, HeartRatePermissionState.resolve(true, true));
  }
}
