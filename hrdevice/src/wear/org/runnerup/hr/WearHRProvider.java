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
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.Set;
import org.runnerup.common.util.Constants;

public class WearHRProvider implements HRProvider {
  private static final String TAG = "WearHRProvider";
  public static final String NAME = "Wear OS";

  private final Context context;
  private HRClient hrClient;
  private Handler hrClientHandler;
  private boolean isScanning;
  private boolean isConnecting;
  private boolean isConnected;
  private boolean isDisconnecting;
  private String connectedNodeId;

  private int hrValue = 0;
  private long hrTimestamp = 0;
  private long hrElapsedRealtime = 0;
  private int batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;

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
  public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
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
    hrClient = null;
    hrClientHandler = null;
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
    return isConnected;
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

    Wearable.getMessageClient(context)
        .sendMessage(connectedNodeId, Constants.Wear.Path.MSG_CMD_HR_START, new byte[0])
        .addOnSuccessListener(
            integer -> {
              if (isConnecting) {
                isConnected = true;
                isConnecting = false;
                postToHRClient(() -> hrClient.onConnectResult(true));
                Wearable.getMessageClient(context).addListener(onHRMessageListener);
              }
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Failed to send Start HR message: " + e.getMessage());
              isConnected = false;
              isConnecting = false;
              postToHRClient(() -> hrClient.onConnectResult(false));
            });
  }

  @Override
  public void disconnect() {
    if (!isConnected || isDisconnecting) {
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
  }

  @Override
  public int getHRValue() {
    return hrValue;
  }

  @Override
  public long getHRValueTimestamp() {
    return hrTimestamp;
  }

  @Override
  public long getHRValueElapsedRealtime() {
    return this.hrElapsedRealtime;
  }

  @Override
  public HRData getHRData() {
    if (hrValue <= 0) {
      return null;
    }

    return new HRData().setHeartRate(hrValue).setTimestampEstimate(hrTimestamp);
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
        String path = messageEvent.getPath();

        if (Constants.Wear.Path.MSG_HEART_RATE.equals(path)) {
          byte[] payload = messageEvent.getData();
          try {
            hrValue = Integer.parseInt(new String(payload));
            hrTimestamp = System.currentTimeMillis();
            hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
          } catch (NumberFormatException e) {
            Log.w(TAG, "onMessageReceived: invalid HR payload", e);
          }
        } else if (Constants.Wear.Path.MSG_BATTERY_LEVEL.equals(path)) {
          byte[] payload = messageEvent.getData();
          try {
            batteryLevel = Integer.parseInt(new String(payload));
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
