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

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

@TargetApi(18)
public class AndroidBLEHRProvider implements HRProvider {

    public static boolean checkLibrary(Context ctx) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
            return false;

        if (!ctx.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        return true;
    }

    static final String NAME = "AndroidBLE";
    static final String DISPLAY_NAME = "Bluetooth SMART (BLE)";
    static final UUID HRP_SERVICE = UUID
            .fromString("0000180D-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_SERVICE = UUID
            .fromString("0000180f-0000-1000-8000-00805f9b34fb");
    static final UUID FIRMWARE_REVISON_UUID = UUID
            .fromString("00002a26-0000-1000-8000-00805f9b34fb");
    static final UUID DIS_UUID = UUID
            .fromString("0000180a-0000-1000-8000-00805f9b34fb");
    static final UUID HEART_RATE_MEASUREMENT_CHARAC = UUID
            .fromString("00002A37-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_LEVEL_CHARAC = UUID
            .fromString("00002A19-0000-1000-8000-00805f9b34fb");
    static final UUID CCC = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    static final UUID[] SCAN_UUIDS = {
        HRP_SERVICE
    };
    static boolean AVOID_SCAN_WITH_UUID = false;
    static boolean CONNECT_IN_OWN_THREAD_FROM_ON_LE_SCAN = false;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // 4.3
            AVOID_SCAN_WITH_UUID = true;
            CONNECT_IN_OWN_THREAD_FROM_ON_LE_SCAN = true;
        }
    }

    private Context context;
    private BluetoothAdapter btAdapter = null;
    private BluetoothGatt btGatt = null;
    private BluetoothDevice btDevice = null;
    private int hrValue = 0;
    private long hrTimestamp = 0;
    private int batteryLevel = -1;
    private boolean hasBatteryService = false;

    private HRClient hrClient;
    private Handler hrClientHandler;

    private boolean mIsScanning = false;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;
    private boolean mIsDisconnecting = false;

