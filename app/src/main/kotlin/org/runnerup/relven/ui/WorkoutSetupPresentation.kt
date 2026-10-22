package org.runnerup.relven.ui

data class WorkoutSetupChoice(val value: String, val label: String)

data class WorkoutSetupStep(val title: String, val detail: String, val indent: Int)

data class WorkoutSetupState(
  val selectedTab: Int,
  val targetType: Int,
  val targetTypes: List<WorkoutSetupChoice>,
  val pace: String,
  val heartRateZone: String,
  val heartRateZones: List<WorkoutSetupChoice>,
  val basicAudio: String,
  val basicAudioSchemes: List<WorkoutSetupChoice>,
  val intervalRepetitions: String,
  val intervalType: Int,
  val intervalTime: String,
  val intervalDistance: String,
  val restType: Int,
  val restTime: String,
  val restDistance: String,
  val intervalAudio: String,
  val intervalAudioSchemes: List<WorkoutSetupChoice>,
  val advancedWorkout: String,
  val advancedWorkouts: List<WorkoutSetupChoice>,
  val advancedSteps: List<WorkoutSetupStep>,
  val advancedAudio: String,
  val advancedAudioSchemes: List<WorkoutSetupChoice>,
)

object WorkoutSetupPresentation {
  fun showsPace(targetType: Int) = targetType == 4

  fun showsHeartRate(targetType: Int, zonesConfigured: Boolean) =
    targetType == 6 && zonesConfigured

  fun showsActiveTime(intervalType: Int) = intervalType == 0

  fun showsRestTime(restType: Int) = restType == 0
}

object WorkoutSetupInput {
  fun isPositive(value: String): Boolean = value.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true

  fun isTime(value: String): Boolean =
    value.matches(Regex("(?:[0-9]{1,2}:)?[0-5]?[0-9]:[0-5][0-9]"))
}
