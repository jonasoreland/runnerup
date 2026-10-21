package org.runnerup.relven.ui

data class RunWorkoutStepItem(val step: org.runnerup.workout.Step, val indentLevel: Int)

data class RunWorkoutStepsPresentation<T>(val items: List<T>, val currentIndex: Int?)

object RunWorkoutStepsPresentationFactory {
  fun <T> create(items: List<T>, currentIndex: Int?): RunWorkoutStepsPresentation<T> {
    val validCurrentIndex = currentIndex?.takeIf { it in items.indices }
    return RunWorkoutStepsPresentation(items = items, currentIndex = validCurrentIndex)
  }
}
