package org.runnerup.relven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R
import org.runnerup.relven.strength.CompletedStrengthSession

@Composable
internal fun PlanWorkoutDetailsScreen(
    workout: PlanCalendarEntry.Workout,
    dateLabel: String,
    planName: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onRename: (String, String) -> Unit,
    onReschedule: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditRunningEnd: (String, RunningSegmentSummary) -> Unit,
    onEditRunningTarget: (String, RunningSegmentSummary) -> Unit,
    onStartStrength: () -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit
) {
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
          modifier = Modifier.semantics { heading() })
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(workout.workoutName, fontSize = 28.sp, fontWeight = FontWeight.Bold)
      Text(dateLabel, color = RelvenColors.MutedText, fontSize = 14.sp)
      Text(planName, color = RelvenColors.MutedText, fontSize = 14.sp)
    }

    workout.strengthDetails?.let { details ->
      Text(
          stringResource(
              if (details.sport() == "prehab") R.string.Plan_calendar_pre_hab_workout
              else R.string.Plan_calendar_strength_workout),
          color = RelvenColors.Orange,
          fontWeight = FontWeight.SemiBold)
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        details.exercises().forEachIndexed { index, exercise ->
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${index + 1}. ${exercise.name()}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.Plan_calendar_strength_prescription,
                    exercise.sets(),
                    exercise.minRepetitions(),
                    exercise.maxRepetitions()),
                color = RelvenColors.MutedText)
            exercise.durationSeconds()?.let {
              Text(
                  stringResource(R.string.Plan_calendar_strength_timed, it),
                  color = RelvenColors.MutedText)
            }
            Text(
                stringResource(R.string.Plan_calendar_strength_rest, exercise.restSeconds()),
                color = RelvenColors.MutedText)
            Text(exercise.instructions(), lineHeight = 22.sp)
          }
          if (index != details.exercises().lastIndex) {
            Spacer(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(RelvenColors.Surface))
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
      } ?: Button(
          onClick = onStartStrength,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
            Text(
                stringResource(R.string.Strength_session_start),
                color = RelvenColors.Background,
                fontWeight = FontWeight.SemiBold)
          }
    }

    if (workout.runningSegments.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        workout.runningSegments.forEachIndexed { index, segment ->
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                segment.name ?: stringResource(phaseLabel(segment.phase)),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold)
            Text(segmentEndLabel(segment), color = RelvenColors.MutedText)
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
          if (index != workout.runningSegments.lastIndex) {
            Spacer(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(RelvenColors.Surface))
          }
        }
      }
    }

    workout.notes.takeIf(String::isNotBlank)?.let { Text(it, color = RelvenColors.MutedText) }

    Column(horizontalAlignment = Alignment.Start) {
      TextButton(
          onClick = { onRename(workout.workoutId, workout.workoutName) },
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Plan_calendar_rename_workout))
      }
      TextButton(
          onClick = { onReschedule(workout.sessionId) },
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Plan_calendar_reschedule))
      }
      TextButton(
          onClick = { onRemove(workout.sessionId) },
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Plan_calendar_remove))
      }
    }
  }
}
