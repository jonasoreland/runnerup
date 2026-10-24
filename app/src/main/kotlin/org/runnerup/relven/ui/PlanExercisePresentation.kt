package org.runnerup.relven.ui

import org.runnerup.relven.exercise.ExerciseCatalog

internal data class PlanExercisePresentation(val name: String, val instructions: String)

internal fun planExercisePresentation(
    exerciseId: String,
    snapshotName: String,
    snapshotInstructions: String,
    catalog: ExerciseCatalog?
): PlanExercisePresentation {
  val localizedExercise = catalog?.exercises?.firstOrNull { it.exerciseId == exerciseId }
  return PlanExercisePresentation(
      name = localizedExercise?.name ?: snapshotName,
      instructions = localizedExercise?.instructions?.joinToString("\n") ?: snapshotInstructions)
}
