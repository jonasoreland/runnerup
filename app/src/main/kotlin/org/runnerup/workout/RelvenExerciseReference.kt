package org.runnerup.workout

/** Canonical exercise metadata used to validate an imported pinned snapshot. */
internal data class RelvenExerciseReference(
    val libraryRevision: Int,
    val exerciseRevision: Int,
    val name: String,
    val instructions: String,
    val equipment: List<String>
)
