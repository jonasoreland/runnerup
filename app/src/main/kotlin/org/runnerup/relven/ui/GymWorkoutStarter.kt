package org.runnerup.relven.ui

/** Narrow bridge from the legacy Start fragment into Relven's existing strength-session host. */
interface GymWorkoutStarter {
  fun startQuickStrengthWorkout(name: String, workoutJson: String)
}
