package org.runnerup.hr;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Looper;

import org.runnerup.util.Constants;

/**
 * Created by jonas on 11/9/14.
 *
 * The following class handles transparent retries for flaky bluetooth
 * The following features are implemented:
 * - connect()
 *   if connect fails, it's retried N times
 *   if connect takes longer than X it's retried
 *
 * - connected
 *   if loosing connection when connected,
 *   it will auto connect
 */
public class RetryingHRProviderProxy implements HRProvider, HRProvider.HRClient {

    private HRDeviceRef connectRef;

    enum State {
        OPENING,
        OPENED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        CLOSING,
        CLOSED,
        ERROR,
        RECONNECTING
    };

    private int attempt = 0;
    private int maxRetries = 3;
    private HRProvider provider;
    private HRClient client = null;
    private Handler handler = null;
    private State state = State.CLOSED;
    private State requestedState = State.CLOSED;

    private final int kConnectTimeout = 2000;
    private final int kResetBtTimeoutMillis = 3000;

    private boolean checkMaxAttempts() {
        attempt++;
        if (attempt > maxRetries)
            return false;
        return true;
    }

    private void resetAttempts() {
        attempt = 0;
    }

    public RetryingHRProviderProxy(HRProvider src, int retries) {
        this.provider = src;
        this.maxRetries = retries;
    }

    @Override
    public String getName() {
        return provider.getName();
    }

    @Override
    public String getProviderName() { return provider.getProviderName(); }

    @Override
    public boolean isEnabled() {
        return provider.isEnabled();
    }

    @Override
    public boolean startEnableIntent(Activity activity, int requestCode) {
        return provider.startEnableIntent(activity, requestCode);
    }

    @Override
    public void open(Handler handler, HRClient hrClient) {
        this.client = hrClient;
        this.handler = handler;
        this.requestedState = State.OPENED;
        state = State.OPENING;
        provider.open(handler, this);
    }

    @Override
    public void onOpenResult(boolean ok) {
        log("onOpenResult(" + ok + ")");

        if (requestedState != State.OPENED) {
            /* ignore onOpenResult in weird state */
            return;
        }

        if (ok == true) {
            state = State.OPENED;
            client.onOpenResult(ok);
        } else {
            state = State.CLOSED;
            client.onOpenResult(ok);
        }
    }

    @Override
    public void close() {
        state = State.CLOSED;
        requestedState = State.CLOSED;
        if (provider != null) {
            provider.stopScan();
            provider.disconnect();
            provider.close();
        }
        client = null;
    }

    @Override
    public boolean isBondingDevice() {
        return provider.isBondingDevice();
    }

    @Override
    public boolean isScanning() { return provider.isScanning(); }

    @Override
    public boolean isConnected() { return provider.isConnected(); }

    @Override
    public boolean isConnecting() {
        return (requestedState == State.CONNECTING);
    }

    @Override
    public void startScan() {
        this.state = State.SCANNING;
        this.requestedState = State.SCANNING;
        provider.startScan();
    }

    @Override
    public void onScanResult(HRDeviceRef device) {
        client.onScanResult(device);
    }

    @Override
    public void stopScan() {
        this.state = State.OPENED;
        this.requestedState = State.OPENED;
        provider.stopScan();
    }

    @Override
    public void connect(HRDeviceRef ref) {
        log("connect("+ref+")");
        resetAttempts();
        this.state = State.CONNECTING;
        this.requestedState = State.CONNECTED;
        this.connectRef = ref;
        provider.connect(ref);
    }