    public AndroidBLEHRProvider(Context ctx) {
        context = ctx;
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

    public boolean startEnableIntent(Activity activity, int requestCode) {
        return Bt20Base.startEnableIntentImpl(activity, requestCode);
    }

    @Override
    public void open(Handler handler, HRClient hrClient) {
        this.hrClient = hrClient;
        this.hrClientHandler = handler;

        if (btAdapter == null) {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (btAdapter == null) {
            hrClient.onOpenResult(false);
            return;
        }

        hrClient.onOpenResult(true);
    }

    @Override
    public void close() {
        stopScan();
        disconnect();

        if (btGatt != null) {
            btGatt.close();
            btGatt = null;
        }

        if (btAdapter == null) {
            btAdapter = null;
        }

        hrClient = null;
        hrClientHandler = null;
    }

    private BluetoothGattCallback btGattCallbacks = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic arg0) {
            if (gatt != btGatt) {
                return;
            }

            if (!arg0.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
                System.err.println("onCharacteristicChanged(" + arg0
                        + ") != HEART_RATE ??");
                return;
            }

            int length = arg0.getValue().length;
            if (length == 0) {
                System.err.println("length = 0");
                return;
            }

            if (isHeartRateInUINT16(arg0.getValue()[0])) {
                hrValue = arg0.getIntValue(
                        BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            } else {
                hrValue = arg0.getIntValue(
                        BluetoothGattCharacteristic.FORMAT_UINT8, 1);
            }
            hrTimestamp = System.currentTimeMillis();

            if (mIsConnecting) {
                reportConnected(true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic arg0, int status) {
            System.out.println("onCharacteristicRead(): " + gatt + ", char: "
                    + arg0.getUuid() + ", status: " + status);

            if (gatt != btGatt)
                return;

            UUID charUuid = arg0.getUuid();
            if (charUuid.equals(FIRMWARE_REVISON_UUID)) {
                System.out.println(" => startHR()");
                // triggered from DummyReadForSecLevelCheck
                startHR();
                return;
            }
            else if (charUuid.equals(BATTERY_LEVEL_CHARAC)) {
                batteryLevel = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                System.err.println("Battery level: " + batteryLevel);

                System.out.println(" => startHR()");
                // triggered from DummyReadForSecLevelCheck
                startHR();
                return;

            } else {
                System.err.println("Unknown characteristic received: " + charUuid);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                int newState) {

            System.err.println("onConnectionStateChange: " + gatt
                    + ", status: " + status + ", newState: " + newState);
            System.err.println("STATUS_SUCCESS:" + BluetoothGatt.GATT_SUCCESS);
            System.err.println("STATE_CONNECTED: "
                    + BluetoothProfile.STATE_CONNECTED
                    + ", STATE_DISCONNECTED: "
                    + BluetoothProfile.STATE_DISCONNECTED);

            if (gatt != btGatt) {
                System.err.println("gatt(" + gatt + ") != btGatt (" + btGatt
                        + ")");
                return;
            }

            if (mIsConnecting) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    boolean res = btGatt.discoverServices();
                    System.err.println("discoverServices() => " + res);
                    return;
                } else {
                    boolean res = btGatt.connect();
                    System.err
                            .println("disconnect while connecting => btGatt.connect() => "
                                    + res);
                    return;
                }
            }

            if (mIsDisconnecting) {
                System.err.println("mIsDisconnecting => notify");
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
            System.err.println("onConnectionStateChange => WHAT TO DO??");
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
        public void onDescriptorWrite(BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            System.out.println("onServicesDiscoverd(): " + gatt + ", status: "
                    + status);

            if (gatt != btGatt)
                return;

            List<BluetoothGattService> list = btGatt.getServices();
            for (BluetoothGattService s : list) {
                System.err.println("Found service: " + s.getType() + ", "
                        + s.getInstanceId() + ", " + s.getUuid());
                for (BluetoothGattCharacteristic a : s.getCharacteristics()) {
                    System.err.println("  char: " + a.getUuid());
                }
                for (BluetoothGattService a : s.getIncludedServices()) {
                    System.err.println("  serv: " + a.getUuid());
                }

                if (s.getUuid().equals(BATTERY_SERVICE)) {
                    hasBatteryService = true;
                }
            }

            System.out.println(" => DummyRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DummyReadForSecLevelCheck(gatt);
                // continue in onCharacteristicRead
            } else {
                DummyReadForSecLevelCheck(gatt);
                // reportConnectFailed("onServicesDiscovered(" + gatt + ", " +
                // status + ")");
            }
        }

        /*
         * from Samsung HRPService.java
         */
        private void DummyReadForSecLevelCheck(BluetoothGatt btGatt) {
            if (btGatt == null)
                return;

            if (hasBatteryService) {
                readBatteryLevel();
                return;
            }

            BluetoothGattService disService = btGatt.getService(DIS_UUID);
            if (disService == null) {
                reportConnectFailed("Dis service not found");
                return;
            }
            BluetoothGattCharacteristic firmwareIdCharc = disService
                    .getCharacteristic(FIRMWARE_REVISON_UUID);
            if (firmwareIdCharc == null) {
                reportConnectFailed("firmware revison charateristic not found!");
                return;
            }

            if (btGatt.readCharacteristic(firmwareIdCharc) == false) {
                reportConnectFailed("firmware revison reading is failed!");
            }
            // continue in onCharacteristicRead
        }

        private boolean isHeartRateInUINT16(byte b) {
            if ((b & 1) != 0)
                return true;
            return false;
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
            if (btGatt.readDescriptor(mHRMccc) == false) {
                reportConnectFailed("readDescriptor() is failed");
                return;
            }
            // Continue in onDescriptorRead
        }

        private void readBatteryLevel() {
            BluetoothGattService mBS = btGatt.getService(BATTERY_SERVICE);
            if (mBS == null) {
                System.err.println("Battery service not found.");
                return;
            }

            BluetoothGattCharacteristic mBLcharac = mBS
                    .getCharacteristic(BATTERY_LEVEL_CHARAC);
            if (mBLcharac == null) {
                reportConnectFailed("BATTERY LEVEL charateristic not found!");
                return;
            }

            if (!btGatt.readCharacteristic(mBLcharac)) {
                System.err.println("readCharacteristic(" + mBLcharac.getUuid() + ") failed");
            }
            // continue in onCharacteristicRead
        }

    };

    private boolean enableNotification(boolean onoff,
            BluetoothGattCharacteristic charac) {
        if (btGatt == null)
            return false;

        if (!btGatt.setCharacteristicNotification(charac, onoff)) {
            System.err.println("btGatt.setCharacteristicNotification() failed");
            return false;
        }

        BluetoothGattDescriptor clientConfig = charac.getDescriptor(CCC);
        if (clientConfig == null) {
            System.err.println("clientConfig == null");
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

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                byte[] scanRecord) {
            if (hrClient == null)
                return;

            if (hrClientHandler == null)
                return;

            String address = device.getAddress();
            if (mIsConnecting
                    && address.equals(btDevice.getAddress())) {
                stopScan();

                if (CONNECT_IN_OWN_THREAD_FROM_ON_LE_SCAN) {
                    System.err.println("CONNECT_IN_OWN_THREAD_FROM_ON_LE_SCAN");
                    new Thread() {
                        @Override
                        public void run() {
                            btGatt = btDevice.connectGatt(context, false, btGattCallbacks);
                        }
                    };
                } else {
                    btGatt = btDevice.connectGatt(context, false, btGattCallbacks);
                }
                return;
            }

            if (mScanDevices.contains(address))
                return;

            mScanDevices.add(address);

            hrClientHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mIsScanning) { // NOTE: mIsScanning in user-thread
                        hrClient.onScanResult(Bt20Base.createDeviceRef(NAME, device));
                    }
                }
            });
        }

    };

