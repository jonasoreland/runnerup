/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Connects to a Bluetooth Low Energy module for Android versions >= 5.0
 *
 * @author jonas
 */
public class AndroidBLEHRProvider extends BtHRBase implements HRProvider {

  public static boolean checkLibrary(Context ctx) {
    return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
  }

  static final String NAME = "AndroidBLE";
  private static final String DISPLAY_NAME = "Bluetooth LE";

  private final Context context;
  private BluetoothAdapter btAdapter = null;
  private BluetoothGatt btGatt = null;
  private BluetoothDevice btDevice = null;
  private BluetoothLeScanner btScanner = null;

  private int hrValue = 0;
  private long hrTimestamp = 0;
  private long hrElapsedRealtime = 0;
  private int batteryLevel = HRProvider.BATTERY_LEVEL_UNAVAILABLE;
  private boolean hasBatteryService = false;

  private long mPrevHrTimestampNotZero = 0;

  private boolean mIsScanning = false;
  private boolean mIsConnected = false;
  private boolean mIsConnecting = false;
  private boolean mIsDisconnecting = false;
  private boolean mNotifictionsOn = false;
  private final boolean mSupportPaired;

  public AndroidBLEHRProvider(Context ctx) {
    context = ctx;
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Resources res = context.getResources();
    mSupportPaired = prefs.getBoolean(res.getString(R.string.pref_bt_paired_ble), false);
  }

  @Override
  public String getName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getProviderName() {
    return NAME;
  }

  public boolean isEnabled() {
    return Bt20Base.isEnabledImpl();
  }

