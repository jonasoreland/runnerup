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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;
import com.google.android.gms.wearable.Wearable;
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

  @Override
  public IBinder onBind(Intent intent) {
    // We don't provide binding, so return null
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");

    // Initialize the SensorManager and attempt to get the default heart rate sensor.
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    if (sensorManager != null) {
      heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
    }

    if (heartRateSensor == null) {
      Log.e(TAG, "Heart rate sensor not available.");
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

    // TODO: Check permission before start listening to the sensor.
    startHeartRateMonitoring();

    // Ensures the Intent (with sourceNodeId) is redelivered if the service restarts
    return START_REDELIVER_INTENT;
  }

  private void startHeartRateMonitoring() {
    if (sensorManager != null && heartRateSensor != null) {
      // TODO: Use SENSOR_DELAY_UI for faster updates?
      boolean registered = sensorManager.registerListener(
              this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
      if (registered) {
        Log.d(TAG, "Heart rate sensor listener registered.");
      } else {
        Log.e(TAG, "Failed to register heart rate sensor listener.");
        stopSelf(); // Stop if registration fails
      }
    } else {
      Log.e(TAG, "SensorManager or HeartRateSensor is null in startHeartRateMonitoring.");
      stopSelf();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy");

    stopHeartRateMonitoring();
  }

  private void stopHeartRateMonitoring() {
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      Log.d(TAG, "Heart rate sensor listener unregistered.");
    }
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
    Log.d(TAG, "sendHeartRateToPhone: bpm=" + bpm);

    byte[] payload = String.valueOf(bpm).getBytes();

    // Wearable API clients, such as DataClient and MessageClient, are inexpensive to create.
    // So instead of holding onto the clients, recreate them when needed.
    // https://developer.android.com/training/wearables/data/overview#recreate-client-instances
    Wearable.getMessageClient(this).sendMessage(sourceNodeId, Constants.Wear.Path.MSG_HEART_RATE, payload)
            .addOnSuccessListener(integer -> Log.d(TAG, "HR sent successfully: " + bpm))
            .addOnFailureListener(e -> Log.e(TAG, "Error sending HR: " + e.getMessage()));

  }
}
