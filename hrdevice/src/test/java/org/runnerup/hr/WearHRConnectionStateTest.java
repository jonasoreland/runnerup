package org.runnerup.hr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WearHRConnectionStateTest {
  @Test
  public void movesFromDisconnectedThroughConnectAndSample() {
    WearHRConnectionState state = new WearHRConnectionState();

    assertEquals(WearHRConnectionState.Status.DISCONNECTED, state.getStatus());
    assertTrue(state.beginConnect());
    assertEquals(WearHRConnectionState.Status.CONNECTING, state.getStatus());
    assertTrue(state.onValidSample(1_000));
    assertEquals(WearHRConnectionState.Status.CONNECTED, state.getStatus());
    assertFalse(state.beginConnect());
  }

  @Test
  public void becomesReconnectingOnlyAfterSampleExpiresAndCanRecover() {
    WearHRConnectionState state = new WearHRConnectionState();
    state.beginConnect();
    state.onValidSample(1_000);

    assertFalse(state.beginRecoveryIfStale(16_000));
    assertTrue(state.beginRecoveryIfStale(16_001));
    assertEquals(WearHRConnectionState.Status.RECONNECTING, state.getStatus());
    assertTrue(state.onValidSample(20_000));
    assertEquals(WearHRConnectionState.Status.CONNECTED, state.getStatus());
    assertFalse(state.markUnavailable());
  }

  @Test
  public void recoveryTimeoutAndDisconnectAllowManualReconnect() {
    WearHRConnectionState state = new WearHRConnectionState();
    state.beginConnect();
    assertTrue(state.markUnavailable());
    assertEquals(WearHRConnectionState.Status.UNAVAILABLE, state.getStatus());
    assertTrue(state.beginConnect());

    state.onValidSample(1_000);
    assertTrue(state.beginRecoveryIfStale(16_001));
    assertTrue(state.markUnavailable());
    state.disconnect();
    assertEquals(WearHRConnectionState.Status.DISCONNECTED, state.getStatus());
  }
}