  public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
    return Bt20Base.startEnableIntentImpl(activity, requestCode);
  }

  private static String statusToString(int status) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      return "GATT_SUCCESS";
    } else if (status == BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED) {
      return "GATT_REQUEST_NOT_SUPPORTED";
    } else if (status == BluetoothGatt.GATT_CONNECTION_TIMEOUT) {
      return "GATT_CONNECTION_TIMEOUT";
    } else if (status == BluetoothGatt.GATT_FAILURE) {
      return "GATT_FAILURE";
    } else if (status == BluetoothGatt.GATT_CONNECTION_CONGESTED) {
      return "GATT_CONNECTION_CONGESTED";
    } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
      return "GATT_READ_NOT_PERMITTED";
    }
    return "<unknown status: " + status + " >";
  }

  private static String stateToString(int state) {
    if (state == BluetoothProfile.STATE_DISCONNECTED) {
      return "DISCONNECTED";
    } else if (state == BluetoothProfile.STATE_CONNECTED) {
      return "CONNECTED";
    }
    return "<unknown state: " + state + " >";
  }

  @Override
  public void open(Handler handler, HRClient hrClient) {
    this.hrClient = hrClient;
    this.hrClientHandler = handler;
    System.err.println("KESO open: " + getName());

    if (btAdapter == null) {
      btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    hrClient.onOpenResult(btAdapter != null);
  }

  @Override
  public void close(String from) {
    var client = hrClient;
    var handler = hrClientHandler;
    log("close, from: " + from);
    stopScan();
    disconnect();

    close(btGatt);
    btGatt = null;
    btAdapter = null;
    btDevice = null;
    hrClient = null;
    hrClientHandler = null;
    if (client != null && handler != null) {
      handler.post(
          () -> {
            log("close(" + from + ") => onCloseResult(true)");
            client.onCloseResult(true);
          });
    } else {
      log("close(" + from + ") with client: " + client + ", handler: " + handler + " => nothing");
    }
  }

  private final BluetoothGattCallback btGattCallbacks =
      new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic arg0) {
          try {
            if (!checkBtGattOnlyLogError(gatt)) {
              return;
            }

            if (!arg0.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
              log("onCharacteristicChanged(" + arg0 + ") != HEART_RATE ??");
              return;
            }

            int length = arg0.getValue().length;
            if (length == 0) {
              log("onCharacteristicChanged length = 0");
              return;
            }

            int val;
            if (isHeartRateInUINT16(arg0.getValue()[0])) {
              val = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            } else {
              val = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
            }

            hrTimestamp = System.currentTimeMillis();
            hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();

            if (val == 0) {
              // Some HR straps (low quality?) report 0 when it cannot read HR but still have
              // connection
              // (especially first value, so it never connects)
              // Previously this was considered as an indication that the strap was disconnected
              // This keeps this behavior, reporting old value until timeout.
              // Discussion in PR #477
              final long mMaxHrTimestampNotZero = 60 * 1000;
              if (mPrevHrTimestampNotZero > 0
                  && hrTimestamp - mPrevHrTimestampNotZero > mMaxHrTimestampNotZero) {
                reportDisconnected("got hrValue = 0 => reportConnectFailed");
                return;
              }
            } else {
              hrValue = val;
              mPrevHrTimestampNotZero = hrTimestamp;
            }

            if (mIsConnecting) {
              reportConnected(true);
            }
          } catch (Exception e) {
            log("onCharacteristicChanged => " + e);
            reportDisconnected("Exception in onCharacteristicChanged: " + e);
          }
        }

        @Override
        public void onCharacteristicRead(
            BluetoothGatt gatt, BluetoothGattCharacteristic arg0, int status) {
          try {
            log(
                "onCharacteristicRead(): "
                    + gatt
                    + ", char: "
                    + arg0.getUuid()
                    + ", status: "
                    + status);

            if (!checkBtGatt(gatt)) {
              return;
            }

            UUID charUuid = arg0.getUuid();
            if (charUuid.equals(FIRMWARE_REVISON_UUID)) {
              log("firmware => startHR()");
              // triggered from DummyReadForSecLevelCheck
              startHR();
            } else if (mSupportPaired && charUuid.equals(HARDWARE_REVISON_UUID)) {
              // Some paired devices like Huami (MiBand) has no firmware uuid
              log("BLE hardware rev => startHR()");
              // triggered from DummyReadForSecLevelCheck
              startHR();
            } else if (charUuid.equals(BATTERY_LEVEL_CHARAC)) {
              log("batterylevel: " + arg0);
              batteryLevel = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
              log("Battery level: " + batteryLevel);

              log(" => startHR()");
              // triggered from DummyReadForSecLevelCheck
              startHR();

            } else {
              log("Unknown characteristic received: " + charUuid);
            }
          } catch (Exception e) {
            log("onCharacteristicRead => " + e);
            reportConnectFailed("Exception in onCharacteristicRead: " + e);
          }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
          try {
            log(
                "onConnectionStateChange: "
                    + gatt
                    + ", status: "
                    + statusToString(status)
                    + ", newState: "
                    + stateToString(newState)
                    + ", mIsConnecting: "
                    + mIsConnecting
                    + ", mIsConnected: "
                    + mIsConnected
                    + ", mIsDisconnecting: "
                    + mIsDisconnecting);
            if (!checkBtGatt(gatt)) {
              log("checkBtGatt => return");
              return;
            }
            log("after checkBtGatt");

            if (mIsConnecting) {
              if (newState == BluetoothProfile.STATE_CONNECTED) {
                boolean res = discoverServices(btGatt);
                log("discoverServices() => " + res);
              } else {
                boolean res = connect(btGatt);
                log("reconnect while connecting => btGatt.connect() => " + res);
              }
              return;
            }

            if (mIsDisconnecting) {
              log("mIsDisconnecting => notify");
              synchronized (this) {
                close(btGatt);
                btGatt = null;
                this.notifyAll();
                return;
              }
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
              reportDisconnected("onConnectionStateChange => reportDisconnected");
              return;
            }
            log("onConnectionStateChange => Already connected?");
          } catch (Exception e) {
            log("onConnectionStateChange => " + e);
            reportConnectFailed("Exception in onConnectionStateChange: " + e);
          }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor arg0, int status) {

          BluetoothGattCharacteristic mHRMcharac = arg0.getCharacteristic();
          if (!enableNotification(gatt, true, mHRMcharac)) {
            reportConnectFailed("Failed to enable notification in onDescriptorRead");
          }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
          log("onServicesDiscoverd(): " + gatt + ", status: " + status);

          if (!checkBtGatt(gatt)) {
            return;
          }

          List<BluetoothGattService> list = btGatt.getServices();
          for (BluetoothGattService s : list) {
            log("Found service: " + s.getType() + ", " + s.getInstanceId() + ", " + s.getUuid());
            for (BluetoothGattCharacteristic a : s.getCharacteristics()) {
              log("  char: " + a.getUuid());
            }
            for (BluetoothGattService a : s.getIncludedServices()) {
              log("  serv: " + a.getUuid());
            }

            if (s.getUuid().equals(BATTERY_SERVICE)) {
              hasBatteryService = true;
            }
          }

          log(" => DummyRead");
          DummyReadForSecLevelCheck(gatt);
          // if GATT_SUCCESS, continue in onCharacteristicRead
          // no report on error
        }

        /*
         * from Samsung HRPService.java
         */
        private void DummyReadForSecLevelCheck(BluetoothGatt btGatt) {
          if (btGatt == null) {
            return;
          }

          if (hasBatteryService && readBatteryLevel()) {
            return;
          }

          BluetoothGattService disService = btGatt.getService(DIS_UUID);
          if (disService == null) {
            reportConnectFailed("Dis service not found");
            return;
          }
          BluetoothGattCharacteristic firmwareIdCharc =
              disService.getCharacteristic(FIRMWARE_REVISON_UUID);
          if (mSupportPaired && firmwareIdCharc == null) {
            firmwareIdCharc = disService.getCharacteristic(HARDWARE_REVISON_UUID);
          }
          if (firmwareIdCharc == null) {
            reportConnectFailed("firmware revison charateristic not found!");
            return;
          }

          if (!readCharacteristic(btGatt, firmwareIdCharc)) {
            reportConnectFailed("firmware revison reading is failed!");
          }
          // continue in onCharacteristicRead
        }

        private boolean isHeartRateInUINT16(byte b) {
          return (b & 1) != 0;
        }

        private void startHR() {
          BluetoothGattService mHRP = btGatt.getService(HRP_SERVICE);
          if (mHRP == null) {
            reportConnectFailed("HRP service not found!");
            return;
          }

          BluetoothGattCharacteristic mHRMcharac =
              mHRP.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
          if (mHRMcharac == null) {
            reportConnectFailed("HEART RATE MEASUREMENT charateristic not found!");
            return;
          }
          BluetoothGattDescriptor mHRMccc = mHRMcharac.getDescriptor(CCC);
          if (mHRMccc == null) {
            reportConnectFailed("CCC for HEART RATE MEASUREMENT charateristic not found!");
            return;
          }
          if (!readDescriptor(btGatt, mHRMccc)) {
            reportConnectFailed("readDescriptor() is failed");
          }
          // Continue in onDescriptorRead
        }

        private boolean readBatteryLevel() {
          BluetoothGattService mBS = btGatt.getService(BATTERY_SERVICE);
          if (mBS == null) {
            log("Battery service not found.");
            return false;
          }

          BluetoothGattCharacteristic mBLcharac = mBS.getCharacteristic(BATTERY_LEVEL_CHARAC);
          if (mBLcharac == null) {
            reportConnectFailed("BATTERY LEVEL charateristic not found!");
            return false;
          }

          if (!readCharacteristic(btGatt, mBLcharac)) {
            log("readCharacteristic(" + mBLcharac.getUuid() + ") failed");
            return false;
          }
          // continue in onCharacteristicRead
          return true;
        }
      };

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean enableNotification(
      BluetoothGatt gatt, boolean onoff, BluetoothGattCharacteristic charac) {
    if (gatt == null) {
      return false;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "enableNotification")) {
      return false;
    }
    if (!gatt.setCharacteristicNotification(charac, onoff)) {
      log("btGatt.setCharacteristicNotification() failed");
      return false;
    }

    BluetoothGattDescriptor clientConfig = charac.getDescriptor(CCC);
    if (clientConfig == null) {
      log("clientConfig == null");
      return false;
    }

    if (onoff) {
      clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    } else {
      clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    }
    boolean result = gatt.writeDescriptor(clientConfig);
    if (result) {
      mNotifictionsOn = onoff;
    }
    return result;
  }

  @Override
  public boolean isScanning() {
    return mIsScanning;
  }

  private final ScanCallback mLeScanCallback =
      new ScanCallback() {
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
          log("onBatchScanResults: ");

          for (var result : results) {
            addScanResult(0, result.getDevice());
          }
        }

        @Override
        public void onScanFailed(int errorCode) {
          reportConnectFailed("onScanFailed: " + errorCode);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
          if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
            log("onScanResult: CALLBACK_TYPE_MATCH_LOST");
          } else {
            addScanResult(callbackType, result.getDevice());
          }
        }
      };

  private void addScanResult(int callbackType, BluetoothDevice device) {
    if (hrClient == null) {
      log("onScanResult(" + callbackType + ", " + device + ") - hrClient NULL");
      return;
    }

    if (hrClientHandler == null) {
      log("onScanResult(" + callbackType + ", " + device + ") - hrClientHandler NULL");
      return;
    }

    String address = device.getAddress();
    if (mIsConnecting && btDevice != null && address.equals(btDevice.getAddress())) {
      log(
          "onScanResult("
              + callbackType
              + ", "
              + device
              + ") - match && mIsConnecting => stopScan");
      stopScan();

      btGatt = connectGatt(btDevice, btGattCallbacks);
      if (btGatt == null) {
        reportConnectFailed("connectGatt returned null");
      } else {
        log("connectGatt: " + btGatt);
      }
      return;
    }

    if (mScanDevices.contains(address)) {
      // No log here...the same device is reported a bunch of times!
      return;
    }

    log("onScanResult(" + callbackType + ", " + device + ")");
    mScanDevices.add(address);

    hrClientHandler.post(
        () -> {
          if (mIsScanning) { // NOTE: mIsScanning in user-thread
            hrClient.onScanResult(Bt20Base.createDeviceRef(NAME, device));
          }
        });
  }

  private final HashSet<String> mScanDevices = new HashSet<>();

  @Override
  public void startScan() {
    if (mIsScanning || btAdapter == null) {
      return;
    }

    mScanDevices.clear();

    if (mSupportPaired) {
      if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "getBondedDevices")) {
        for (BluetoothDevice btDeviceThis : btAdapter.getBondedDevices()) {
          if (btDeviceThis.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
            log("Ignoring paired non BLE device: " + btDeviceThis.getName());
            continue;
          }

          // Paired device, for instance Huami/Amazfit Bip S could be supported
          log("Trying paired generic BLE device: " + btDeviceThis.getName());
          addScanResult(0, btDeviceThis);
        }
      }
    }

    btScanner = btAdapter.getBluetoothLeScanner();
    if (checkPermission(Manifest.permission.BLUETOOTH_SCAN, "startScan")) {
      log("startScan");
      mIsScanning = true;
      btScanner.startScan(
          Collections.singletonList(
              new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HRP_SERVICE)).build()),
          new ScanSettings.Builder().build(),
          mLeScanCallback);
    }
  }

  @Override
  public void stopScan() {
    if (mIsScanning) {
      mIsScanning = false;
      if (btScanner != null && checkPermission(Manifest.permission.BLUETOOTH_SCAN, "stopScan")) {
        log("stopScan");
        btScanner.stopScan(mLeScanCallback);
      }
    }
  }

  @Override
  public boolean isConnected() {
    return mIsConnected;
  }

  @Override
  public boolean isConnecting() {
    return mIsConnecting;
  }

  private String getName(BluetoothDevice device) {
    if (device == null) {
      return null;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "getDeviceName")) {
      return null;
    }
    return device.getName();
  }

  private BluetoothGatt connectGatt(BluetoothDevice device, BluetoothGattCallback callbacks) {
    if (device == null) {
      return null;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "connectGatt")) {
      return null;
    }
    return device.connectGatt(context, false, callbacks);
  }

  private boolean connect(BluetoothGatt gatt) {
    if (gatt == null) {
      return false;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "btGatt.connect")) {
      return false;
    }
    return gatt.connect();
  }

  private boolean discoverServices(BluetoothGatt gatt) {
    if (gatt == null) {
      return false;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "btGatt.connect")) {
      return false;
    }
    return gatt.discoverServices();
  }

  private boolean readCharacteristic(
      BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    if (gatt == null) {
      return false;
    }
    if (characteristic == null) {
      return false;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "btGatt.readCharacteristic")) {
      return false;
    }
    return gatt.readCharacteristic(characteristic);
  }

  private boolean readDescriptor(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
    if (gatt == null) {
      return false;
    }
    if (descriptor == null) {
      return false;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "btGatt.readDescriptor")) {
      return false;
    }
    return gatt.readDescriptor(descriptor);
  }

  private void disconnect(BluetoothGatt gatt) {
    if (gatt == null) {
      return;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "btGatt.disconnect")) {
      return;
    }
    gatt.disconnect();
  }

  private void close(BluetoothGatt gatt) {
    if (gatt == null) {
      return;
    }
    if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT, "gatt.close")) {
      return;
    }
    gatt.close();
  }

  private static BluetoothGattCharacteristic getHeartrateCharacteristic(BluetoothGatt gatt) {
    if (gatt == null) {
      return null;
    }
    BluetoothGattService mHRP = gatt.getService(HRP_SERVICE);
    if (mHRP == null) {
      return null;
    }

    return mHRP.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
  }

  @Override
  public void connect(HRDeviceRef ref) {
    stopScan();

    if (!Bt20Base.isEnabledImpl() || btAdapter == null) {
      reportConnectFailed("BT is not enabled");
      return;
    }

    BluetoothDevice dev = btAdapter.getRemoteDevice(ref.deviceAddress);

    if (mIsConnected || mIsConnecting) {
      return;
    }

    mIsConnecting = true;
    btDevice = dev;

    var deviceName = getName(dev);

    if (ref.deviceName == null || deviceName == null || !deviceName.contentEquals(ref.deviceName)) {
      /*
       * If device doesn't match name, scan for before connecting
       */
      log("Scan before connect");
      startScan();
      return;
    } else {
      log("Skip scan before connect");
    }
    btGatt = connectGatt(btDevice, btGattCallbacks);
    if (btGatt == null) {
      reportConnectFailed("connectGatt returned null");
    } else {
      log("connectGatt: " + btGatt);
    }
  }

  private void reportConnected(final boolean b) {
    if (hrClientHandler != null) {
      hrClientHandler.post(
          () -> {
            if (mIsConnecting && hrClient != null) {
              mIsConnected = b;
              mIsConnecting = false;
              hrClient.onConnectResult(b);
            }
          });
    }
  }

  private void reportConnectFailed(String string) {
    log("reportConnectFailed(" + string + ")");
    if (btGatt != null) {
      disconnect(btGatt);
      close(btGatt);
      btGatt = null;
    }
    btDevice = null;
    reportConnected(false);
  }

  @Override
  public void disconnect() {
    log(
        "disconnect()"
            + ", mIsConnecting: "
            + mIsConnecting
            + ", mIsConnected: "
            + mIsConnected
            + ", mIsDisconnecting: "
            + mIsDisconnecting
            + ", btGatt: "
            + btGatt
            + ", btDevice: "
            + btDevice);

    final boolean isConnected = mIsConnected;
    mIsConnecting = false;
    do {
      if (mIsDisconnecting) {
        return;
      }
      mIsDisconnecting = true;

      if (mNotifictionsOn) {
        if (!disableNotification(btGatt)) {
          log("disconnect: Failed to disable notification");
        }
      }

      if (!mIsConnecting && !mIsConnected) {
        break;
      }

      disconnect(btGatt);

      if (isConnected) {
        log("close btGatt in onConnectionState, btGatt=" + btGatt);
        // close btGatt in onConnectionState
        synchronized (this) {
          long end = System.currentTimeMillis() + 2000; // wait max 2 seconds
          while (btGatt != null && System.currentTimeMillis() < end) {
            log("waiting for btGatt to become null");
            try {
              this.wait(500);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          BluetoothGatt copy = btGatt;
          if (copy != null) {
            log("close btGatt in disconnect() after waiting 2 secs");
            close(copy);
          }
        }
      } else {
        log("close btGatt here in disconnect()");
        BluetoothGatt copy = btGatt;
        close(copy);
      }
    } while (false);

    btGatt = null;
    btDevice = null;
    mIsConnected = false;
    mIsConnecting = false;
    reportDisconnected("user request");
    mIsDisconnecting = false;
  }

  boolean disableNotification(BluetoothGatt gatt) {
    if (gatt == null) {
      log("disableNotification: gatt == null");
      return false;
    }

    BluetoothGattService mHRP = gatt.getService(HRP_SERVICE);
    if (mHRP == null) {
      log("disableNotification: HRP service not found!");
      return false;
    }

    BluetoothGattCharacteristic mHRMcharac = mHRP.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
    if (mHRMcharac == null) {
      log("disableNotification: HEART RATE MEASUREMENT charateristic not found!");
      return false;
    }

    return enableNotification(gatt, false, mHRMcharac);
  }

  /***
   * Wrapper to check permissions.
   * These permissions should already have been checked and requested before this module,
   * this is primary to catch unexpected uses and code inspect.
   * @param context The context
   * @param perm The name of the permission
   * @return true if permission is granted
   */
  private boolean checkPermission(Context context, String perm, String from) {
    boolean check =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            || ActivityCompat.checkSelfPermission(context, perm)
                == PackageManager.PERMISSION_GRANTED;
    if (!check) {
      log("No permission: " + perm + ", from: " + from);
    }
    return check;
  }

  private boolean checkPermission(String perm, String from) {
    return checkPermission(context, perm, from);
  }

  private void reportDisconnectFailed(String string) {
    log("disconnect failed: " + string);
    hrClient.onDisconnectResult(false);
  }

  private void reportDisconnected(String msg) {
    log("disconnected: " + msg);
    var isConnecting = mIsConnecting;
    var isConnected = mIsConnected;
    var isDisconnecting = mIsDisconnecting;
    var client = hrClient;
    mIsConnecting = false;
    mIsConnected = false;
    mIsDisconnecting = false;
    if (client == null) {
      return;
    }

    if (isDisconnecting) {
      hrClient.onDisconnectResult(true);
    } else if (isConnecting) {
      hrClient.onConnectResult(false);
    } else if (isConnected) {
      hrClient.onConnectResult(false);
    }
  }

  @Override
  public int getHRValue() {
    return this.hrValue;
  }

  @Override
  public long getHRValueTimestamp() {
    return this.hrTimestamp;
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
  public boolean includePairingBLE() {
    return mSupportPaired;
  }

  @Override
  public int getBatteryLevel() {
    return this.batteryLevel;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean checkBtGatt(BluetoothGatt gatt) {
    return checkBtGatt(gatt, false);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean checkBtGattOnlyLogError(BluetoothGatt gatt) {
    return checkBtGatt(gatt, true);
  }

  private synchronized boolean checkBtGatt(BluetoothGatt gatt, boolean onlyLogError) {
    if (btGatt == null) {
      if (!onlyLogError) {
        log("checkBtGatt, btGatt == null => true");
      }
      btGatt = gatt;
      return true;
    }
    if (btGatt == gatt) {
      if (!onlyLogError) {
        log("checkBtGatt, btGatt == gatt => true");
      }
      return true;
    }
    log("checkBtGatt, btGatt(" + btGatt + ") != gatt(" + gatt + ") => false");
    return false;
  }
}
