package org.runnerup.relven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanStrengthInputTest {
  @Test
  fun blankPrescriptionFieldIsInvalid() {
    assertFalse(isPlanStrengthNumberInputValid("", min = 1, max = 100))
  }

  @Test
  fun prescriptionFieldMustStayWithinItsConfiguredRange() {
    assertFalse(isPlanStrengthNumberInputValid("0", min = 1, max = 100))
    assertFalse(isPlanStrengthNumberInputValid("101", min = 1, max = 100))
    assertTrue(isPlanStrengthNumberInputValid("100", min = 1, max = 100))
  }

  @Test
  fun maximumRepetitionsCannotBeBelowMinimum() {
    assertFalse(isPlanStrengthNumberInputValid("7", min = 8, max = 500))
    assertTrue(isPlanStrengthNumberInputValid("8", min = 8, max = 500))
  }
}
