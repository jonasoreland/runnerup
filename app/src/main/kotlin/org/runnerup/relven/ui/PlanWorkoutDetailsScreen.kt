package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R
import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.strength.CompletedStrengthSession

@Composable
internal fun PlanWorkoutDetailsScreen(
    workout: PlanCalendarEntry.Workout,
    dateLabel: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onRename: (String, String) -> Unit,
    onReschedule: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditRunningEnd: (String, RunningSegmentSummary) -> Unit,
    onEditRunningTarget: (String, RunningSegmentSummary) -> Unit,
    onSaveFavoriteWorkout: (String, String) -> Unit,
    onStartStrength: () -> Unit,
    onStartRunning: () -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit,
    exerciseCatalog: ExerciseCatalog?
) {
  var moreActionsExpanded by rememberSaveable(workout.sessionId) { mutableStateOf(false) }
  var showFavoriteDialog by rememberSaveable(workout.sessionId) { mutableStateOf(false) }
  var favoriteName by rememberSaveable(workout.sessionId) { mutableStateOf(workout.workoutName) }
  val workoutDocument = workout.workoutDocument

  if (showFavoriteDialog && workoutDocument != null) {
    AlertDialog(
        onDismissRequest = { showFavoriteDialog = false },
        title = { Text(stringResource(org.runnerup.R.string.plan_favorite_save_title)) },
        text = {
          OutlinedTextField(
              value = favoriteName,
              onValueChange = { favoriteName = it.take(80) },
              label = { Text(stringResource(org.runnerup.R.string.plan_favorite_name)) },
              singleLine = true)
        },
        confirmButton = {
          TextButton(
              enabled = favoriteName.trim().isNotEmpty(),
              onClick = {
                onSaveFavoriteWorkout(favoriteName.trim(), workoutDocument)
                showFavoriteDialog = false
              }) { Text(stringResource(org.runnerup.R.string.plan_favorite_save)) }
        },
        dismissButton = {
          TextButton(onClick = { showFavoriteDialog = false }) {
            Text(stringResource(R.string.Plan_calendar_cancel_reschedule))
          }
        })
  }

  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.Home_activity_detail_back),
            tint = RelvenColors.Orange)
      }
      Text(
          stringResource(R.string.Plan_calendar_workout_details_title),
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f).semantics { heading() })
      IconButton(
          onClick = { moreActionsExpanded = true },
          enabled = enabled,
          modifier = Modifier.size(48.dp)) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.Plan_calendar_workout_more_actions),
            tint = RelvenColors.Text)
      }
      DropdownMenu(
          expanded = moreActionsExpanded,
          onDismissRequest = { moreActionsExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Plan_calendar_rename_workout)) },
            enabled = enabled,
            onClick = {
              moreActionsExpanded = false
              onRename(workout.workoutId, workout.workoutName)
            })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Plan_calendar_reschedule)) },
            enabled = enabled,
            onClick = {
              moreActionsExpanded = false
              onReschedule(workout.sessionId)
            })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Plan_calendar_remove), color = RelvenColors.Orange) },
            enabled = enabled,
            onClick = {
              moreActionsExpanded = false
              onRemove(workout.sessionId)
            })
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(workout.workoutName, fontSize = 28.sp, fontWeight = FontWeight.Bold)
      Text(dateLabel, color = RelvenColors.MutedText, fontSize = 14.sp)
    }

    if (workoutDocument != null) {
      TextButton(
          onClick = { favoriteName = workout.workoutName; showFavoriteDialog = true },
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(org.runnerup.R.string.plan_favorite_save_action), color = RelvenColors.Orange)
      }
    }

    workout.strengthDetails?.let { details ->
      Text(
          stringResource(
              if (details.sport() == "prehab") R.string.Plan_calendar_pre_hab_workout
              else R.string.Plan_calendar_strength_workout),
          color = RelvenColors.Orange,
          fontWeight = FontWeight.SemiBold)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        details.exercises().forEachIndexed { index, exercise ->
          val presentation =
              planExercisePresentation(
                  exercise.exerciseId(), exercise.name(), exercise.instructions(), exerciseCatalog)
          Surface(
              modifier = Modifier.fillMaxWidth(),
              color = RelvenColors.Surface,
              shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                  "${index + 1}. ${presentation.name}",
                  fontSize = 18.sp,
                  fontWeight = FontWeight.SemiBold)
              Text(
                  stringResource(
                      R.string.Plan_calendar_strength_prescription,
                      exercise.sets(),
                      exercise.minRepetitions(),
                      exercise.maxRepetitions()),
                  color = RelvenColors.Text,
                  fontWeight = FontWeight.Medium)
              exercise.durationSeconds()?.let {
                Text(
                    stringResource(R.string.Plan_calendar_strength_timed, it),
                    color = RelvenColors.MutedText)
              }
              Text(
                  stringResource(R.string.Plan_calendar_strength_rest, exercise.restSeconds()),
                  color = RelvenColors.MutedText)
              presentation.instructions.takeIf(String::isNotBlank)?.let {
                Text(it, lineHeight = 22.sp, color = RelvenColors.MutedText)
              }
            }
          }
        }
      }
      workout.completion?.let { completion ->
        Text(
            stringResource(R.string.Strength_session_completed),
            color = RelvenColors.Orange,
            fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onViewStrengthCompletion(completion) }) {
          Text(stringResource(R.string.Strength_session_view_summary))
        }
      }
      Button(
          onClick = onStartStrength,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
            Text(
                stringResource(
                    if (workout.completion == null) R.string.Strength_session_start
                    else R.string.Strength_session_start_again),
                color = RelvenColors.Background,
                fontWeight = FontWeight.SemiBold)
          }
    }

    if (workout.runningSegments.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        workout.runningSegments.forEachIndexed { index, segment ->
          Surface(
              modifier = Modifier.fillMaxWidth(),
              color = RelvenColors.Surface,
              shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(50),
                    color = RelvenColors.SurfaceRaised) {
                  BoxContentIndex(index + 1)
                }
                Text(
                    segment.name ?: stringResource(phaseLabel(segment.phase)),
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
              }
              Text(segmentEndLabel(segment), color = RelvenColors.Text)
              Text(
                  segment.target?.let { target -> segmentTargetLabel(target) }
                      ?: stringResource(R.string.Plan_calendar_no_target),
                  color = RelvenColors.MutedText)
              if (segment.repeatCount > 1) {
                Text(
                    stringResource(R.string.Plan_calendar_repeat_count, segment.repeatCount),
                    color = RelvenColors.MutedText)
              }
              Column(horizontalAlignment = Alignment.Start) {
                TextButton(
                    onClick = { onEditRunningEnd(workout.workoutId, segment) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                  Text(stringResource(R.string.Plan_calendar_edit_end))
                }
                TextButton(
                    onClick = { onEditRunningTarget(workout.workoutId, segment) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                  Text(stringResource(R.string.Plan_calendar_edit_target))
                }
              }
            }
          }
        }
      }
      Button(
          onClick = onStartRunning,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
        Text(stringResource(R.string.Plan_calendar_start_workout), color = RelvenColors.Background, fontWeight = FontWeight.SemiBold)
      }
    }

    workout.notes.takeIf(String::isNotBlank)?.let { Text(it, color = RelvenColors.MutedText) }

  }
}

@Composable
private fun BoxContentIndex(index: Int) {
  Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
      contentAlignment = Alignment.Center) {
    Text(index.toString(), color = RelvenColors.Orange, fontWeight = FontWeight.SemiBold)
  }
}
