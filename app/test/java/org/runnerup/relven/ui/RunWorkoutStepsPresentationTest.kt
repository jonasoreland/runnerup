package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWorkoutStepsPresentationTest {
  private val warmup = 0
  private val interval = 1
  private val recovery = 2

  @Test
  fun currentStepIsMarkedAndOtherStepsRemainOrdered() {
    val presentation = RunWorkoutStepsPresentationFactory.create(listOf(warmup, interval, recovery), 1)

    assertEquals(listOf(warmup, interval, recovery), presentation.items)
    assertEquals(1, presentation.currentIndex)
  }

  @Test
  fun invalidCurrentIndexDoesNotMarkAnyStepOrScroll() {
    val presentation = RunWorkoutStepsPresentationFactory.create(listOf(warmup, interval), 8)

    assertEquals(listOf(warmup, interval), presentation.items)
    assertEquals(null, presentation.currentIndex)
  }

  @Test
  fun emptyWorkoutHasNoCurrentStep() {
    val presentation = RunWorkoutStepsPresentationFactory.create(emptyList<Int>(), null)

    assertTrue(presentation.items.isEmpty())
    assertFalse(presentation.currentIndex != null)
  }
}
