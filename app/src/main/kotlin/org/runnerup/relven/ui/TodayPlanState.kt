package org.runnerup.relven.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.runnerup.workout.RelvenPlanImporter

internal sealed interface TodayPlanState {
  data object Loading : TodayPlanState

  data object NoActivePlan : TodayPlanState

  data object Unavailable : TodayPlanState

  data class NoPlannedItem(val planName: String) : TodayPlanState

  data class RestDay(val planName: String, val notes: String) : TodayPlanState

  data class PlannedWorkout(val planName: String, val workoutName: String, val notes: String) :
      TodayPlanState
}

internal enum class TodayPlanAction {
  CreateOrManagePlans,
  ManageActivePlan
}

internal fun todayPlanAction(state: TodayPlanState): TodayPlanAction? =
    when (state) {
      TodayPlanState.NoActivePlan -> TodayPlanAction.CreateOrManagePlans
      is TodayPlanState.NoPlannedItem,
      is TodayPlanState.RestDay,
      is TodayPlanState.PlannedWorkout -> TodayPlanAction.ManageActivePlan
      TodayPlanState.Loading,
      TodayPlanState.Unavailable -> null
    }

internal fun todayPlanState(
    plan: RelvenPlanImporter.Plan?,
    nowMillis: Long
): TodayPlanState {
  if (plan == null) return TodayPlanState.NoActivePlan

  val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
  formatter.timeZone = TimeZone.getTimeZone(plan.timezone())
  val today = formatter.format(Date(nowMillis))
  val session = plan.days().firstOrNull { it.date() == today } ?: return TodayPlanState.NoPlannedItem(
      plan.name())

  if (session.kind() == "rest") return TodayPlanState.RestDay(plan.name(), session.notes())

  val workoutName = plan.workoutNames()[session.workoutId()] ?: return TodayPlanState.Unavailable
  return TodayPlanState.PlannedWorkout(plan.name(), workoutName, session.notes())
}
