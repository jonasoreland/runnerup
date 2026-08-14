/*
 * Copyright (C) 2014 robert.jonsson75@gmail.com
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

  // API 36+ requires READ_HEART_RATE. We intentionally use the literal permission
  // string rather than HealthPermissions.READ_HEART_RATE because referencing
  // android.health.connect classes may trigger class-loading failures on older
  // Wear OS versions where those classes are unavailable.
  private static final String READ_HEART_RATE_PERMISSION =
          "android.permission.health.READ_HEART_RATE";

  /** Node ID of the connected phone. */
  private String sourceNodeId;
  private SensorManager sensorManager;
  private Sensor heartRateSensor;

  private BroadcastReceiver hrPermissionReceiver;
  private BroadcastReceiver batteryChangedReceiver;

  @Override
  public IBinder onBind(Intent intent) {
    // We don't provide binding, so return null
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");

    setupPermissionReceiver();
    setupBatteryChangedReceiver();

    // Initialize the SensorManager and attempt to get the default heart rate sensor.
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    if (sensorManager != null) {
      heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
    }

    if (heartRateSensor == null) {
      Log.e(TAG, "onCreate: Heart rate sensor not available.");
      stopSelf(); // Stop the service if sensor is not found
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "onStartCommand: intent=" + intent);

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
    Log.d(TAG, "startHeartRateMonitoring");

    if (sensorManager != null && heartRateSensor != null) {
      boolean registered = sensorManager.registerListener(
              this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
      if (registered) {
        Log.d(TAG, "startHeartRateMonitoring: Heart rate sensor listener registered.");
      } else {
        Log.e(TAG, "startHeartRateMonitoring: Failed to register heart rate sensor listener.");
        stopSelf(); // Stop if registration fails
      }
    } else {
      Log.e(TAG, "startHeartRateMonitoring: SensorManager or HeartRateSensor is null in startHeartRateMonitoring.");
      stopSelf();
    }
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy");

    // Unregister the HR permission receiver
    if (hrPermissionReceiver != null) {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(hrPermissionReceiver);
      Log.d(TAG, "onDestroy: HR Permission Receiver unregistered.");
      hrPermissionReceiver = null;
    }

    // Stop listening for battery changes
    if (batteryChangedReceiver != null) {
      unregisterReceiver(batteryChangedReceiver);
      Log.d(TAG, "onDestroy: Battery Receiver unregistered.");
      batteryChangedReceiver = null;
    }

    stopHeartRateMonitoring(); // Ensure monitoring is stopped
    super.onDestroy();
  }

  private void attemptToStartHeartRateMonitoring() {
    Log.d(TAG, "attemptToStartHeartRateMonitoring");

    if (checkHeartRatePermission()) {
      startHeartRateMonitoring();
    } else {
      Log.w(TAG, "attemptToStartHeartRateMonitoring: Permission not granted for HR monitoring. Requesting...");
      launchPermissionActivity(); // The result will be handled by hrPermissionReceiver
    }
  }

  private void stopHeartRateMonitoring() {
    Log.d(TAG, "stopHeartRateMonitoring");

    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      Log.d(TAG, "stopHeartRateMonitoring: Heart rate sensor listener unregistered.");
    }
  }

  /**
   * Checks if the app has the necessary permission to access heart rate data.
   *
   * <p>The required permission depends on the Android version:
   * <ul>
   *   <li>Wear OS 6 (API 36) and higher: {@code android.permission.health.READ_HEART_RATE}</li>
   *   <li>Wear OS 5 (API 35) and lower: {@link Manifest.permission#BODY_SENSORS}</li>
   * </ul>
   *
   * @return {@code true} if the required permission is granted, {@code false} otherwise.
   */
  private boolean checkHeartRatePermission() {
    String permission = getRequiredHeartRatePermission();
    boolean permissionGranted = ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED;
    Log.d(TAG, "checkHeartRatePermission: permission=" + permission + ", granted=" + permissionGranted);
    return permissionGranted;
  }

  private String getRequiredHeartRatePermission() {
    if (Build.VERSION.SDK_INT >= 36) {
      return READ_HEART_RATE_PERMISSION;
    } else {
      return Manifest.permission.BODY_SENSORS;
    }
  }

  private void launchPermissionActivity() {
    Log.d(TAG, "launchPermissionActivity");
    Intent intent = new Intent(this, RequestPermissionActivity.class);
    intent.putExtra(Constants.Intents.EXTRA_PERMISSION_TO_REQUEST, getRequiredHeartRatePermission());
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Necessary when starting activity from a service
    startActivity(intent);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    Log.d(TAG, "onSensorChanged: sensor=" + event.sensor.getName());

    if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
      // When the accuracy is SENSOR_STATUS_UNRELIABLE or SENSOR_STATUS_NO_CONTACT,
      // the heart rate value should be discarded.
      // https://developer.android.com/reference/android/hardware/Sensor#TYPE_HEART_RATE
      if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
              || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
        Log.d(TAG, "onSensorChanged: HR value accuracy is unreliable or no contact.");
        return;
      }

      if (event.values.length > 0) {
        int currentHeartRate = Math.round(event.values[0]); // Heart rate in beats per minute (bpm)
        Log.d(TAG, "onSensorChanged: Current Heart Rate: " + currentHeartRate + " bpm");

        sendHeartRateToPhone(currentHeartRate);
      }
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    Log.d(TAG, "onAccuracyChanged: sensor=" + sensor.getName() + ", accuracy=" + accuracy);
  }

  private void sendHeartRateToPhone(int bpm) {
    Log.d(TAG, "sendHeartRateToPhone: bpm=" + bpm + ", sourceNodeId=" + sourceNodeId);

    if (sourceNodeId == null) {
      Log.e(TAG, "sendHeartRateToPhone: sourceNodeId is null. Not sending HR.");
      return;
    }

    byte[] payload = String.valueOf(bpm).getBytes();

    // Wearable API clients, such as DataClient and MessageClient, are inexpensive to create.
    // So instead of holding onto the clients, recreate them when needed.
    // https://developer.android.com/training/wearables/data/overview#recreate-client-instances
    Wearable.getMessageClient(this).sendMessage(sourceNodeId, Constants.Wear.Path.MSG_HEART_RATE, payload)
            .addOnSuccessListener(integer -> Log.d(TAG, "HR sent successfully: " + bpm))
            .addOnFailureListener(e -> Log.e(TAG, "Error sending HR: " + e.getMessage()));
  }

  private void sendBatteryLevelToPhone(int batteryLevel) {
    Log.d(TAG, "sendBatteryLevelToPhone: batteryLevel=" + batteryLevel + ", sourceNodeId=" + sourceNodeId);

    if (sourceNodeId == null) {
      Log.e(TAG, "sendBatteryLevelToPhone: sourceNodeId is null. Not sending battery level.");
      return;
    }

    byte[] payload = String.valueOf(batteryLevel).getBytes();

    // Wearable API clients, such as DataClient and MessageClient, are inexpensive to create.
    // So instead of holding onto the clients, recreate them when needed.
    // https://developer.android.com/training/wearables/data/overview#recreate-client-instances
    Wearable.getMessageClient(this).sendMessage(sourceNodeId, Constants.Wear.Path.MSG_BATTERY_LEVEL, payload)
            .addOnSuccessListener(integer -> Log.d(TAG, "Battery level sent successfully: " + batteryLevel))
            .addOnFailureListener(e -> Log.e(TAG, "Error sending battery level: " + e.getMessage()));
  }

  private void setupPermissionReceiver() {
    Log.d(TAG, "setupPermissionReceiver");

    hrPermissionReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        if (Constants.Intents.ACTION_PERMISSION_RESULT.equals(action)) {
          boolean permissionGranted = intent.getBooleanExtra(
                  Constants.Intents.EXTRA_PERMISSION_GRANTED, false);

          Log.d(TAG, "onReceive: permissionGranted=" + permissionGranted);

          if (permissionGranted) {
            startHeartRateMonitoring();
          }
          else {
            Log.w(TAG, "onReceive: stopping service due to missing permission");
            stopSelf(); // Stop if permission is not granted
            // TODO: Notify phone app that permission is missing?
          }
        }
      }
    };

    // Register the receiver
    IntentFilter filter = new IntentFilter(Constants.Intents.ACTION_PERMISSION_RESULT);
    LocalBroadcastManager.getInstance(this).registerReceiver(hrPermissionReceiver, filter);
    Log.d(TAG, "setupPermissionReceiver: HR Permission Receiver registered.");
  }

  private void setupBatteryChangedReceiver() {
    Log.d(TAG, "setupBatteryChangedReceiver");

    batteryChangedReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "batteryChangedReceiver.onReceive: action=" + action);

        if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
          int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
          int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
          if (level < 0 || scale <= 0) {
            Log.w(TAG, "batteryChangedReceiver.onReceive: unknown battery level/scale: level=" + level + ", scale=" + scale);
            return;
          }
          int batteryPercent = (int) ((level / (float) scale) * 100);

          Log.d(TAG, "batteryChangedReceiver.onReceive: battery level=" + batteryPercent + "%");

          sendBatteryLevelToPhone(batteryPercent);
        }
      }
    };

    // Register the receiver
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(batteryChangedReceiver, filter);
    Log.d(TAG, "batteryChangedReceiver: Battery Receiver registered.");
  }
}
