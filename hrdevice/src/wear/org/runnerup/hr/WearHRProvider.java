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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.runnerup.common.util.Constants;

public class WearHRProvider implements HRProvider {
  private static final String TAG = "WearHRProvider";
  public static final String NAME = "Wear OS";

  private final Context context;
  private HRClient hrClient;
  private Handler hrClientHandler;
  private volatile boolean isScanning;
  private volatile boolean isConnecting;
  private volatile boolean isConnected;
  private volatile boolean isDisconnecting;
  private volatile String connectedNodeId;

  private final WearHeartRateSample sample = new WearHeartRateSample();
  private volatile long hrTimestamp = 0;
  private volatile long hrElapsedRealtime = 0;
  private volatile int batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;

  public WearHRProvider(Context context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getProviderName() {
    return NAME;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean startEnableIntent(
      AppCompatActivity activity, ActivityResultLauncher<Intent> launcher) {
    return false; // Assume watch is already paired/connected to phone
  }

  @Override
  public void open(Handler handler, HRClient hrClient) {
    this.hrClient = hrClient;
    this.hrClientHandler = handler;

    postToHRClient(() -> hrClient.onOpenResult(true));
  }

  @Override
  public void close(String from) {
    Wearable.getMessageClient(context).removeListener(onHRMessageListener);
    reset();
    HRClient oldClient = hrClient;
    Handler oldHandler = hrClientHandler;
    hrClient = null;
    hrClientHandler = null;
    if (oldClient != null && oldHandler != null) {
      oldHandler.post(() -> oldClient.onCloseResult(true));
    }
  }

  @Override
  public boolean includePairingBLE() {
    return false;
  }

  @Override
  public boolean isScanning() {
    return isScanning;
  }

  @Override
  public boolean isConnected() {
    return isConnected && sample.valueAt(SystemClock.elapsedRealtimeNanos()) > 0;
  }

  @Override
  public boolean isConnecting() {
    return isConnecting;
  }

  @Override
  public void startScan() {
    if (isScanning) {
      return;
    }

    isScanning = true;

    // Get nodes with capability to provide heart rate data for RunnerUp
    CapabilityClient capabilityClient = Wearable.getCapabilityClient(context);
    Task<CapabilityInfo> capabilityInfoTask =
        capabilityClient.getCapability(
            Constants.Wear.Capability.HEART_RATE_PROVIDER, CapabilityClient.FILTER_REACHABLE);

    capabilityInfoTask.addOnSuccessListener(
        capabilityInfo -> {
          Set<Node> connectedNodes = capabilityInfo.getNodes();
          if (!connectedNodes.isEmpty()) {
            for (Node node : connectedNodes) {
              HRDeviceRef deviceRef = HRDeviceRef.create(NAME, node.getDisplayName(), node.getId());

              postToHRClient(() -> hrClient.onScanResult(deviceRef));
            }
          } else {
            postToHRClient(() -> hrClient.log(this, "No Wear OS device found with HR capability"));
          }

          stopScan();
        });

    capabilityInfoTask.addOnFailureListener(
        e -> {
          Log.e(TAG, "startScan failed: " + e.getMessage());
          postToHRClient(() -> hrClient.log(this, e.getMessage()));
          stopScan();
        });
  }

  @Override
  public void stopScan() {
    isScanning = false;
  }

  @Override
  public void connect(HRDeviceRef ref) {
    if (isConnecting || isConnected) {
      return;
    }

    isConnecting = true;
    connectedNodeId = ref.getAddress();
    sample.clear();
    hrTimestamp = 0;
    hrElapsedRealtime = 0;
    Wearable.getMessageClient(context).addListener(onHRMessageListener);

    Wearable.getMessageClient(context)
        .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_START, new byte[0])
        .addOnSuccessListener(
            integer -> {
              if (isConnecting) {
                // Delivery confirms only that the message reached the watch, not that the sensor
                // started. Wait for a valid measurement before reporting a connected HR source.
                Handler handler = hrClientHandler;
                if (handler == null) return;
                handler.postDelayed(
                    () -> {
                      if (completeConnection(false)) {
                        Wearable.getMessageClient(context)
                            .sendMessage(
                                connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_STOP, new byte[0]);
                        Wearable.getMessageClient(context).removeListener(onHRMessageListener);
                        postToHRClient(() -> hrClient.onConnectResult(false));
                      }
                    },
                    30_000);
              }
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Failed to send Start HR message: " + e.getMessage());
              if (completeConnection(false)) {
                Wearable.getMessageClient(context).removeListener(onHRMessageListener);
                postToHRClient(() -> hrClient.onConnectResult(false));
              }
            });
  }

  @Override
  public void disconnect() {
    if (connectedNodeId == null || isDisconnecting) {
      return;
    }

    isDisconnecting = true;
    if (connectedNodeId != null) {
      Wearable.getMessageClient(context)
          .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_STOP, new byte[0]);

      postToHRClient(() -> hrClient.onDisconnectResult(true));
      reset();
      Wearable.getMessageClient(context).removeListener(onHRMessageListener);
    }
  }

