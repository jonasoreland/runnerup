package org.runnerup.relven.ui

data class WorkoutSetupChoice(val value: String, val label: String)

data class WorkoutSetupStep(val title: String, val detail: String, val indent: Int)

data class GymExerciseOption(
  val exerciseId: String,
  val name: String,
  val category: String,
  val searchText: String = "",
)

data class GymExerciseDraft(
  val exerciseId: String,
  val name: String,
  val sets: String = "3",
  val repetitions: String = "8",
  val restSeconds: String = "60",
)

data class WorkoutSetupState(
  val selectedTab: Int,
  val goalType: String,
  val goalTypes: List<WorkoutSetupChoice>,
  val pace: String,
  val goalTime: String,
  val goalDistance: String,
  val goalDistanceUnit: String,
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
  val isGym: Boolean = false,
  val gymName: String = "",
  val gymQuery: String = "",
  val gymCatalogLoading: Boolean = false,
  val gymCatalogError: Boolean = false,
  val gymExercises: List<GymExerciseOption> = emptyList(),
  val gymDraft: List<GymExerciseDraft> = emptyList(),
)

object WorkoutSetupPresentation {
  private val simpleGoalTypes = setOf("none", "pace", "time", "distance", "heart_rate")

  @JvmStatic
  fun formatRange(minimum: String, maximum: String): String =
    if (minimum == maximum) minimum else "$minimum–$maximum"

  @JvmStatic
  fun goalTypeFromPreferences(savedGoalType: String?, legacyTargetType: Int): String =
    savedGoalType?.takeIf(simpleGoalTypes::contains)
      ?: when (legacyTargetType) {
        4 -> "pace"
        6 -> "heart_rate"
        else -> "none"
      }

  @JvmStatic
  fun targetDimensionForGoal(goalType: String): Int =
    when (goalType) {
      "pace" -> 4
      "heart_rate" -> 6
      else -> -1
    }

  fun showsPace(goalType: String) = goalType == "pace"

  fun showsGoalTime(goalType: String) = goalType == "time"

  fun showsGoalDistance(goalType: String) = goalType == "distance"

  fun showsHeartRate(goalType: String, zonesConfigured: Boolean) =
    goalType == "heart_rate" && zonesConfigured

  fun showsPace(targetType: Int) = targetType == 4

  fun showsHeartRate(targetType: Int, zonesConfigured: Boolean) =
    targetType == 6 && zonesConfigured

  fun showsActiveTime(intervalType: Int) = intervalType == 0

  fun showsRestTime(restType: Int) = restType == 0
}

object WorkoutSetupInput {
  fun isPositive(value: String): Boolean =
    value.trim().replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true

  fun isTime(value: String): Boolean {
    if (!value.matches(Regex("(?:[0-9]{1,2}:)?[0-5]?[0-9]:[0-5][0-9]"))) return false
    val seconds = value.split(':').map(String::toLong)
    val totalSeconds =
      if (seconds.size == 3) seconds[0] * 3600 + seconds[1] * 60 + seconds[2]
      else seconds[0] * 60 + seconds[1]
    return totalSeconds > 0
  }
}
