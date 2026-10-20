package org.runnerup.hr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WearHeartRateSampleTest {
  @Test
  public void freshMeasurementExpiresAndCanBeCleared() {
    WearHeartRateSample sample = new WearHeartRateSample();
    assertTrue(sample.update("148", 1_000_000_000L));
    assertEquals(148, sample.valueAt(15_000_000_000L));
    assertEquals(0, sample.valueAt(16_000_000_001L));
    sample.clear();
    assertEquals(0, sample.valueAt(1_000_000_001L));
  }

  @Test
  public void rejectsInvalidPayloadWithoutReplacingValidReading() {
    WearHeartRateSample sample = new WearHeartRateSample();
    assertTrue(sample.update("120", 2_000_000_000L));
    assertFalse(sample.update("0", 3_000_000_000L));
    assertFalse(sample.update("999", 3_000_000_000L));
    assertFalse(sample.update("abc", 3_000_000_000L));
    assertEquals(120, sample.valueAt(3_000_000_000L));
  }
}
