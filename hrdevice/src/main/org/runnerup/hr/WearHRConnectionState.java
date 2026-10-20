package org.runnerup.hr;

/** Owns Wear heart-rate connection state independently of Android services and clocks. */
public final class WearHRConnectionState {
  public enum Status {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNAVAILABLE
  }

  private static final long MAX_SAMPLE_AGE_MILLIS = 15_000;

  private Status status = Status.DISCONNECTED;
  private long lastSampleAtMillis;

  public synchronized Status getStatus() {
    return status;
  }

  public synchronized boolean beginConnect() {
    if (status == Status.CONNECTING
        || status == Status.CONNECTED
        || status == Status.RECONNECTING) {
      return false;
    }
    status = Status.CONNECTING;
    lastSampleAtMillis = 0;
    return true;
  }

  public synchronized boolean onValidSample(long nowMillis) {
    boolean stateChanged = status != Status.CONNECTED;
    status = Status.CONNECTED;
    lastSampleAtMillis = nowMillis;
    return stateChanged;
  }

  public synchronized boolean beginRecoveryIfStale(long nowMillis) {
    if (status != Status.CONNECTED || nowMillis - lastSampleAtMillis <= MAX_SAMPLE_AGE_MILLIS) {
      return false;
    }
    status = Status.RECONNECTING;
    return true;
  }

  public synchronized boolean markUnavailable() {
    if (status != Status.CONNECTING && status != Status.RECONNECTING) return false;
    status = Status.UNAVAILABLE;
    return true;
  }

  public synchronized boolean isConnecting() {
    return status == Status.CONNECTING || status == Status.RECONNECTING;
  }

  public synchronized void disconnect() {
    status = Status.DISCONNECTED;
    lastSampleAtMillis = 0;
  }
}
