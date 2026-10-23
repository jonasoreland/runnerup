package org.runnerup.relven.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.runnerup.common.util.Constants.DB
import org.runnerup.workout.Sport

internal data class ActivityEditCommand(
    val activityId: Long,
    val sport: Int,
    val manualDistanceMeters: Double?,
    val comment: String)

internal sealed interface ActivityEditSaveState {
  data object Idle : ActivityEditSaveState

  data object Saving : ActivityEditSaveState

  data object Saved : ActivityEditSaveState

  data object Failed : ActivityEditSaveState
}

internal fun activityEditDistanceMeters(input: String, metersPerUnit: Double): Double? {
  if (!metersPerUnit.isFinite() || metersPerUnit <= 0.0) return null
  val distance = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
  if (!distance.isFinite() || distance < 0.0) return null
  return (distance * metersPerUnit).takeIf(Double::isFinite)
}

internal fun activityEditDistanceMeters(
    input: String,
    metersPerUnit: Double,
    initialInput: String,
    originalDistanceMeters: Double?
): Double? {
  if (input == initialInput) {
    originalDistanceMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let { return it }
  }
  return activityEditDistanceMeters(input, metersPerUnit)
}

/** Applies the editable fields exposed by RunnerUp's saved-activity editor. */
internal fun saveHistoryActivityEdit(
    database: SQLiteDatabase,
    command: ActivityEditCommand
): Boolean {
  if (command.activityId <= 0L || Sport.values().none { it.dbValueForEdit() == command.sport }) {
    return false
  }
  val manualDistance = command.manualDistanceMeters
  if (Sport.hasManualDistance(command.sport) &&
      (manualDistance == null || !manualDistance.isFinite() || manualDistance < 0.0)) {
    return false
  }

  val values =
      ContentValues().apply {
        put(DB.ACTIVITY.SPORT, command.sport)
        put(DB.ACTIVITY.COMMENT, command.comment)
        if (Sport.hasManualDistance(command.sport)) {
          put(DB.ACTIVITY.DISTANCE, manualDistance)
        }
      }
  return database.update(
          DB.ACTIVITY.TABLE,
          values,
          "_id = ? AND ${DB.ACTIVITY.DELETED} = 0",
          arrayOf(command.activityId.toString())) == 1
}

private fun Sport.dbValueForEdit(): Int = getDbValue()
