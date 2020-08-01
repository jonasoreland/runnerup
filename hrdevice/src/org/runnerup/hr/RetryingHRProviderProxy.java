package org.runnerup.hr;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

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
    }

    private int attempt = 0;
    private final HRProvider provider;
    private HRClient client = null;
    private Handler handler = null;
    private State state = State.CLOSED;
    private State requestedState = State.CLOSED;

    private int getMaxRetries() {
        switch(state) {
            case OPENING:
            case OPENED:
            case SCANNING:
            case CONNECTED:
            case DISCONNECTING:
            case CLOSING:
            case CLOSED:
            case ERROR:
                return 0;
            case CONNECTING:
                return 3; //kMaxConnectRetries
            case RECONNECTING:
                return 10; //kMaxReconnectRetries
        }
        return 0;
    }

    private boolean checkMaxAttempts() {
        attempt++;
        return attempt <= getMaxRetries();
    }

    private int getRetryDelayMillis() {
        switch(state) {
            case OPENING:
            case OPENED:
            case SCANNING:
            case CONNECTED:
            case DISCONNECTING:
            case CLOSING:
            case CLOSED:
            case ERROR:
                return 0;
            case CONNECTING:
                return 750 * (attempt - 1);
            case RECONNECTING:
                return 3000 * (Math.min(attempt, 6));
        }
        return 0;
    }

    private void resetAttempts() {
        attempt = 0;
    }

    public RetryingHRProviderProxy(HRProvider src) {
        this.provider = src;
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
    public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
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

        if (ok) {
            state = State.OPENED;
        } else {
            state = State.CLOSED;
        }
        client.onOpenResult(ok);
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
        } else {
            if (!checkMaxAttempts()) {
                state = State.OPENED;
                requestedState = State.OPENED;
                log("client.onConnectResult(false)");
                client.onConnectResult(false);
                return;
            }

            int delayMillis = getRetryDelayMillis();
            log("retry in " + delayMillis + "ms");
            handler.postDelayed(() -> {
                log("retry connect");
                provider.connect(connectRef);
            }, delayMillis);

        }
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
    public long getHRValueElapsedRealtime() {
        return provider.getHRValueElapsedRealtime();
    }

    @Override
    public HRData getHRData() {
        return provider.getHRData();
    }

    @Override
    public int getBatteryLevel() {
        return provider.getBatteryLevel();
    }

    /*** HRClient interface */

    @Override
    public void onDisconnectResult(boolean disconnectOK) {
        log("onDisonncetResult("+disconnectOK+")");
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
        if (client != null)
            client.onDisconnectResult(disconnectOK);
    }

    @Override
    public void onCloseResult(boolean closeOK) {
        state = State.CLOSED;
        requestedState = State.CLOSED;
        if (client != null)
            client.onConnectResult(closeOK);
    }

    @Override
    public void log(HRProvider src, String msg) {
        log(msg);
    }

    private void log(final String msg) {

        String res = "[ RetryingHRProviderProxy: " +
                provider.getProviderName() +
                ", attempt: " + attempt +
                " ]" +
                ", state: " + state + ", request: " + requestedState + ", " + msg;
        System.err.println(res);
        if (client != null) {
            if(Looper.myLooper() == Looper.getMainLooper()) {
                client.log(this, msg);
            } else {
                handler.post(() -> {
                    if (client != null)
                        client.log(RetryingHRProviderProxy.this, msg);
                });
            }
        }
    }
}
