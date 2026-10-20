package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RunMetricsPresentationTest {
  @Test
  fun hidesIntervalAndHeartRateWhenThoseSourcesAreUnavailable() {
    val presentation =
        runMetricsPresentation(
            total = RunMetricValues("12:34", "2.4 km", "5:14 /km", "145"),
            lap = RunMetricValues("4:10", "0.8 km", "5:12 /km", "148"),
            interval = RunMetricValues("1:00", "0.2 km", "4:50 /km", "160"),
            currentPace = "5:02 /km",
            currentHeartRate = "155",
            paceLabel = "Pace",
            showInterval = false,
            heartRateAvailable = false,
            heartRateNotice = "Ansluter till pulssensor")

    assertEquals(null, presentation.interval)
    assertEquals("", presentation.total.heartRate)
    assertEquals("", presentation.lap.heartRate)
    assertEquals(null, presentation.currentHeartRate)
    assertEquals("Ansluter till pulssensor", presentation.heartRateNotice)
  }

  @Test
  fun keepsIntervalAndHeartRateWhenTheTrackerProvidesThem() {
    val presentation =
        runMetricsPresentation(
            total = RunMetricValues("12:34", "2.4 km", "5:14 /km", "145"),
            lap = RunMetricValues("4:10", "0.8 km", "5:12 /km", "148"),
            interval = RunMetricValues("1:00", "0.2 km", "4:50 /km", "160"),
            currentPace = "5:02 /km",
            currentHeartRate = "155",
            paceLabel = "Pace",
            showInterval = true,
            heartRateAvailable = true,
            heartRateNotice = "ignored")

    assertEquals("1:00", presentation.interval?.time)
    assertEquals("145", presentation.total.heartRate)
    assertEquals("148", presentation.lap.heartRate)
    assertEquals("155", presentation.currentHeartRate)
    assertEquals(null, presentation.heartRateNotice)
  }
}