  private void reset() {
    isConnecting = false;
    isConnected = false;
    isDisconnecting = false;
    isScanning = false;
    connectedNodeId = null;
    sample.clear();
    hrTimestamp = 0;
    hrElapsedRealtime = 0;
    batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;
  }

  private synchronized boolean completeConnection(boolean connected) {
    if (!isConnecting) {
      return false;
    }
    isConnecting = false;
    isConnected = connected;
    return true;
  }

  @Override
  public int getHRValue() {
    return sample.valueAt(SystemClock.elapsedRealtimeNanos());
  }

  @Override
  public long getHRValueTimestamp() {
    return getHRValue() > 0 ? hrTimestamp : 0;
  }

  @Override
  public long getHRValueElapsedRealtime() {
    return getHRValue() > 0 ? hrElapsedRealtime : 0;
  }

  @Override
  public HRData getHRData() {
    int value = getHRValue();
    if (value <= 0) {
      return null;
    }

    return new HRData().setHeartRate(value).setTimestampEstimate(hrTimestamp);
  }

  @Override
  public int getBatteryLevel() {
    return batteryLevel;
  }

  private void postToHRClient(Runnable action) {
    if (hrClientHandler != null && hrClient != null) {
      hrClientHandler.post(
          () -> {
            if (hrClient != null) {
              action.run();
            } else {
              Log.w(TAG, "postToHRClient: hrClient became null before action execution.");
            }
          });
    } else {
      Log.w(TAG, "postToHRClient: hrClientHandler or hrClient is null.");
    }
  }

  private final MessageClient.OnMessageReceivedListener onHRMessageListener =
      messageEvent -> {
        if (connectedNodeId == null || !connectedNodeId.equals(messageEvent.getSourceNodeId())) {
          return;
        }
        String path = messageEvent.getPath();

        if (Constants.Wear.Path.MSG_HEART_RATE.equals(path)) {
          if (sample.update(
              new String(messageEvent.getData(), StandardCharsets.US_ASCII),
              SystemClock.elapsedRealtimeNanos())) {
            hrTimestamp = System.currentTimeMillis();
            hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
            if (completeConnection(true)) {
              postToHRClient(() -> hrClient.onConnectResult(true));
            }
          } else {
            Log.w(TAG, "onMessageReceived: invalid HR payload");
          }
        } else if (Constants.Wear.Path.MSG_BATTERY_LEVEL.equals(path)) {
          byte[] payload = messageEvent.getData();
          try {
            int level = Integer.parseInt(new String(payload, StandardCharsets.US_ASCII));
            if (level >= 0 && level <= 100) batteryLevel = level;
          } catch (NumberFormatException e) {
            Log.w(TAG, "onMessageReceived: invalid battery payload", e);
          }
        }
      };

  public static boolean checkLibrary(Context ctx) {
    GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
    int resultCode = apiAvailability.isGooglePlayServicesAvailable(ctx);

    if (resultCode != ConnectionResult.SUCCESS) {
      Log.w(TAG, "Google Play services is not installed or is not enabled on this device.");
      return false;
    }

    return true;
  }
}