    HashSet<String> mScanDevices = new HashSet<String>();

    @Override
    public void startScan() {
        if (mIsScanning)
            return;

        mIsScanning = true;
        mScanDevices.clear();
        if (AVOID_SCAN_WITH_UUID)
            btAdapter.startLeScan(mLeScanCallback);
        else
            btAdapter.startLeScan(SCAN_UUIDS, mLeScanCallback);
    }

    @Override
    public void stopScan() {
        if (mIsScanning) {
            mIsScanning = false;
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

        if (!Bt20Base.isEnabledImpl()) {
            reportConnectFailed("BT is not enabled");
            return;
        }

        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                ref.deviceAddress);

        if (mIsConnected)
            return;

        if (mIsConnecting)
            return;

        mIsConnecting = true;
        btDevice = dev;
        if (ref.deviceName == null || dev.getName() == null
                || !dev.getName().contentEquals(ref.deviceName)) {
            /**
             * If device doesn't match name, scan for before connecting
             */
            System.err.println("Scan before connect");
            startScan();
            return;

        }
        System.err.println("connectGatt");
        btGatt = btDevice.connectGatt(context, false, btGattCallbacks);
        if (btGatt == null) {
            reportConnectFailed("connectGatt returned null");
        }
    }

    private void reportConnected(final boolean b) {
        if (hrClientHandler != null) {
            hrClientHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mIsConnecting && hrClient != null) {
                        mIsConnected = b;
                        mIsConnecting = false;
                        hrClient.onConnectResult(b);
                    }
                }
            });
        }
    }

    private void reportConnectFailed(String string) {
        System.err.println("reportConnectFailed(" + string + ")");
        if (btGatt != null) {
            btGatt.disconnect();
            btGatt.close();
            btGatt = null;
        }
        btDevice = null;
        reportConnected(false);
    }

    @Override
    public void disconnect() {
        if (btGatt == null)
            return;

        if (btDevice == null) {
            return;
        }

        boolean isConnected = mIsConnected;
        if (mIsConnecting == false && mIsConnected == false)
            return;

        if (mIsDisconnecting == true)
            return;

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

        btGatt.disconnect();

        if (isConnected) {
            System.out.println("close btGatt in onConnectionState");
            // close btGatt in onConnectionState
            synchronized (this) {
                long end = System.currentTimeMillis() + 2000; // wait max 2
                                                              // seconds
                while (btGatt != null && System.currentTimeMillis() < end) {
                    System.out.println("waiting for btGatt to become null");
                    try {
                        this.wait(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                BluetoothGatt copy = btGatt;
                if (copy != null) {
                    System.out
                            .println("close btGatt in disconnect() after waiting 2 secs");
                    copy.close();
                    btGatt = null;
                }
            }
        } else {
            System.out.println("close btGatt here in disconnect()");
            BluetoothGatt copy = btGatt;
            if (copy != null)
                copy.close();
            btGatt = null;
        }

        btDevice = null;
        mIsDisconnecting = false;
    }

    private void reportDisconnectFailed(String string) {
        System.err.println("disconnect failed: " + string);
    }

    private void reportDisconnected() {
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
    public boolean isBondingDevice() {
        return false;
    }

    @Override
    public int getBatteryLevel() {
        return this.batteryLevel;
    }
}
