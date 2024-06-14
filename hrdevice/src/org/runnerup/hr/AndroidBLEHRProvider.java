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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Connects to a Bluetooth Low Energy module for Android versions >= 4.3
 *
 * @author jonas
 */

public class AndroidBLEHRProvider extends BtHRBase implements HRProvider {

    public static boolean checkLibrary(Context ctx) {

        return ctx.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE);
    }

    static final String NAME = "AndroidBLE";
    private static final String DISPLAY_NAME = "Bluetooth LE";

    private static final UUID[] SCAN_UUIDS = {
            HRP_SERVICE
    };

    private final Context context;
    private BluetoothAdapter btAdapter = null;
    private BluetoothGatt btGatt = null;
    private BluetoothDevice btDevice = null;
    private int hrValue = 0;
    private long hrTimestamp = 0;
    private long hrElapsedRealtime = 0;
    private int batteryLevel = -1;
    private boolean hasBatteryService = false;

    private long mPrevHrTimestampNotZero = 0;

    private boolean mIsScanning = false;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;
    private boolean mIsDisconnecting = false;
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

    @Override
    public void open(Handler handler, HRClient hrClient) {
        this.hrClient = hrClient;
        this.hrClientHandler = handler;

        if (btAdapter == null) {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        hrClient.onOpenResult(btAdapter != null);
    }

    @Override
    public void close() {
        stopScan();
        disconnect();

        if (btGatt != null) {
            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in close");
                btGatt.close();
            }

            btGatt = null;
        }

        btAdapter = null;
        btDevice = null;
        hrClient = null;
        hrClientHandler = null;
    }

    private final BluetoothGattCallback btGattCallbacks = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic arg0) {
            try {
                if (!checkBtGattOnlyLogError(gatt)) {
                    return;
                }

                if (!arg0.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
                    log("onCharacteristicChanged(" + arg0
                            + ") != HEART_RATE ??");
                    return;
                }

                int length = arg0.getValue().length;
                if (length == 0) {
                    log("onCharacteristicChanged length = 0");
                    return;
                }

                int val;
                if (isHeartRateInUINT16(arg0.getValue()[0])) {
                    val = arg0.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT16, 1);
                } else {
                    val = arg0.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                }

                hrTimestamp = System.currentTimeMillis();
                hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();

                if (val == 0) {
                    // Some HR straps (low quality?) report 0 when it cannot read HR but still have connection
                    // (especially first value, so it never connects)
                    // Previously this was considered as an indication that the strap was disconnected
                    // This keeps this behavior, reporting old value until timeout.
                    // Discussion in PR #477
                    final long mMaxHrTimestampNotZero = 60 * 1000;
                    if (mPrevHrTimestampNotZero > 0 &&
                            hrTimestamp - mPrevHrTimestampNotZero > mMaxHrTimestampNotZero) {
                        if (mIsConnecting) {
                            reportConnectFailed("got hrValue = 0 => reportConnectFailed");
                            return;
                        }
                        log("got hrValue == 0 => disconnecting");
                        reportDisconnected();
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
                if (mIsConnecting)
                    reportConnectFailed("Exception in onCharacteristicChanged: " + e);
                else if (mIsConnected)
                    reportDisconnected();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic arg0, int status) {
            try {
                log("onCharacteristicRead(): " + gatt + ", char: "
                        + arg0.getUuid() + ", status: " + status);

                if (!checkBtGatt(gatt))
                    return;

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
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {

            try {
                log("onConnectionStateChange: " + gatt
                        + ", status: " + status + ", newState: " + newState);
                log("STATUS_SUCCESS:" + BluetoothGatt.GATT_SUCCESS);
                log("STATE_CONNECTED: "
                        + BluetoothProfile.STATE_CONNECTED
                        + ", STATE_DISCONNECTED: "
                        + BluetoothProfile.STATE_DISCONNECTED);

                if (!checkBtGatt(gatt)) {
                    return;
                }

                if (mIsConnecting) {
                    if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                        log("No BLUETOOTH_CONNECT permission in onConnectionStateChange() when connecting");
                        return;
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        boolean res = btGatt.discoverServices();
                        log("discoverServices() => " + res);
                    } else {
                        boolean res = btGatt.connect();
                        log("reconnect while connecting => btGatt.connect() => "
                                + res);
                    }
                    return;
                }

                if (mIsDisconnecting) {
                    log("mIsDisconnecting => notify");
                    if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                        log("No BLUETOOTH_CONNECT permission in onConnectionStateChange() when disconnecting");
                        return;
                    }

                    synchronized (this) {
                        btGatt.close();
                        btGatt = null;
                        this.notifyAll();
                        return;
                    }
                }

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    reportDisconnected();
                    return;
                }
                log("onConnectionStateChange => Already connected?");
            } catch (Exception e) {
                log("onConnectionStateChange => " + e);
                reportConnectFailed("Exception in onConnectionStateChange: " + e);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor arg0, int status) {

            BluetoothGattCharacteristic mHRMcharac = arg0.getCharacteristic();
            if (!enableNotification(true, mHRMcharac)) {
                reportConnectFailed("Failed to enable notification in onDescriptorRead");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            log("onServicesDiscoverd(): " + gatt + ", status: "
                    + status);

            if (!checkBtGatt(gatt))
                return;

            List<BluetoothGattService> list = btGatt.getServices();
            for (BluetoothGattService s : list) {
                log("Found service: " + s.getType() + ", "
                        + s.getInstanceId() + ", " + s.getUuid());
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
            if (btGatt == null)
                return;

            if (hasBatteryService && readBatteryLevel()) {
                return;
            }

            BluetoothGattService disService = btGatt.getService(DIS_UUID);
            if (disService == null) {
                reportConnectFailed("Dis service not found");
                return;
            }
            BluetoothGattCharacteristic firmwareIdCharc = disService
                    .getCharacteristic(FIRMWARE_REVISON_UUID);
            if (mSupportPaired && firmwareIdCharc == null) {
                firmwareIdCharc = disService
                        .getCharacteristic(HARDWARE_REVISON_UUID);
            }
            if (firmwareIdCharc == null) {
                reportConnectFailed("firmware revison charateristic not found!");
                return;
            }

            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in DummyReadForSecLevelCheck");
                return;
            }
            if (!btGatt.readCharacteristic(firmwareIdCharc)) {
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

            BluetoothGattCharacteristic mHRMcharac = mHRP
                    .getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
            if (mHRMcharac == null) {
                reportConnectFailed("HEART RATE MEASUREMENT charateristic not found!");
                return;
            }
            BluetoothGattDescriptor mHRMccc = mHRMcharac.getDescriptor(CCC);
            if (mHRMccc == null) {
                reportConnectFailed("CCC for HEART RATE MEASUREMENT charateristic not found!");
                return;
            }
            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in startHR");
                return;
            }
            if (!btGatt.readDescriptor(mHRMccc)) {
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

            BluetoothGattCharacteristic mBLcharac = mBS
                    .getCharacteristic(BATTERY_LEVEL_CHARAC);
            if (mBLcharac == null) {
                reportConnectFailed("BATTERY LEVEL charateristic not found!");
                return false;
            }

            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in readBatteryLevel");
                return false;
            }
            if (!btGatt.readCharacteristic(mBLcharac)) {
                log("readCharacteristic(" + mBLcharac.getUuid() + ") failed");
                return false;
            }
            // continue in onCharacteristicRead
            return true;
        }
    };

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean enableNotification(boolean onoff,
                                       BluetoothGattCharacteristic charac) {
        if (btGatt == null || !checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
            log("No BLUETOOTH_CONNECT permission in enableNotification");
            return false;
        }

        if (!btGatt.setCharacteristicNotification(charac, onoff)) {
            log("btGatt.setCharacteristicNotification() failed");
            return false;
        }

        BluetoothGattDescriptor clientConfig = charac.getDescriptor(CCC);
        if (clientConfig == null) {
            log("clientConfig == null");
            return false;
        }

        if (onoff) {
            clientConfig
                    .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig
                    .setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return btGatt.writeDescriptor(clientConfig);
    }

    @Override
    public boolean isScanning() {
        return mIsScanning;
    }

    // Using a deprecated API - change in API 21 (Marshmallow)
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {
            if (hrClient == null)
                return;

            if (hrClientHandler == null)
                return;

            String address = device.getAddress();
            if (mIsConnecting
                    && btDevice != null
                    && address.equals(btDevice.getAddress())) {
                stopScan();

                if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                    log("No BLUETOOTH_CONNECT permission in onLeScan");
                    return;
                }
                btGatt = btDevice.connectGatt(context, false, btGattCallbacks);
                if (btGatt == null) {
                    reportConnectFailed("connectGatt returned null");
                } else {
                    log("connectGatt: " + btGatt);
                }
                return;
            }

            if (mScanDevices.contains(address))
                return;

            mScanDevices.add(address);

            hrClientHandler.post(() -> {
                if (mIsScanning) { // NOTE: mIsScanning in user-thread
                    hrClient.onScanResult(Bt20Base.createDeviceRef(NAME, device));
                }
            });
        }

    };

    private final HashSet<String> mScanDevices = new HashSet<>();

    @Override
    public void startScan() {
        if (mIsScanning || btAdapter == null)
            return;

        mIsScanning = true;
        mScanDevices.clear();

        if (mSupportPaired) {
            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in startScan");
                return;
            }

            for (BluetoothDevice btDeviceThis : btAdapter.getBondedDevices()) {
                if (btDeviceThis.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
                    log("Ignoring paired non BLE device: " + btDeviceThis.getName());
                    continue;
                }

                // Paired device, for instance Huami/Amazfit Bip S could be supported
                log("Trying paired generic BLE device: " + btDeviceThis.getName());
                mLeScanCallback.onLeScan(btDeviceThis, 0, null);
            }
        }
        btAdapter.startLeScan(SCAN_UUIDS, mLeScanCallback);
    }

    @Override
    public void stopScan() {
        if (mIsScanning) {
            mIsScanning = false;
            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_SCAN)) {
                log("No BLUETOOTH_SCAN permission in stopScan");
                return;
            }

            btAdapter.stopLeScan(mLeScanCallback);
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

    @Override
    public void connect(HRDeviceRef ref) {
        stopScan();

        if (!Bt20Base.isEnabledImpl() || btAdapter == null) {
            reportConnectFailed("BT is not enabled");
            return;
        }

        BluetoothDevice dev = btAdapter.getRemoteDevice(ref.deviceAddress);

        if (mIsConnected || mIsConnecting)
            return;

        mIsConnecting = true;
        btDevice = dev;
        if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
            log("No BLUETOOTH_CONNECT permission in connect()");
            return;
        }

        if (ref.deviceName == null || dev.getName() == null
                || !dev.getName().contentEquals(ref.deviceName)) {
            /*
             * If device doesn't match name, scan for before connecting
             */
            log("Scan before connect");
            startScan();
            return;
        } else {
            log("Skip scan before connect");
        }

        btGatt = btDevice.connectGatt(context, false, btGattCallbacks);
        if (btGatt == null) {
            reportConnectFailed("connectGatt returned null");
        } else {
            log("connectGatt: " + btGatt);
        }
    }

    private void reportConnected(final boolean b) {
        if (hrClientHandler != null) {
            hrClientHandler.post(() -> {
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
            if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
                log("No BLUETOOTH_CONNECT permission in reportConnectFailed");
                return;
            }

            btGatt.disconnect();
            btGatt.close();
            btGatt = null;
        }
        btDevice = null;
        reportConnected(false);
    }

    @Override
    public void disconnect() {
        if (btGatt == null || btDevice == null) {
            return;
        }

        if (!mIsConnecting && !mIsConnected)
            return;

        if (mIsDisconnecting)
            return;

        boolean isConnected = mIsConnected;
        mIsConnected = false;
        mIsConnecting = false;
        mIsDisconnecting = true;

        do {
            BluetoothGattService mHRP = btGatt.getService(HRP_SERVICE);
            if (mHRP == null) {
                reportDisconnectFailed("HRP service not found!");
                break;
            }

            BluetoothGattCharacteristic mHRMcharac = mHRP
                    .getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
            if (mHRMcharac == null) {
                reportDisconnectFailed("HEART RATE MEASUREMENT charateristic not found!");
                break;
            }

            if (!enableNotification(false, mHRMcharac)) {
                reportDisconnectFailed("disableNotfication");
                break;
            }
        } while (false);

        if (!checkPermission(context, Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)) {
            log("No BLUETOOTH_CONNECT permission in disconnect");
            return;
        }

        if (btGatt != null) {
            btGatt.disconnect();
        }

        if (isConnected) {
            log("close btGatt in onConnectionState");
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
                    copy.close();
                    btGatt = null;
                }
            }
        } else {
            log("close btGatt here in disconnect()");
            BluetoothGatt copy = btGatt;
            if (copy != null) {
                copy.close();
            }

            btGatt = null;
        }

        btDevice = null;
        mIsDisconnecting = false;
        reportDisconnected();
    }

    /***
     * Wrapper to check permissions.
     * These permissions should already have been checked and requested before this module,
     * this is primary to catch unexpected uses and code inspect.
     * @param context The context
     * @param minCode Build version code
     * @param perm The name of the permission
     * @return true if permission is granted
     */
    private boolean checkPermission(Context context, int minCode, String perm) {
        return Build.VERSION.SDK_INT < minCode || ActivityCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void reportDisconnectFailed(String string) {
        log("disconnect failed: " + string);
        hrClient.onDisconnectResult(false);
    }

    private void reportDisconnected() {
        hrClient.onDisconnectResult(true);
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
            if (!onlyLogError)
                log("checkBtGatt, btGatt == null => true");
            btGatt = gatt;
            return true;
        }
        if (btGatt == gatt) {
            if (!onlyLogError)
                log("checkBtGatt, btGatt == gatt => true");
            return true;
        }
        log("checkBtGatt, btGatt("+btGatt+") != gatt(" + gatt + ") => false");
        return false;
    }
}
