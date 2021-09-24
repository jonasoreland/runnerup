
package org.runnerup.hr;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;


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

    private boolean mIsScanning = false;

    @Override
    public boolean isScanning() {
        return mIsScanning;
    }

    private final Runnable fakeScanResult = new Runnable() {
        int count = 0;

        @Override
        public void run() {
            if (mIsScanning) {
                String dev = "00:43:A8:23:10:"
                        + String.format("%02X", System.currentTimeMillis() % 256);
                hrClient.onScanResult(HRDeviceRef.create(NAME, getName(), dev));
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

    private boolean mIsConnecting = false;
    private boolean mIsConnected = false;

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
        hrClientHandler.postDelayed(() -> {
            if (mIsConnecting) {
                mIsConnected = true;
                mIsConnecting = false;
                hrClient.onConnectResult(true);
                hrClientHandler.postDelayed(hrUpdate, 750);
            }
        }, 3000);
    }

    private final Runnable hrUpdate = new Runnable() {
        @Override
        public void run() {
            hrValue = (int) (120 + SystemClock.elapsedRealtime() / 1000.0 % 40 + 3 * Math.random());
            hrTimestamp = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                hrElapsedRealtime = SystemClock.elapsedRealtimeNanos();
            } else {
                final int NANO_IN_MILLI = 1000000;
                hrElapsedRealtime = SystemClock.elapsedRealtime() * NANO_IN_MILLI;
            }
            if (mIsConnected) {
                hrClientHandler.postDelayed(hrUpdate, 750);
            }
        }
    };

    @Override
    public void disconnect() {
        mIsConnecting = false;
        mIsConnected = false;
    }

    private int hrValue = 0;
    private long hrTimestamp = 0;
    private long hrElapsedRealtime = 0;

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
    public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
        return false;
    }
}
