package org.runnerup.util;

import java.util.List;

/** Small, deterministic helpers used to color route segments from recorded samples. */
public final class RouteMetricColoring {
  private RouteMetricColoring() {}

  public static boolean isAvailable(List<Double> values) {
    int count = 0;
    for (Double value : values) {
      if (value != null && Double.isFinite(value)) count++;
    }
    return count >= 2;
  }

  public static int colorIndex(double value, double min, double max) {
    if (!Double.isFinite(value) || max <= min) return 2;
    return Math.max(0, Math.min(4, (int) Math.floor((value - min) / (max - min) * 5)));
  }
}