    @Override
    public void onConnectResult(boolean connectOK) {
        log("onConnectResult("+connectOK+")");
        switch (requestedState) {
            case OPENING:
            case OPENED:
            case SCANNING:
            case CONNECTING:
            case CLOSING:
            case CLOSED:
            case ERROR:
                /* weird => ignore */
                return;
            case CONNECTED:
                break;
            case DISCONNECTING:
                /* ignore */
                return;
        }

        if (connectOK) {
            boolean reconnect = state == State.RECONNECTING;
            state = State.CONNECTED;
            requestedState = State.CONNECTED;
            if (!reconnect) {
                log("client.onConnectResult(true)");
                client.onConnectResult(true);
            }
            return;
        } else {
            if (!checkMaxAttempts()) {
                state = State.OPENED;
                requestedState = State.OPENED;
                log("client.onConnectResult(false)");
                client.onConnectResult(false);
                return;
            }
            if (attempt == 1 || attempt == 3) {
                log("retry connect");
                provider.connect(connectRef);
                return;
            }

            // before attempt 2, we turnOnOff BT
            log("resetBt");
            turnOnOffBt(new Runnable() {
                @Override
                public void run() {
                    if ((state == State.CONNECTING || state == State.RECONNECTING) &&
                            requestedState == State.CONNECTED)
                        log("retry connect after resetBt");
                        provider.connect(connectRef);
                }
            });
            return;
        }
    }

    void turnOnOffBt(final Runnable runnable) {
        turnOffBt(new Runnable() {
            @Override
            public void run() {
                turnOnBt(runnable);
            }
        });
    }

    void turnOffBt(final Runnable runnable) {
        final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt.isEnabled() == false) {
            runnable.run();
            return;
        }
        bt.disable();
        waitBtState(false, runnable, kResetBtTimeoutMillis /2);
    }



    void turnOnBt(final Runnable runnable) {
        final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt.isEnabled() == true) {
            runnable.run();
            return;
        }
        bt.enable();
        waitBtState(true, runnable, kResetBtTimeoutMillis /2);
    }

    void waitBtState(final boolean state, final Runnable runnable, final int maxMillis) {
        final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        handler.postDelayed(new Runnable() {
            int count = maxMillis / 500;

            @Override
            public void run() {
                if (bt.isEnabled() == state || count == 0) {
                    runnable.run();
                    return;
                }
                count--;
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    @Override
    public void disconnect() {
        resetAttempts();
        this.state = State.DISCONNECTING;
        this.requestedState = State.OPENED;
        provider.disconnect();
    }

    @Override
    public int getHRValue() {
        return provider.getHRValue();
    }

    @Override
    public long getHRValueTimestamp() {
        return provider.getHRValueTimestamp();
    }

    @Override
    public int getBatteryLevel() {
        return provider.getBatteryLevel();
    }

    /*** HRClient interface */

    @Override
    public void onDisconnectResult(boolean disconnectOK) {
        if (disconnectOK && state == State.CONNECTED && requestedState == State.CONNECTED) {
            /* this is unwanted disconnect, silently disconnect/connect */
            state = State.DISCONNECTING;
            provider.disconnect();
            return;
        }

        if (state == State.DISCONNECTING && requestedState == State.CONNECTED) {
            /* this is disconnected after unwanted disconnect, silently connect */
            state = State.RECONNECTING;
            provider.connect(connectRef);
            return;
        }

        state = State.OPENED;
        requestedState = State.OPENED;
        client.onDisconnectResult(disconnectOK);
    }

    @Override
    public void onCloseResult(boolean closeOK) {
        state = State.CLOSED;
        requestedState = State.CLOSED;
        client.onConnectResult(closeOK);
    }

    @Override
    public void log(HRProvider src, String msg) {
        log(msg);
    }

    public void log(final String msg) {
        StringBuilder str = new StringBuilder();
        str.append("[ RetryingHRProviderProxy: ").
                append(provider.getProviderName()).
                append(", attempt: ").append(Integer.toString(attempt)).
                append(", retries: ").append(Integer.toString(maxRetries)).
                append(" ]");

        str.append(", state: " + state + ", request: " + requestedState +
                ", " + msg);
        String res = str.toString();
        System.err.println(res);
        if (client != null) {
            if(Looper.myLooper() == Looper.getMainLooper()) {
                client.log(this, msg);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (client != null)
                            client.log(RetryingHRProviderProxy.this, msg);
                    }
                });
            }
        }
    }
}
