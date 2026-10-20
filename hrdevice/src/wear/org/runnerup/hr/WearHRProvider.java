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
  private static final long CONNECTION_TIMEOUT_MILLIS = 30_000;
  private static final long SAMPLE_FRESHNESS_MILLIS = 15_001;
  public static final String NAME = "Wear OS";

  private final Context context;
  private HRClient hrClient;
  private Handler hrClientHandler;
  private volatile boolean isScanning;
  private volatile boolean isConnecting;
  private volatile boolean isConnected;
  private volatile boolean isDisconnecting;
  private volatile String connectedNodeId;
  private final WearHRConnectionState connectionState = new WearHRConnectionState();

  private final WearHeartRateSample sample = new WearHeartRateSample();
  private volatile long hrTimestamp = 0;
  private volatile long hrElapsedRealtime = 0;
  private volatile int batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;

  private final Runnable connectionTimeout;
  private final Runnable sampleFreshnessWatchdog;

  public WearHRProvider(Context context) {
    this.context = context;
    connectionTimeout =
        () -> {
          if (completeConnection(false)) {
            Wearable.getMessageClient(this.context)
                .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_STOP, new byte[0]);
            stopWatchService();
            postToHRClient(() -> hrClient.onConnectResult(false));
          }
        };
    sampleFreshnessWatchdog =
        () -> {
          if (connectionState.beginRecoveryIfStale(SystemClock.elapsedRealtime())) {
            isConnected = false;
            isConnecting = true;
            log("Wear HR sample became stale; requesting one sensor restart");
            startWatchService();
          }
        };
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
    if (connectedNodeId != null) {
      Wearable.getMessageClient(context)
          .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_STOP, new byte[0]);
    }
    cancelPendingRecovery();
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
    checkSampleFreshness();
    return isConnected && sample.valueAt(SystemClock.elapsedRealtimeNanos()) > 0;
  }

  public WearHRConnectionState.Status getConnectionStatus() {
    checkSampleFreshness();
    return connectionState.getStatus();
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
    if (!connectionState.beginConnect()) {
      return;
    }

    isConnecting = true;
    isDisconnecting = false;
    connectedNodeId = ref.getAddress();
    sample.clear();
    hrTimestamp = 0;
    hrElapsedRealtime = 0;
    Wearable.getMessageClient(context).addListener(onHRMessageListener);
    startWatchService();
  }

  private void startWatchService() {
    Handler handler = hrClientHandler;
    if (handler != null) {
      handler.removeCallbacks(connectionTimeout);
      handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS);
    }
    Wearable.getMessageClient(context)
        .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_START, new byte[0])
        .addOnSuccessListener(
            ignored -> {
              // Delivery confirms only that the command reached the watch, not that the sensor
              // restarted. Wait for another valid measurement before reporting recovery.
            })
        .addOnFailureListener(
            error -> {
              Log.e(TAG, "Failed to send Start HR message: " + error.getMessage());
              if (completeConnection(false)) {
                stopWatchService();
                postToHRClient(() -> hrClient.onConnectResult(false));
              }
            });
  }

  private void stopWatchService() {
    Wearable.getMessageClient(context).removeListener(onHRMessageListener);
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
    cancelPendingRecovery();
    isConnecting = false;
    isConnected = false;
    isDisconnecting = false;
    isScanning = false;
    connectedNodeId = null;
    sample.clear();
    hrTimestamp = 0;
    hrElapsedRealtime = 0;
    batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;
    connectionState.disconnect();
  }

  private synchronized boolean completeConnection(boolean connected) {
    if (!connectionState.isConnecting()) return false;
    isConnecting = false;
    isConnected = connected;
    if (connected) {
      connectionState.onValidSample(SystemClock.elapsedRealtime());
    } else {
      connectionState.markUnavailable();
      cancelPendingRecovery();
    }
    return true;
  }

  @Override
  public int getHRValue() {
    checkSampleFreshness();
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
            long elapsedRealtime = SystemClock.elapsedRealtime();
            boolean notifyConnected = connectionState.onValidSample(elapsedRealtime);
            isConnecting = false;
            isConnected = true;
            hrTimestamp = System.currentTimeMillis();
            hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
            scheduleFreshnessCheck();
            cancelConnectionTimeout();
            if (notifyConnected) {
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

  private void checkSampleFreshness() {
    if (connectionState.beginRecoveryIfStale(SystemClock.elapsedRealtime())) {
      isConnected = false;
      isConnecting = true;
      log("Wear HR sample became stale; requesting one sensor restart");
      startWatchService();
    }
  }

  private void scheduleFreshnessCheck() {
    Handler handler = hrClientHandler;
    if (handler != null) {
      handler.removeCallbacks(sampleFreshnessWatchdog);
      handler.postDelayed(sampleFreshnessWatchdog, SAMPLE_FRESHNESS_MILLIS);
    }
  }

  private void cancelPendingRecovery() {
    Handler handler = hrClientHandler;
    if (handler != null) {
      handler.removeCallbacks(sampleFreshnessWatchdog);
      handler.removeCallbacks(connectionTimeout);
    }
  }

  private void cancelConnectionTimeout() {
    Handler handler = hrClientHandler;
    if (handler != null) handler.removeCallbacks(connectionTimeout);
  }

  private void log(String message) {
    postToHRClient(() -> hrClient.log(this, message));
  }

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
