package org.runnerup.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RouteMetricColoringTest {
  @Test
  public void rejectsMetricsWithTooFewValues() {
    assertFalse(RouteMetricColoring.isAvailable(Arrays.asList(120.0, null, null)));
    assertTrue(RouteMetricColoring.isAvailable(List.of(120.0, 130.0)));
  }

  @Test
  public void bucketsLowAndHighValuesAcrossPalette() {
    assertEquals(0, RouteMetricColoring.colorIndex(1.0, 1.0, 5.0));
    assertEquals(4, RouteMetricColoring.colorIndex(5.0, 1.0, 5.0));
    assertEquals(2, RouteMetricColoring.colorIndex(3.0, 1.0, 5.0));
  }

  @Test
  public void keepsConstantMetricInMiddleBucket() {
    assertEquals(2, RouteMetricColoring.colorIndex(7.0, 7.0, 7.0));
  }
}
