package org.runnerup.hr;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import org.runnerup.common.wear.HeartRateReadinessProtocol;

/** Reads actual sensor and permission availability without capturing or transmitting samples. */
public final class HeartRateReadiness {
  private HeartRateReadiness() {}

  public static HeartRatePermissionState.State inspect(Context context) {
    SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    boolean sensorAvailable =
        sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null;
    String foregroundPermission = HeartRatePermissionState.permissionForSdk(Build.VERSION.SDK_INT);
    String backgroundPermission =
        HeartRatePermissionState.backgroundPermissionForSdk(Build.VERSION.SDK_INT);
    boolean foregroundGranted = isGranted(context, foregroundPermission);
    boolean backgroundGranted =
        backgroundPermission == null || isGranted(context, backgroundPermission);
    return HeartRatePermissionState.resolve(
        sensorAvailable, foregroundGranted, backgroundGranted, Build.VERSION.SDK_INT);
  }

  public static HeartRateReadinessProtocol.Status toProtocolStatus(
      HeartRatePermissionState.State state) {
    switch (state) {
      case READY:
        return HeartRateReadinessProtocol.Status.READY;
      case UNAVAILABLE:
        return HeartRateReadinessProtocol.Status.SENSOR_UNAVAILABLE;
      case NEEDS_PERMISSION:
        return HeartRateReadinessProtocol.Status.PERMISSION_REQUIRED;
      case NEEDS_BACKGROUND_PERMISSION:
      default:
        return HeartRateReadinessProtocol.Status.BACKGROUND_PERMISSION_REQUIRED;
    }
  }

  private static boolean isGranted(Context context, String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }
}
