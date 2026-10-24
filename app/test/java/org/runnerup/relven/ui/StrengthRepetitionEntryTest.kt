package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthRepetitionEntryTest {
  @Test
  fun plusAndMinusStartFromOneAndZeroWithoutGoingNegative() {
    assertEquals(1, adjustRepetitionCount(null, 1))
    assertEquals(0, adjustRepetitionCount(null, -1))
    assertEquals(0, adjustRepetitionCount(0, -1))
    assertEquals(9, adjustRepetitionCount(10, -1))
  }

  @Test
  fun repetitionCountSupportsZeroAndHighValuesWithinStorageLimit() {
    assertEquals(0, parseRepetitionCount("0"))
    assertEquals(10_000, parseRepetitionCount("10000"))
    assertNull(parseRepetitionCount("10001"))
    assertNull(parseRepetitionCount("-1"))
    assertNull(parseRepetitionCount(""))
  }
}
