package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunningSegmentEditInputTest {
  @Test fun parsesSupportedEndTypesAndRejectsInvalidValues() {
    assertEquals(1800.0, runningSegmentEndInput("time", "1800")!!.getDouble("seconds"), 0.0)
    assertEquals(400.0, runningSegmentEndInput("distance", "400")!!.getDouble("meters"), 0.0)
    assertEquals("manual", runningSegmentEndInput("manual", "")!!.getString("type"))
    assertNull(runningSegmentEndInput("time", "Infinity"))
    assertNull(runningSegmentEndInput("distance", "0"))
    assertNull(runningSegmentEndInput("time", "1000000001"))
  }

  @Test fun parsesTargetRangesAndAllowsNoTarget() {
    assertEquals(5.0, runningSegmentTargetInput("pace", "5", "6")!!.getDouble("minSecondsPerKm"), 0.0)
    assertNull(runningSegmentTargetInput("speed", "8", "7"))
    assertNull(runningSegmentTargetInput("heart_rate", "120.5", "150"))
    assertNull(runningSegmentTargetInput("heart_rate", "29", "150"))
    assertNull(runningSegmentTargetInput("none", "", ""))
  }
}
