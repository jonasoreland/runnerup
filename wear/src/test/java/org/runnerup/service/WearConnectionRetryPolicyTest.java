package org.runnerup.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WearConnectionRetryPolicyTest {
  @Test
  public void retryDelayDoublesAndCapsAtSixteenSeconds() {
    assertEquals(1_000L, WearConnectionRetryPolicy.delayMillis(0));
    assertEquals(2_000L, WearConnectionRetryPolicy.delayMillis(1));
    assertEquals(4_000L, WearConnectionRetryPolicy.delayMillis(2));
    assertEquals(8_000L, WearConnectionRetryPolicy.delayMillis(3));
    assertEquals(16_000L, WearConnectionRetryPolicy.delayMillis(4));
    assertEquals(16_000L, WearConnectionRetryPolicy.delayMillis(100));
  }

  @Test
  public void retriesOnlyWhenClientIsNeitherConnectedNorConnecting() {
    assertTrue(WearConnectionRetryPolicy.shouldRetry(false, false));
    assertFalse(WearConnectionRetryPolicy.shouldRetry(true, false));
    assertFalse(WearConnectionRetryPolicy.shouldRetry(false, true));
  }
}
