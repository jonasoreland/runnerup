package org.runnerup.hr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.runnerup.common.wear.HeartRateReadinessProtocol;

public class HeartRatePermissionStateTest {
  @Test
  public void readinessInspectionPreservesHardwareAndPermissionFailureReasons() {
    assertEquals(
        HeartRateReadinessProtocol.Status.READY,
        HeartRateReadiness.toProtocolStatus(HeartRatePermissionState.State.READY));
    assertEquals(
        HeartRateReadinessProtocol.Status.SENSOR_UNAVAILABLE,
        HeartRateReadiness.toProtocolStatus(HeartRatePermissionState.State.UNAVAILABLE));
    assertEquals(
        HeartRateReadinessProtocol.Status.PERMISSION_REQUIRED,
        HeartRateReadiness.toProtocolStatus(HeartRatePermissionState.State.NEEDS_PERMISSION));
    assertEquals(
        HeartRateReadinessProtocol.Status.BACKGROUND_PERMISSION_REQUIRED,
        HeartRateReadiness.toProtocolStatus(
            HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION));
  }

  @Test
  public void mapsWearOsVersionsToTheirHeartRatePermission() {
    assertEquals("android.permission.BODY_SENSORS", HeartRatePermissionState.permissionForSdk(32));
    assertEquals("android.permission.BODY_SENSORS", HeartRatePermissionState.permissionForSdk(35));
    assertEquals(
        "android.permission.health.READ_HEART_RATE", HeartRatePermissionState.permissionForSdk(36));
    assertEquals(
        "android.permission.health.READ_HEART_RATE", HeartRatePermissionState.permissionForSdk(37));
  }

  @Test
  public void mapsBackgroundPermissionOnlyWhereRequired() {
    assertEquals(null, HeartRatePermissionState.backgroundPermissionForSdk(32));
    assertEquals(
        "android.permission.BODY_SENSORS_BACKGROUND",
        HeartRatePermissionState.backgroundPermissionForSdk(33));
    assertEquals(
        "android.permission.BODY_SENSORS_BACKGROUND",
        HeartRatePermissionState.backgroundPermissionForSdk(35));
    assertEquals(
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
        HeartRatePermissionState.backgroundPermissionForSdk(36));
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

  @Test
  public void requiresBackgroundPermissionFromApi33() {
    assertEquals(
        HeartRatePermissionState.State.READY,
        HeartRatePermissionState.resolve(true, true, false, 32));
    assertEquals(
        HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION,
        HeartRatePermissionState.resolve(true, true, false, 33));
    assertEquals(
        HeartRatePermissionState.State.NEEDS_BACKGROUND_PERMISSION,
        HeartRatePermissionState.resolve(true, true, false, 36));
    assertEquals(
        HeartRatePermissionState.State.READY,
        HeartRatePermissionState.resolve(true, true, true, 36));
    assertEquals(
        HeartRatePermissionState.State.NEEDS_PERMISSION,
        HeartRatePermissionState.resolve(true, false, true, 36));
    assertEquals(
        HeartRatePermissionState.State.UNAVAILABLE,
        HeartRatePermissionState.resolve(false, true, true, 36));
  }
}
