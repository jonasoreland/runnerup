
package org.runnerup.hr;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MockHRProvider implements HRProvider {

    private HRClient hrClient = null;
    private Handler hrClientHandler = null;
    public static final String NAME = "MockHR";

    public MockHRProvider(Context ctx) {
    }

    @Override
    public String getName() {
        return "MockHR";
    }

    @Override
    public String getProviderName() {
        return "MockHR";
    }

    @Override
    public void open(Handler handler, HRClient hrClient) {
        this.hrClient = hrClient;
        this.hrClientHandler = handler;
        hrClient.onOpenResult(true);
    }

    @Override
    public void close() {
    }

    boolean mIsScanning = false;

    @Override
    public boolean isScanning() {
        return mIsScanning;
    }

    final Runnable fakeScanResult = new Runnable() {
        int count = 0;

        @Override
        public void run() {
            if (mIsScanning &&  BluetoothAdapter.getDefaultAdapter() != null) {
                String dev = "00:43:A8:23:10:"
                        + String.format("%02X", System.currentTimeMillis() % 256);
                hrClient.onScanResult(Bt20Base.createDeviceRef(NAME, BluetoothAdapter
                        .getDefaultAdapter().getRemoteDevice(dev)));
                if (++count < 3) {
                    hrClientHandler.postDelayed(fakeScanResult, 3000);
                    return;
                }
            }
            count = 0;
        }
    };

    @Override
    public void startScan() {
        mIsScanning = true;
        hrClientHandler.postDelayed(fakeScanResult, 3000);
    }

    @Override
    public void stopScan() {
        mIsScanning = false;
    }

    boolean mIsConnecting = false;
    boolean mIsConnected = false;

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
        if (mIsConnected)
            return;

        if (mIsConnecting)
            return;

        mIsConnecting = true;
        hrClientHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mIsConnecting) {
                    mIsConnected = true;
                    mIsConnecting = false;
                    hrClient.onConnectResult(true);
                    hrClientHandler.postDelayed(hrUpdate, 750);
                }
            }
        }, 3000);
    }

    final Runnable hrUpdate = new Runnable() {
        @Override
        public void run() {
            hrValue = (int) (150 + 40 * Math.random());
            hrTimestamp = System.currentTimeMillis();
            if (mIsConnected == true) {
                hrClientHandler.postDelayed(hrUpdate, 750);
            }
        }
    };

    @Override
    public void disconnect() {
        mIsConnecting = false;
        mIsConnected = false;
    }

    int hrValue = 0;
    long hrTimestamp = 0;

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
        return (int) (100 * Math.random());
    }

    @Override
    public boolean isBondingDevice() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean startEnableIntent(Activity activity, int requestCode) {
        return false;
    }
}
