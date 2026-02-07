package org.runnerup.hr;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by jonas on 11/9/14.
 *
 * <p>The following class handles transparent retries for flaky bluetooth The following features are
 * implemented: - connect() if connect fails, it's retried N times if connect takes longer than X
 * it's retried
 *
 * <p>- connected if loosing connection when connected, it will auto connect
 */
public class RetryingHRProviderProxy implements HRProvider, HRProvider.HRClient {

  private HRDeviceRef connectRef;

  // What has been request by us.
  enum State {
    INITIAL,
    OPEN,
    CLOSE,
    START_SCAN,
    STOP_SCAN,
    CONNECT,
    DISCONNECT,
  };

  private final HRProvider provider;
  private int attempt = 0;
  private HRClient client = null;
  private Handler handler = null;
  private DefaultProxy proxy = new DefaultProxy();
  private State requested = State.INITIAL; // What has been last requested.

  public RetryingHRProviderProxy(HRProvider src) {
    this.provider = src;
  }

  @Override
  public String getName() {
    return provider.getName();
  }

  @Override
  public String getProviderName() {
    return provider.getProviderName();
  }

  @Override
  public String getLogName() {
    return "RetryingHRProviderProxy";
  }

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
    log("open");
    this.client = hrClient;
    this.handler = handler;
    this.requested = State.OPEN;
    registerDefault();
    provider.open(handler, this);
  }

  @Override
  public void onOpenResult(boolean ok) {
    getProxy().onOpenResult(ok);
  }

  @Override
  public void close(String from) {
    log("close, from: " + from);
    this.requested = State.CLOSE;
    var save = this.client;
    this.client = null;
    if (provider != null) {
      registerDefault();
      provider.stopScan();
      provider.disconnect();
      provider.close(from);
    }
    if (save != null) {
      save.onCloseResult(true);
    }
  }

  @Override
  public void onCloseResult(boolean closeOK) {
    getProxy().onCloseResult(closeOK);
  }

  @Override
  public boolean includePairingBLE() {
    return provider.includePairingBLE();
  }

  @Override
  public boolean isScanning() {
    return provider.isScanning();
  }

  @Override
  public boolean isConnected() {
    return provider.isConnected();
  }

  @Override
  public boolean isConnecting() {
    return requested == State.CONNECT;
  }

  @Override
  public void startScan() {
    log("startScan");
    this.requested = State.START_SCAN;
    registerDefault();
    provider.startScan();
  }

  @Override
  public void onScanResult(HRDeviceRef device) {
    getProxy().onScanResult(device);
  }

  @Override
  public void stopScan() {
    log("stopScan");
    this.requested = State.STOP_SCAN;
    registerDefault();
    provider.stopScan();
  }

  @Override
  public void connect(HRDeviceRef ref) {
    log("connect(" + ref + ")");
    this.attempt = 0;
    this.requested = State.CONNECT;
    this.connectRef = ref;
    log("Update proxy from: " + proxy + ", to: ConnectProxy");
    setProxy(
        new DefaultProxy() {
          int getDelay() {
            int delay = (1 << attempt) * 1000;
            if (delay > 10000) {
              delay = 10000;
            }
            return delay;
          }

          boolean check(String from) {
            var proxy = RetryingHRProviderProxy.this.getProxy();
            if (proxy == this && RetryingHRProviderProxy.this.requested == State.CONNECT) {
              RetryingHRProviderProxy.this.log("check(" + from + ") => TRUE");
              return true;
            }
            RetryingHRProviderProxy.this.log(
                "check("
                    + from
                    + "), proxy: "
                    + (proxy == this)
                    + " state: "
                    + RetryingHRProviderProxy.this.requested
                    + " => FALSE");
            return false;
          }

          @Override
          public void onConnectResult(boolean ok) {
            String from = "onConnectResult(" + ok + ")";
            if (!check(from)) {
              return;
            }
            if (ok) {
              RetryingHRProviderProxy.this.log(from + " => super.onConnectResult(true)");
              super.onConnectResult(ok);
              return;
            }
            handler.postDelayed(
                () -> {
                  if (!check("delayed:" + from)) {
                    return;
                  }
                  RetryingHRProviderProxy.this.log(from + " => provider.disconnect()");
                  provider.disconnect();
                },
                getDelay());
          }

          @Override
          public void onDisconnectResult(boolean ok) {
            String from = "onDisconnectResult(" + ok + ")";
            if (!check(from)) {
              return;
            }
            handler.postDelayed(
                () -> {
                  if (!check("delayed:" + from)) {
                    return;
                  }
                  RetryingHRProviderProxy.this.log(from + " => provider.close()");
                  provider.close("proxy:onDisconnectResult(" + ok + ")");
                },
                getDelay());
          }

          @Override
          public void onCloseResult(boolean ok) {
            String from = "onCloseResult(" + ok + ")";
            if (!check(from)) {
              return;
            }
            attempt++;
            if (attempt >= 10) {
              RetryingHRProviderProxy.this.log(from + ", attempt: " + attempt);
              return;
            }
            handler.postDelayed(
                () -> {
                  if (!check("delayed:" + from)) {
                    return;
                  }
                  RetryingHRProviderProxy.this.log(from + " => provider.open()");
                  provider.open(handler, this);
                },
                getDelay());
          }

          @Override
          public void onOpenResult(boolean ok) {
            String from = "onOpenResult(" + ok + ")";
            if (!check(from)) {
              return;
            }
            if (!ok) {
              RetryingHRProviderProxy.this.log(from + " => give up");
              return;
            }
            handler.post(
                () -> {
                  if (!check("delayed:" + from)) {
                    return;
                  }
                  RetryingHRProviderProxy.this.log(from + " => provider.connect()");
                  provider.connect(ref);
                });
          }
        });
    provider.connect(ref);
  }

  @Override
  public void onConnectResult(boolean connectOK) {
    getProxy().onConnectResult(connectOK);
  }
    
  @Override
  public void disconnect() {
    log("disconnect");
    this.requested = State.DISCONNECT;
    registerDefault();
    provider.disconnect();
  }

  @Override
  public void onDisconnectResult(boolean disconnectOK) {
    getProxy().onDisconnectResult(disconnectOK);
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
  public void log(HRProvider src, String msg) {
    String extra = (client == null) ? "Log w/o client: " : "";
    Log.d(src.getLogName(), "KESO: " + extra + msg);
    if (client != null) {
      if (Looper.myLooper() == Looper.getMainLooper()) {
        client.log(src, msg);
      } else {
        handler.post(
            () -> {
              if (client != null) client.log(src, msg);
            });
      }
    }
  }

  void log(final String msg) {
    String res =
        "[ "
            + provider.getProviderName()
            + ", request: "
            + requested
            + (attempt > 0 ? ", attempt: " + attempt : "")
            + " ]: "
            + msg;
    log(this, res);
  }

  class DefaultProxy implements HRProvider.HRClient {
    DefaultProxy() {}

    @Override
    public void onOpenResult(boolean ok) {
      var client = checkAndLog("onOpenResult(" + ok + ")");
      if (client != null) {
        client.onOpenResult(ok);
      }
    }

    @Override
    public void onScanResult(HRDeviceRef device) {
      var client = checkAndLog("onScanResult(" + device + ")");
      if (client != null) {
        client.onScanResult(device);
      }
    }

    @Override
    public void onConnectResult(boolean connectOK) {
      var client = checkAndLog("onConnectResult(" + connectOK + ")");
      if (client != null) {
        client.onConnectResult(connectOK);
      }
    }

    @Override
    public void onDisconnectResult(boolean disconnectOK) {
      var client = checkAndLog("onDisconnectResult(" + disconnectOK + ")");
      if (client != null) {
        client.onDisconnectResult(disconnectOK);
      }
    }

    @Override
    public void onCloseResult(boolean closeOK) {
      var client = checkAndLog("onCloseResult(" + closeOK + ")");
      if (client != null) {
        client.onCloseResult(closeOK);
      }
    }

    @Override
    public void log(HRProvider src, String msg) {}

    HRClient checkAndLog(String msg) {
      var client = RetryingHRProviderProxy.this.client;
      RetryingHRProviderProxy.this.log((client != null ? "proxy: " : "null: ") + msg);
      return client;
    }
  }
  ;

  void registerDefault() {
    setProxy(new DefaultProxy());
  }

  synchronized void setProxy(DefaultProxy p) {
    log("Update proxy from: " + proxy + ", to: " + p);
    proxy = p;
  }

  synchronized DefaultProxy getProxy() {
    if (proxy != null) {
      return proxy;
    }
    return new DefaultProxy();
  }
}
