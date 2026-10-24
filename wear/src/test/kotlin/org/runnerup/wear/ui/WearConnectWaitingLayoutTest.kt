package org.runnerup.wear.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConnectWaitingLayoutTest {
  @Test
  fun compactLayoutIsSelectedForSmallRoundAndShortDisplays() {
    assertTrue(isCompactWearConnectLayout(192f, 192f))
    assertTrue(isCompactWearConnectLayout(220f, 180f))
    assertFalse(isCompactWearConnectLayout(280f, 280f))
  }
}
