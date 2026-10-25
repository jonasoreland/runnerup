package org.runnerup.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WearPeerAvailabilityTest {
  @Test
  public void requiresExpectedPhoneNodeWhenKnown() {
    assertFalse(WearPeerAvailability.isPhonePeerConnected("phone-a", Collections.emptyList()));
    assertTrue(WearPeerAvailability.isPhonePeerConnected("phone-a", Arrays.asList("phone-a")));
    assertFalse(WearPeerAvailability.isPhonePeerConnected("phone-a", Arrays.asList("phone-b")));
  }

  @Test
  public void acceptsAnyConnectedPeerOnlyWhenExpectedPhoneIsUnknown() {
    assertFalse(WearPeerAvailability.isPhonePeerConnected(null, Collections.emptyList()));
    assertTrue(WearPeerAvailability.isPhonePeerConnected(null, Arrays.asList("phone-a")));
  }

  @Test
  public void localApiConnectionAloneDoesNotMeanPhonePeerIsConnected() {
    assertEquals(WearConnectionStatus.DISCONNECTED, WearPeerAvailability.status(false, false));
    assertEquals(WearConnectionStatus.DISCONNECTED, WearPeerAvailability.status(true, false));
    assertEquals(WearConnectionStatus.CONNECTED, WearPeerAvailability.status(true, true));
  }
}
