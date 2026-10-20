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
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.android.gms.wearable.Wearable;
import java.nio.charset.StandardCharsets;
import org.runnerup.common.util.Constants;

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

  private BroadcastReceiver batteryChangedReceiver;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();

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

    // A restarted service must wait for a new command from the phone. A stale start command
    // could otherwise leave the watch sensor running after the workout has ended.
    return START_NOT_STICKY;
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
      Log.w(
          TAG,
          "Heart rate permission is missing; grant it from the visible watch permission page.");
      stopSelf();
    }
  }

  private void stopHeartRateMonitoring() {
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
    }
  }

  /** Checks if the version-appropriate heart-rate permission has been granted. */
  private boolean checkHeartRatePermission() {
    String permission = HeartRatePermissionState.permissionForSdk(Build.VERSION.SDK_INT);
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
      if (event.values.length > 0) {
        int currentHeartRate = Math.round(event.values[0]);
        if (currentHeartRate >= 30 && currentHeartRate <= 240) {
          sendHeartRateToPhone(currentHeartRate);
        }
      }
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private void sendHeartRateToPhone(int bpm) {
    if (sourceNodeId == null) {
      return;
    }

    byte[] payload = String.valueOf(bpm).getBytes(StandardCharsets.US_ASCII);
    Wearable.getMessageClient(this)
        .sendMessage(sourceNodeId, Constants.Wear.Path.MSG_HEART_RATE, payload)
        .addOnFailureListener(e -> Log.e(TAG, "Error sending HR: " + e.getMessage()));
  }

  private void sendBatteryLevelToPhone(int batteryLevel) {
    if (sourceNodeId == null) {
      return;
    }

    byte[] payload = String.valueOf(batteryLevel).getBytes(StandardCharsets.US_ASCII);
    Wearable.getMessageClient(this)
        .sendMessage(sourceNodeId, Constants.Wear.Path.MSG_BATTERY_LEVEL, payload)
        .addOnFailureListener(e -> Log.e(TAG, "Error sending battery level: " + e.getMessage()));
  }

  private void setupBatteryChangedReceiver() {
    batteryChangedReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
              int batteryPercent = (int) ((level / (float) scale) * 100);
              sendBatteryLevelToPhone(batteryPercent);
            }
          }
        };
    registerReceiver(batteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }
}
