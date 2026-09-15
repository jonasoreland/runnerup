/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
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
    Log.d(TAG, "WearHRProvider: context=" + context);
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
    Log.d(TAG, "open: handler=" + handler + ", hrClient=" + hrClient);

    this.hrClient = hrClient;
    this.hrClientHandler = handler;

    postToHRClient(() -> hrClient.onOpenResult(true));
  }

  @Override
  public void close(String from) {
    Log.d(TAG, "close: from=" + from);
    // Best-effort cleanup even if we never reached a fully connected state.
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
    Log.d(TAG, "startScan");

    if (isScanning) {
      Log.d(TAG, "startScan: Scan already in progress.");
      return;
    }

    isScanning = true;

    // Get nodes with capability to provide heart rate data for RunnerUp
    CapabilityClient capabilityClient = Wearable.getCapabilityClient(context);
    Task<CapabilityInfo> capabilityInfoTask =
        capabilityClient.getCapability(
            Constants.Wear.Capability.HEART_RATE_PROVIDER,
            CapabilityClient.FILTER_REACHABLE // Only currently connected and reachable nodes
            );

    capabilityInfoTask.addOnSuccessListener(
        capabilityInfo -> {
          Set<Node> connectedNodes = capabilityInfo.getNodes();
          Log.d(
              TAG,
              "startScan: Successfully fetched capability info. Nodes found: "
                  + connectedNodes.size());

          if (!connectedNodes.isEmpty()) {
            for (Node node : connectedNodes) {
              Log.d(
                  TAG,
                  "startScan: Found capable node: "
                      + node.getDisplayName()
                      + " ("
                      + node.getId()
                      + ")");
              // Create an HRDeviceRef for each found node
              HRDeviceRef deviceRef =
                  HRDeviceRef.create(
                      NAME, // Provider name
                      node.getDisplayName(), // Device name
                      node.getId() // Device address (using node ID as address)
                      );

              postToHRClient(() -> hrClient.onScanResult(deviceRef));
            }
          } else {
            postToHRClient(() -> hrClient.log(this, "No Wear OS device found with HR capability"));
          }

          stopScan();
        });

    capabilityInfoTask.addOnFailureListener(
        e -> {
          Log.e(TAG, "startScan: Failed to get capabilities. Error message: " + e.getMessage());
          postToHRClient(() -> hrClient.log(this, e.getMessage()));
          stopScan();
        });
  }

  @Override
  public void stopScan() {
    Log.d(TAG, "stopScan");
    isScanning = false;
  }

  @Override
  public void connect(HRDeviceRef ref) {
    Log.d(TAG, "connect: device name=" + ref.getName() + ", address=" + ref.getAddress());

    if (isConnecting || isConnected) {
      Log.d(TAG, "connect: Already connecting or connected.");
      return;
    }

    // "Connecting" means sending a message to the Wear OS app to start sending HR data
    isConnecting = true;
    connectedNodeId = ref.getAddress(); // Store the node ID we are trying to connect to

    // Send a message to the Wear OS app to start sending HR
    Log.d(
        TAG,
        "connect: Sending "
            + Constants.Wear.Path.MSG_CMD_HR_START
            + " message to node: "
            + connectedNodeId);
    Wearable.getMessageClient(context)
        .sendMessage(
            connectedNodeId,
            Constants.Wear.Path.MSG_CMD_HR_START,
            new byte[0] // No payload needed for the start command
            )
        .addOnSuccessListener(
            integer -> {
              Log.d(TAG, "connect: Start HR message sent successfully to " + connectedNodeId);
              if (isConnecting) {
                isConnected = true;
                isConnecting = false;
                postToHRClient(() -> hrClient.onConnectResult(true));

                // Start listening for HR data
                // Important: Don't forget to remove the listener to avoid leaking resources.
                Wearable.getMessageClient(context).addListener(onHRMessageListener);
              }
            })
        .addOnFailureListener(
            e -> {
              Log.e(
                  TAG,
                  "Failed to send Start HR message to " + connectedNodeId + ": " + e.getMessage());
              isConnected = false;
              isConnecting = false;
              postToHRClient(() -> hrClient.onConnectResult(false));
            });
  }

  @Override
  public void disconnect() {
    Log.d(TAG, "disconnect");

    if (!isConnected || isDisconnecting) {
      Log.d(TAG, "disconnect: Not connected or already disconnecting.");
      return;
    }

    // Send a message to the Wear OS app to stop sending HR
    isDisconnecting = true;
    if (connectedNodeId != null) {
      Log.d(
          TAG,
          "disconnect: Sending "
              + Constants.Wear.Path.MSG_CMD_HR_STOP
              + " message to node: "
              + connectedNodeId);
      Wearable.getMessageClient(context)
          .sendMessage(
              connectedNodeId,
              Constants.Wear.Path.MSG_CMD_HR_STOP,
              new byte[0] // No payload needed for the stop command
              );

      // Regardless of success or failure, consider us to be disconnected
      Log.d(TAG, "disconnect: Disconnected from " + connectedNodeId);
      postToHRClient(() -> hrClient.onDisconnectResult(true));
      reset();

      // Stop listening for HR data
      Wearable.getMessageClient(context).removeListener(onHRMessageListener);
    }
  }

  private void reset() {
    Log.d(TAG, "reset");
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

  /**
   * Helper method to safely post actions to be executed on the {@link HRClient}'s handler thread.
   *
   * @param action The action to perform on the hrClient.
   */
  private void postToHRClient(Runnable action) {
    if (hrClientHandler != null && hrClient != null) {
      hrClientHandler.post(
          () -> {
            // Re-check hrClient as it might have been nulled out between posting and execution
            if (hrClient != null) {
              action.run();
            } else {
              Log.w(
                  TAG, "postToHRClient: hrClient became null before action execution on handler.");
            }
          });
    } else {
      Log.w(TAG, "postToHRClient: Cannot post to hrClient: hrClientHandler or hrClient is null.");
    }
  }

  private final MessageClient.OnMessageReceivedListener onHRMessageListener =
      messageEvent -> {
        String path = messageEvent.getPath();
        Log.d(TAG, "onMessageReceived: " + path);

        if (Constants.Wear.Path.MSG_HEART_RATE.equals(path)) {
          byte[] payload = messageEvent.getData();
          try {
            hrValue = Integer.parseInt(new String(payload));
            hrTimestamp = System.currentTimeMillis();
            hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
            Log.d(TAG, "onMessageReceived: hrValue: " + hrValue);
          } catch (NumberFormatException e) {
            Log.w(TAG, "onMessageReceived: invalid HR payload", e);
          }
        } else if (Constants.Wear.Path.MSG_BATTERY_LEVEL.equals(path)) {
          byte[] payload = messageEvent.getData();
          try {
            batteryLevel = Integer.parseInt(new String(payload));
            Log.d(TAG, "onMessageReceived: battery level: " + batteryLevel);
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
