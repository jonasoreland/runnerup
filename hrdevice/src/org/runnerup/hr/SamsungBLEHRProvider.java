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

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.samsung.android.sdk.bt.gatt.BluetoothGatt;
import com.samsung.android.sdk.bt.gatt.BluetoothGattAdapter;
import com.samsung.android.sdk.bt.gatt.BluetoothGattCallback;
import com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic;
import com.samsung.android.sdk.bt.gatt.BluetoothGattDescriptor;
import com.samsung.android.sdk.bt.gatt.BluetoothGattService;

import java.util.List;
import java.util.UUID;

/**
 * A HRProvider for BLE on Android versions 4.2, 4.2.1 and 4.2.2. For later versions, see
 * {@link AndroidBLEHRProvider}
 *
 * The Samsung BLE SDK for Bluetooth Low Energy connectivity. On later versions, Android supports BLE natively
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SamsungBLEHRProvider extends BtHRBase implements HRProvider {

    static final String NAME = "SamsungBLE";
    static final String DISPLAY_NAME = "Bluetooth SMART (BLE)";

    private final Context context;
    private BluetoothAdapter btAdapter = null;
    private BluetoothGatt btGatt = null;
    private BluetoothDevice btDevice = null;
    private int hrValue = 0;
    private long hrTimestamp = 0;
    private boolean hasBattery = false;
    private int batteryLevel = -1;

    public SamsungBLEHRProvider(Context ctx) {
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

    private boolean mIsScanning = false;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;

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

        BluetoothGattAdapter.getProfileProxy(context, profileServiceListener,
                BluetoothGattAdapter.GATT);
    }

    @Override
    public void close() {
        do {
            if (btAdapter == null)
                break;

            if (btGatt == null)
                break;

            stopScan();
            disconnect();

            BluetoothGattAdapter.closeProfileProxy(BluetoothGattAdapter.GATT, btGatt);
            btAdapter = null;
        } while (false);

        hrClient = null;
        hrClientHandler = null;
    }

    private final ServiceListener profileServiceListener = new ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothGattAdapter.GATT) {
                btGatt = (BluetoothGatt) proxy;

                if (hrClient == null) {
                    log("hrClient == null => skip register in onServiceConnected => closeProfileProxy");
                    BluetoothGattAdapter.closeProfileProxy(BluetoothGattAdapter.GATT, btGatt);
                    btGatt = null;
                    return;
                }

                btGatt.registerApp(btGattCallbacks);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothGattAdapter.GATT) {
                if (btGatt != null) {
                    btGatt.unregisterApp();
                }
                btGatt = null;
            }
        }
    };

    private final BluetoothGattCallback btGattCallbacks = new BluetoothGattCallback() {

        @Override
        public void onAppRegistered(final int arg0) {
            if (hrClientHandler == null) {
                /*
                 * let's hope that unregister has been called...and that it
                 * works to call it before this callback is called
                 */
                log("onAppRegistered: hrClientHandler == null => return");
                return;
            }
            hrClientHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (hrClient == null) {
                        log("onAppRegistered: hrClient == null => return");
                        return;
                    }
                    if (arg0 == BluetoothGatt.GATT_SUCCESS) {
                        hrClient.onOpenResult(true);
                    } else {
                        hrClient.onOpenResult(false);
                    }
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGattCharacteristic arg0) {
            if (!arg0.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
                log("onCharacteristicChanged(" + arg0 + ") != HEART_RATE ??");
                return;
            }

            int length = arg0.getValue().length;
            if (length == 0) {
                log("length = 0");
                return;
            }

            if (isHeartRateInUINT16(arg0.getValue()[0])) {
                hrValue = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            } else {
                hrValue = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
            }

            if (hrValue == 0) {
                if (mIsConnecting) {
                    reportConnectFailed("got hrValue = 0 => reportConnectFailed");
                    return;
                }
                log("got hrValue == 0 => disconnecting");
                reportDisconnected();
                return;
            }

            hrTimestamp = System.currentTimeMillis();

            if (mIsConnecting) {
                reportConnected(true);
            }
        }

        private boolean isHeartRateInUINT16(byte b) {
            if ((b & 1) != 0)
                return true;
            return false;
        }

        @Override
        public void onCharacteristicRead(BluetoothGattCharacteristic arg0, int arg1) {
            // triggered from DummyReadForSecLevelCheck
            UUID charUuid = arg0.getUuid();
            if (charUuid.equals(FIRMWARE_REVISON_UUID)) {
            } else if (charUuid.equals(BATTERY_LEVEL_CHARAC)) {
                batteryLevel = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                log("Battery level: " + batteryLevel);
            }

            log(" => startHR()");
            // triggered from DummyReadForSecLevelCheck
            startHR();
            return;
        }

        private void startHR() {
            BluetoothGattService mHRP = btGatt.getService(btDevice, HRP_SERVICE);
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

        @Override
        public void onCharacteristicWrite(BluetoothGattCharacteristic arg0, int arg1) {
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice arg0, int status, int newState) {

            if (btDevice != null && arg0 != null
                    && btDevice.getAddress().contentEquals(arg0.getAddress())) {
            }

            if (btGatt == null) {
                log("onConnectionStateChange: btGatt == null");
                return;
            }
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                btGatt.discoverServices(arg0);
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                reportDisconnected();
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGattDescriptor arg0, int arg1) {
            BluetoothGattCharacteristic mHRMcharac = arg0.getCharacteristic();
            if (!enableNotification(true, mHRMcharac)) {
                reportConnectFailed("Failed to enable notification in onDescriptorRead");
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGattDescriptor arg0, int arg1) {
        }

        @Override
        public void onReadRemoteRssi(BluetoothDevice arg0, int arg1, int arg2) {
        }

        @Override
        public void onReliableWriteCompleted(BluetoothDevice arg0, int arg1) {
        }

        @Override
        public void onScanResult(final BluetoothDevice arg0, int arg1, byte[] scanRecord) {
            final boolean broadcast = checkIfBroadcastMode(scanRecord);
            log("onScanResult(" + arg0.getName() + "), hrClient:" + hrClient
                    + ", broadcast: " + broadcast +
                    ", mIsConnecting: " + mIsConnecting + ", mIsScanning: " + mIsScanning);

            if (!broadcast) {
                /**
                 * If connect was called and btDevice was unknown, we scan for
                 * it before btGatt.connect()
                 */
                if (mIsConnecting && arg0.getAddress().equals(btDevice.getAddress())) {
                    btGatt.stopScan();
                    btGatt.connect(btDevice, false);
                    return;
                }
                hrClientHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsScanning && hrClient != null) { // NOTE:
                                                               // mIsScanning in
                                                               // user-thread
                            hrClient.onScanResult(Bt20Base.createDeviceRef(NAME, arg0));
                        }
                    }
                });
            } else {
                log("checkIfBroadcastMode(" + arg0 + ") => FAIL");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothDevice device, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List list = btGatt.getServices(device);
                for (Object _s : list) {
                    BluetoothGattService s = (BluetoothGattService) _s;
                    if (BtHRBase.BATTERY_SERVICE.equals(s.getUuid())) {
                        hasBattery = true;
                        break;
                    }
                }
                DummyReadForSecLevelCheck(device);
                // continue in onCharacteristicRead
            } else {
                reportConnectFailed("onServicesDiscovered(" + device + ", " + status + ")");
            }
        }

        /*
         * from Samsung HRPService.java
         */
        private boolean checkIfBroadcastMode(byte[] scanRecord) {
            final int ADV_DATA_FLAG = 0x01;
            final int LIMITED_AND_GENERAL_DISC_MASK = 0x03;

            int offset = 0;
            while (offset < (scanRecord.length - 2)) {
                int len = scanRecord[offset++];
                if (len == 0)
                    break; // Length == 0 , we ignore rest of the packet
                // TOD Check the rest of the packet if get len = 0

                int type = scanRecord[offset++];
                switch (type) {
                    case ADV_DATA_FLAG:

                        if (len >= 2) {
                            // The usual scenario(2) and More that 2 octets
                            // scenario.
                            // Since this data will be in Little endian format,
                            // we
                            // are interested in first 2 bits of first byte
                            byte flag = scanRecord[offset++];
                            /*
                             * 00000011(0x03) - LE Limited Discoverable Mode and
                             * LE General Discoverable Mode
                             */
                            if ((flag & LIMITED_AND_GENERAL_DISC_MASK) > 0)
                                return false;
                            else
                                return true;
                        } else if (len == 1) {
                            continue;// ignore that packet and continue with the
                                     // rest
                        }
                    default:
                        offset += (len - 1);
                        break;
                }
            }
            return false;
        }

        /*
         * from Samsung HRPService.java
         */
        private void DummyReadForSecLevelCheck(BluetoothDevice device) {
            if (btGatt == null) {
                log("DummyReadForSecLevelCheck: btGatt == null");
                return;
            }

            if (device == null) {
                log("DummyReadForSecLevelCheck: device == null");
                return;
            }

            if (hasBattery && readBatteryLevel(device)) {
                return;
            }

            BluetoothGattService disService = btGatt.getService(device, DIS_UUID);
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
    };

    private boolean readBatteryLevel(BluetoothDevice device) {
        BluetoothGattService service = btGatt.getService(device, BATTERY_SERVICE);
        if (service == null) {
            log("Battery service not found.");
            return false;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL_CHARAC);
        if (characteristic == null) {
            log("Battery characteristic not found.");
            return false;
        }

        if (!btGatt.readCharacteristic(characteristic)) {
            log("readCharacteristic(" + characteristic.getUuid() + ") failed");
            return false;
        }
        return true;
        // continue in onCharacteristicRead
    }

    private boolean enableNotification(boolean onoff, BluetoothGattCharacteristic charac) {
        if (btGatt == null) {
            log("enableNotfication("+onoff+ ", " + charac + "): btGatt == null");
            return false;
        }

        if (!btGatt.setCharacteristicNotification(charac, onoff)) {
            log("enableNotfication("+onoff+ ", " + charac + "): setCharacteristicNotification => false");
            return false;
        }

        BluetoothGattDescriptor clientConfig = charac.getDescriptor(CCC);
        if (clientConfig == null) {
            log("enableNotfication("+onoff+ ", " + charac + "): clientConfig == null");
            return false;
        }

        if (onoff) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return btGatt.writeDescriptor(clientConfig);
    }

    @Override
    public boolean isScanning() {
        return mIsScanning;
    }

    @Override
    public void startScan() {
        if (btGatt == null)
            return;

        if (mIsScanning)
            return;

        mIsScanning = true;
        btGatt.startScan();
    }

    @Override
    public void stopScan() {
        if (mIsScanning) {
            mIsScanning = false;
            btGatt.stopScan();
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

        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter()
                .getRemoteDevice(ref.getAddress());

        if (mIsConnected)
            return;

        if (mIsConnecting)
            return;

        mIsConnecting = true;
        btDevice = dev;
        if (ref.deviceName == null || dev.getName() == null ||
                !ref.deviceName.contentEquals(dev.getName())) {
            /**
             * If device doesn't match name, scan for before connecting
             */
            log("Scan before connect");
            startScan();
            return;
        }
        btGatt.connect(btDevice, false);
    }

    private void reportConnected(final boolean b) {
        hrClientHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsConnecting) {
                    mIsConnected = b;
                    mIsConnecting = false;
                    hrClient.onConnectResult(b);
                }
            }
        });
    }

    private void reportConnectFailed(String string) {
        log("reportConnectFailed(" + string + ")");
        if (btGatt != null && btDevice != null) {
            btGatt.cancelConnection(btDevice);
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

        if (mIsConnecting == false && mIsConnected == false)
            return;

        mIsConnected = false;
        mIsConnecting = false;

        do {
            BluetoothGattService mHRP = btGatt.getService(btDevice, HRP_SERVICE);
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

        btGatt.cancelConnection(btDevice);
        btDevice = null;
    }

    private void reportDisconnectFailed(String string) {
        log("disconnect failed: " + string);
        reportDisconnected(false);
    }

    private void reportDisconnected() {
        reportDisconnected(true);
    }

    private void reportDisconnected(final boolean ok) {
        if (hrClient != null) {
            if(Looper.myLooper() == Looper.getMainLooper()) {
                hrClient.onDisconnectResult(ok);
            } else {
                hrClientHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (hrClient != null)
                            hrClient.onDisconnectResult(ok);
                    }
                });
            }
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

    @Override
    public boolean isBondingDevice() {
        return true;
    }
}
