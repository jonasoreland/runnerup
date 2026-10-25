package org.runnerup.tracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GpsFixQualityTest {
  @Test
  public void repeatedCallbacksDoNotReplaceMeasuredAccuracy() {
    assertFalse(GpsFixQuality.isUsableFix(true, 30f));
    assertFalse(GpsFixQuality.isUsableFix(true, 30f));
  }

  @Test
  public void onlyFinitePositiveAccuracyWithinTenMetersIsReady() {
    assertTrue(GpsFixQuality.isUsableFix(true, 9f));
    assertTrue(GpsFixQuality.isUsableFix(true, 10f));
    assertFalse(GpsFixQuality.isUsableFix(true, 10.1f));
    assertFalse(GpsFixQuality.isUsableFix(false, 1f));
    assertFalse(GpsFixQuality.isUsableFix(true, Float.NaN));
    assertFalse(GpsFixQuality.isUsableFix(true, Float.POSITIVE_INFINITY));
    assertFalse(GpsFixQuality.isUsableFix(true, 0f));
  }
}
