package org.runnerup.relven.exercise

import android.content.Context

/** Exposes the user's current local exercise collection to the explicit Plan Kit export action. */
object RelvenPlanKitExerciseData {
  @JvmStatic
  fun exportCustomCollection(context: Context): ByteArray =
      ExerciseCatalogRepository(context).exportCustomExercises()
}
