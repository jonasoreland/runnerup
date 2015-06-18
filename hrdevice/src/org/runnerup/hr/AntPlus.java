/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.IHeartRateDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IPluginAccessResultReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController;
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController.AsyncScanResultDeviceInfo;
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController.IAsyncScanResultReceiver;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;

/**
 * Provides connectivity to ANT+ modules
 *
 * @author jonas
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class AntPlus extends BtHRBase {

    static final String NAME = "AntPlus";
    static final String DISPLAY_NAME = "ANT+";

    final Context context;
    int hrValue;
    long hrTimestamp;

    HRDeviceRef connectRef = null;

    AntPlusHeartRatePcc hrPcc = null;
    AsyncScanController<AntPlusHeartRatePcc> hrScanCtrl = null;

    private boolean mIsScanning = false;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;
    private PccReleaseHandle<AntPlusHeartRatePcc> releaseHandle;

    public static boolean checkLibrary(Context ctx) {
        try {
            Class.forName("com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc");
            Class.forName("com.dsi.ant.plugins.antplus.pcc.defines.DeviceState");
            Class.forName("com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult");
            Class.forName("com.dsi.ant.plugins.antplus.pccbase.AsyncScanController");
            return true;
        } catch (Exception e) {
        }
        return false;
    }

    public AntPlus(Context ctx) {
        this.context = ctx;
    }

    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getProviderName() {
        return NAME;
    }

    @Override
    public void open(Handler handler, HRClient hrClient) {
        log("open()");
        this.hrClientHandler = handler;
        this.hrClient = hrClient;
        hrClient.onOpenResult(true);
    }

    @Override
    public void close() {
        HRClient client = hrClient;
        hrClient = null;
        stopScan();
        disconnect();
        if (client != null) {
            client.onCloseResult(true);
        }
    }

    @Override
    public boolean isBondingDevice() {
        return false;
    }

    @Override
    public boolean isScanning() {
        return mIsScanning;
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
    public void startScan() {
        stopScan();
        log("startScan()");
        mIsScanning = true;
        mScanDevices.clear();
        hrScanCtrl = AntPlusHeartRatePcc.requestAsyncScanController(context, 0, scanReceiver);
    }

    @Override
    public void stopScan() {
        if (mIsScanning || hrScanCtrl != null)
            log("stopScan()");

        mIsScanning = false;
        if (hrScanCtrl != null) {
            hrScanCtrl.closeScanController();
            hrScanCtrl = null;
        }
    }

    final HashSet<String> mScanDevices = new HashSet<String>();
    final IAsyncScanResultReceiver scanReceiver = new IAsyncScanResultReceiver() {

        @Override
        public void onSearchResult(final AsyncScanResultDeviceInfo arg0) {
            log("onSearchResult(" + arg0 + ")");
            if (hrClient == null)
                return;

            if (hrClientHandler == null)
                return;

            final HRDeviceRef ref = HRDeviceRef.create(NAME, arg0.getDeviceDisplayName(),
                    Integer.toString(arg0.getAntDeviceNumber()));

            if ((mIsConnecting || mIsConnected) &&
                    ref.deviceAddress.equals(connectRef.deviceAddress) &&
                    ref.deviceName.equals(connectRef.deviceName)) {

                stopScan();
                releaseHandle = AntPlusHeartRatePcc.requestAccess(context,
                        arg0.getAntDeviceNumber(), 0,
                        resultReceiver, stateReceiver);
                return;
            }

            if (mScanDevices.contains(ref.deviceAddress))
                return;

            mScanDevices.add(ref.deviceAddress);

            hrClientHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mIsScanning) { // NOTE: mIsScanning in user-thread
                        hrClient.onScanResult(HRDeviceRef.create(NAME, arg0.getDeviceDisplayName(),
                                Integer.toString(arg0.getAntDeviceNumber())));
                    }
                }
            });
        }

        @Override
        public void onSearchStopped(RequestAccessResult arg0) {
            log("onSearchStopped(" + arg0 + ")");
        }
    };

    @Override
    public void connect(HRDeviceRef ref) {
        stopScan();
        disconnectImpl();
        log("connect(" + Integer.parseInt(ref.deviceAddress) + ")");
        connectRef = ref;
        mIsConnecting = true;
        releaseHandle = AntPlusHeartRatePcc.requestAccess(context, Integer.parseInt(ref.deviceAddress), 0,
                resultReceiver, stateReceiver);
    }

    AntPlusHeartRatePcc antDevice = null;

    final IPluginAccessResultReceiver<AntPlusHeartRatePcc> resultReceiver = new IPluginAccessResultReceiver<AntPlusHeartRatePcc>() {

        @Override
        public void onResultReceived(AntPlusHeartRatePcc arg0,
                RequestAccessResult arg1, DeviceState arg2) {

            log("onResultReceived(" + arg0 + ", " + arg1 + ", " + arg2 + ")");

            antDevice = arg0;
            switch (arg1) {
                case ALREADY_SUBSCRIBED:
                case CHANNEL_NOT_AVAILABLE:
                case DEPENDENCY_NOT_INSTALLED:
                case DEVICE_ALREADY_IN_USE:
                case OTHER_FAILURE:
                case SEARCH_TIMEOUT:
                case UNRECOGNIZED:
                case USER_CANCELLED:
                    reportConnectFailed(arg1);
                    return;
                case SUCCESS:
                    break;
            }

            switch (arg2) {
                case UNRECOGNIZED:
                case CLOSED:
                case DEAD:
                case PROCESSING_REQUEST:
                case SEARCHING:
                    reportConnectFailed(arg1);
                    return;
                case TRACKING:
                    break;
                default:
                    // ???
                    break;
            }

            antDevice.subscribeHeartRateDataEvent(heartRateDataReceiver);
        }
    };

    final IHeartRateDataReceiver heartRateDataReceiver = new IHeartRateDataReceiver() {

        @Override
        public void onNewHeartRateData(long arg0, EnumSet<EventFlag> arg1,
                int arg2, long arg3, BigDecimal bigDecimal, AntPlusHeartRatePcc.DataState dataState) {

            switch (dataState) {
                case LIVE_DATA:
                    break;
                case INITIAL_VALUE:
                    break;
                case ZERO_DETECTED:
                    break;
                case UNRECOGNIZED:
                    break;
            }

            if (arg2 == 0) {
                log("got hrValue == 0 => aborting");
                if (mIsConnecting)
                    reportConnected(false);
                else if (mIsConnected)
                    reportDisconnected(true);
                return;
            }

            hrValue = arg2;
            hrTimestamp = System.currentTimeMillis();

            if (mIsConnecting) {
                reportConnected(true);
            }
        }
    };

    final IDeviceStateChangeReceiver stateReceiver = new IDeviceStateChangeReceiver() {

        @Override
        public void onDeviceStateChange(DeviceState arg0) {
            log("onDeviceStateChange(" + arg0 + ")");
            switch (arg0) {
                case CLOSED:
                    break;
                case DEAD:
                    if (mIsConnected) {
                        /** don't silent reconnect, let upper lay handle that */
                        reportDisconnected(true);
                        return;
                    }
                    if (mIsConnecting) {
                        reportConnectFailed(null);
                        return;
                    }
                    break;
                case PROCESSING_REQUEST:
                    break;
                case SEARCHING:
                    break;
                case TRACKING:
                    break;
                case UNRECOGNIZED:
                    break;
            }
        }
    };

    private void reportConnectFailed(RequestAccessResult arg1) {
        disconnectImpl();
        if (hrClientHandler != null) {
            hrClientHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (hrClient != null) {
                        hrClient.onConnectResult(false);
                    }
                }
            });
        }
    }

    protected void reportConnected(final boolean b) {
        hrClientHandler.post(new Runnable() {
            @Override
            public void run() {
                if (hrClient == null) {
                    disconnectImpl();
                    return;
                } else if (mIsConnecting) {
                    mIsConnected = b;
                    mIsConnecting = false;
                    hrClient.onConnectResult(b);
                }
            }
        });
    }

    protected void reportDisconnected(final boolean b) {
        disconnectImpl();
        hrClientHandler.post(new Runnable() {
            @Override
            public void run() {
                if (hrClient != null)
                    hrClient.onDisconnectResult(b);
            }
        });
    }

    @Override
    public void disconnect() {
        disconnectImpl();
        if (hrClientHandler != null) {
            hrClientHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (hrClient != null) {
                        hrClient.onDisconnectResult(true);
                    }
                }
            });
        }
    }

    private void disconnectImpl() {
        log("disconnectImpl");
        stopScan();
        if (antDevice != null) {
            antDevice.releaseAccess();
            antDevice = null;
        }
        if (releaseHandle != null) {
            releaseHandle.close();
            releaseHandle = null;
        }
        mIsConnecting = false;
        mIsConnected = false;
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
    public HRData getHRData() {
        if (hrValue <= 0) {
            return null;
        }

        return new HRData().setHeartRate(hrValue).setTimestampEstimate(hrTimestamp);
    }

    @Override
    public int getBatteryLevel() {
        return -1;
    }

    /** it seems ANT+ requires Bluetooth too */

    @Override
    public boolean isEnabled() {
        return Bt20Base.isEnabledImpl();
    }

    @Override
    public boolean startEnableIntent(Activity activity, int requestCode) {
        return Bt20Base.startEnableIntentImpl(activity, requestCode);
    }
}
