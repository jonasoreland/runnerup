/*
 * Copyright (C) 2026 robert.jonsson75@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.hr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.health.connect.HealthPermissions;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.wearable.Wearable;
import org.runnerup.common.util.Constants;
import org.runnerup.view.RequestPermissionActivity;

/**
 * A {@link Service} that monitors heart rate data using the device's {@link Sensor#TYPE_HEART_RATE}
 * and sends this data to a connected phone via the Wearable Data Layer API. This service is started
 * by {@link HeartRateListenerService} in response to commands from the phone.
 */
public class HeartRateService extends Service implements SensorEventListener {
  private static final String TAG = "HeartRateService";

  /** Node ID of the connected phone. */
  private String sourceNodeId;

  private SensorManager sensorManager;
  private Sensor heartRateSensor;

  private BroadcastReceiver hrPermissionReceiver;
  private BroadcastReceiver batteryChangedReceiver;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    setupPermissionReceiver();
    setupBatteryChangedReceiver();

    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    if (sensorManager != null) {
      heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
    }

    if (heartRateSensor == null) {
      Log.e(TAG, "onCreate: Heart rate sensor not available.");
      stopSelf();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null) {
      sourceNodeId = intent.getStringExtra(Constants.Intents.EXTRA_SOURCE_NODE_ID);
    }

    if (sourceNodeId == null) {
      Log.e(TAG, "onStartCommand: sourceNodeId is null. Stopping service.");
      stopSelf();
      return START_NOT_STICKY;
    }

    attemptToStartHeartRateMonitoring();

    // Ensures the Intent (with sourceNodeId) is redelivered if the service restarts
    return START_REDELIVER_INTENT;
  }

  private void startHeartRateMonitoring() {
    if (sensorManager != null && heartRateSensor != null) {
      boolean registered =
          sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
      if (!registered) {
        Log.e(TAG, "startHeartRateMonitoring: Failed to register heart rate sensor listener.");
      }
    }
  }

  @Override
  public void onDestroy() {
    if (hrPermissionReceiver != null) {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(hrPermissionReceiver);
      hrPermissionReceiver = null;
    }

    if (batteryChangedReceiver != null) {
      unregisterReceiver(batteryChangedReceiver);
      batteryChangedReceiver = null;
    }

    stopHeartRateMonitoring();
    super.onDestroy();
  }

  private void attemptToStartHeartRateMonitoring() {
    if (checkHeartRatePermission()) {
      startHeartRateMonitoring();
    } else {
      launchPermissionActivity();
    }
  }

  private void stopHeartRateMonitoring() {
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
    }
  }

  /**
   * Checks if the app has the necessary permission to access heart rate data.
   *
   * <p>The required permission depends on the Android version:
   *
   * <ul>
   *   <li>Wear OS 6 (API 36) and higher: {@code android.permission.health.READ_HEART_RATE}
   *   <li>Wear OS 5 (API 35) and lower: {@link Manifest.permission#BODY_SENSORS}
   * </ul>
   *
   * @return {@code true} if the required permission is granted, {@code false} otherwise.
   */
  private boolean checkHeartRatePermission() {
    String permission = getRequiredHeartRatePermission();
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
  }

  private String getRequiredHeartRatePermission() {
    if (Build.VERSION.SDK_INT >= 36) {
      return HealthPermissions.READ_HEART_RATE;
    }
    return Manifest.permission.BODY_SENSORS;
  }

  @SuppressLint("WearRecents")
  private void launchPermissionActivity() {
    Intent intent = new Intent(this, RequestPermissionActivity.class);
    intent.putExtra(
        Constants.Intents.EXTRA_PERMISSION_TO_REQUEST, getRequiredHeartRatePermission());
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK); // Necessary when starting activity from a service
    startActivity(intent);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
      if (event.values.length > 0) {
        int currentHeartRate = Math.round(event.values[0]);
        sendHeartRateToPhone(currentHeartRate);
      }
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private void sendHeartRateToPhone(int bpm) {
    if (sourceNodeId == null) {
      return;
    }

    byte[] payload = String.valueOf(bpm).getBytes();
    Wearable.getMessageClient(this)
        .sendMessage(sourceNodeId, Constants.Wear.Path.MSG_HEART_RATE, payload)
        .addOnFailureListener(e -> Log.e(TAG, "Error sending HR: " + e.getMessage()));
  }

  private void sendBatteryLevelToPhone(int batteryLevel) {
    if (sourceNodeId == null) {
      return;
    }

    byte[] payload = String.valueOf(batteryLevel).getBytes();
    Wearable.getMessageClient(this)
        .sendMessage(sourceNodeId, Constants.Wear.Path.MSG_BATTERY_LEVEL, payload)
        .addOnFailureListener(e -> Log.e(TAG, "Error sending battery level: " + e.getMessage()));
  }

  private void setupPermissionReceiver() {
    hrPermissionReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String permission =
                intent.getStringExtra(Constants.Intents.EXTRA_REQUESTED_PERMISSION_NAME);
            boolean granted =
                intent.getBooleanExtra(Constants.Intents.EXTRA_PERMISSION_GRANTED, false);
            if (getRequiredHeartRatePermission().equals(permission) && granted) {
              startHeartRateMonitoring();
            }
          }
        };
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            hrPermissionReceiver, new IntentFilter(Constants.Intents.ACTION_PERMISSION_RESULT));
  }

  private void setupBatteryChangedReceiver() {
    batteryChangedReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1) {
              int batteryPercent = (int) ((level / (float) scale) * 100);
              sendBatteryLevelToPhone(batteryPercent);
            }
          }
        };
    registerReceiver(batteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }
}
