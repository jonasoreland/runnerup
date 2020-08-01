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

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;

import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.IHeartRateDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
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

public class AntPlus extends BtHRBase {

    private static final String NAME = "AntPlus";
    private static final String DISPLAY_NAME = "ANT+";

    private final Context context;
    private int hrValue;
    private long hrTimestamp;
    private long hrElapsedRealtime = 0;

    private HRDeviceRef connectRef = null;

    private AsyncScanController<AntPlusHeartRatePcc> hrScanCtrl = null;

    private boolean mIsScanning = false;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;
    private PccReleaseHandle<AntPlusHeartRatePcc> releaseHandle;

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

    public static boolean checkLibrary(Context ctx) {
        try {
            // A few required libs
            Class.forName("com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc");
            Class.forName("com.dsi.ant.plugins.antplus.pcc.defines.DeviceState");
            Class.forName("com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult");
            Class.forName("com.dsi.ant.plugins.antplus.pccbase.AsyncScanController");

            // The system libraries must be installed
            return AntPluginPcc.getInstalledPluginsVersionNumber(ctx) >= 0;
        } catch(Exception e){}
        return false;
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

    private final HashSet<String> mScanDevices = new HashSet<>();
    private final IAsyncScanResultReceiver scanReceiver = new IAsyncScanResultReceiver() {

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

            hrClientHandler.post(() -> {
                if (mIsScanning) { // NOTE: mIsScanning in user-thread
                    hrClient.onScanResult(HRDeviceRef.create(NAME, arg0.getDeviceDisplayName(),
                            Integer.toString(arg0.getAntDeviceNumber())));
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

    private AntPlusHeartRatePcc antDevice = null;

    private final IPluginAccessResultReceiver<AntPlusHeartRatePcc> resultReceiver = new IPluginAccessResultReceiver<AntPlusHeartRatePcc>() {

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

    private final IHeartRateDataReceiver heartRateDataReceiver = new IHeartRateDataReceiver() {

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
            } else {
                final int NANO_IN_MILLI = 1000000;
                hrElapsedRealtime = SystemClock.elapsedRealtime() * NANO_IN_MILLI;
            }

            if (mIsConnecting) {
                reportConnected(true);
            }
        }
    };

    private final IDeviceStateChangeReceiver stateReceiver = arg0 -> {
        log("onDeviceStateChange(" + arg0 + ")");
        switch (arg0) {
            case CLOSED:
                break;
            case DEAD:
                if (mIsConnected) {
                    /* don't silent reconnect, let upper lay handle that */
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
    };

    private void reportConnectFailed(RequestAccessResult arg1) {
        disconnectImpl();
        if (hrClientHandler != null) {
            hrClientHandler.post(() -> {
                if (hrClient != null) {
                    hrClient.onConnectResult(false);
                }
            });
        }
    }

    private void reportConnected(final boolean b) {
        hrClientHandler.post(() -> {
            if (hrClient == null) {
                disconnectImpl();
            } else if (mIsConnecting) {
                mIsConnected = b;
                mIsConnecting = false;
                hrClient.onConnectResult(b);
            }
        });
    }

    private void reportDisconnected(final boolean b) {
        disconnectImpl();
        hrClientHandler.post(() -> {
            if (hrClient != null)
                hrClient.onDisconnectResult(b);
        });
    }

    @Override
    public void disconnect() {
        disconnectImpl();
        if (hrClientHandler != null) {
            hrClientHandler.post(() -> {
                if (hrClient != null) {
                    hrClient.onDisconnectResult(true);
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
        return -1;
    }

    // ANT+ requires Bluetooth too, as well as that system libs are loaded

    @Override
    public boolean isEnabled() {
        return Bt20Base.isEnabledImpl();
    }

    @Override
    public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
        return Bt20Base.startEnableIntentImpl(activity, requestCode);
    }
}
