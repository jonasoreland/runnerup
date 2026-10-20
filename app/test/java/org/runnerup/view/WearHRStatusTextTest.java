package org.runnerup.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.runnerup.common.R;
import org.runnerup.hr.WearHRConnectionState;

public class WearHRStatusTextTest {
  @Test
  public void mapsEveryConnectionStateToLocalizedStringResource() {
    assertEquals(
        R.string.wear_hr_status_disconnected,
        WearHRStatusText.resourceFor(WearHRConnectionState.Status.DISCONNECTED));
    assertEquals(
        R.string.wear_hr_status_connecting,
        WearHRStatusText.resourceFor(WearHRConnectionState.Status.CONNECTING));
    assertEquals(
        R.string.wear_hr_status_connected,
        WearHRStatusText.resourceFor(WearHRConnectionState.Status.CONNECTED));
    assertEquals(
        R.string.wear_hr_status_reconnecting,
        WearHRStatusText.resourceFor(WearHRConnectionState.Status.RECONNECTING));
    assertEquals(
        R.string.wear_hr_status_unavailable,
        WearHRStatusText.resourceFor(WearHRConnectionState.Status.UNAVAILABLE));
  }
}
