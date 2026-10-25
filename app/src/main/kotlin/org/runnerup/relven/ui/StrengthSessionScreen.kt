package org.runnerup.relven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import kotlinx.coroutines.delay
import org.runnerup.common.R
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.strengthSessionSummary

@Composable
internal fun StrengthSessionScreen(
    session: StrengthSession,
    completedAtMillis: Long?,
    saveError: Boolean,
    isSavingProgress: Boolean,
    isSavingCompletion: Boolean,
    onExport: (() -> Unit)? = null,
    watchPreparationLabel: String? = null,
    onEditResult: (StrengthSession.SetResult) -> Unit,
    onTick: () -> Unit,
    onLogRepetitions: (StrengthSession.RepetitionResultInput) -> Unit,
    onSkipPendingResult: () -> Unit,
    onSkipExercise: () -> Unit,
    onSkipRest: () -> Unit,
    onBegin: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit
) {
  BackHandler(onBack = onClose)
  val completed = completedAtMillis != null
  val exercise = session.currentExerciseOrNull()
  val pendingResult = session.pendingResults.firstOrNull()
  var editingResultKey by remember(session.executionId) { mutableStateOf<String?>(null) }
  val haptic = LocalHapticFeedback.current
  var previousPhase by remember(session.executionId) { mutableStateOf(session.phase) }
  LaunchedEffect(session.phase) {
    if (shouldSignalTimedWorkFinished(previousPhase, session.phase)) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    previousPhase = session.phase
  }
  LaunchedEffect(
      session.executionId,
      session.phase,
      session.exerciseIndex,
      session.setNumber,
      session.timerDeadlineElapsedRealtimeMillis,
      isSavingProgress) {
    if (!completed && !isSavingProgress && (session.phase == StrengthSession.Phase.TIMED_WORK || session.phase == StrengthSession.Phase.REST)) {
      onTick()
      while (true) {
        delay(1_000)
        onTick()
      }
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
          .verticalScroll(rememberScrollState())
          .background(RelvenColors.Background).padding(horizontal = 24.dp, vertical = 28.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Text(
        stringResource(if (completed) R.string.Strength_session_summary_title else R.string.Strength_session_title),
        color = RelvenColors.Text,
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold)
    Text(session.workout.name(), color = RelvenColors.MutedText, fontSize = 15.sp)

    if (saveError) {
      Surface(color = RelvenColors.SurfaceRaised, shape = RoundedCornerShape(14.dp)) {
        Text(
            stringResource(R.string.Strength_session_error),
            color = RelvenColors.Text,
            modifier = Modifier.padding(16.dp))
      }
    }

    pendingResult?.let { pending ->
      Surface(
          color = RelvenColors.Surface,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              stringResource(R.string.Strength_session_pending_result),
              color = RelvenColors.Orange,
              fontWeight = FontWeight.SemiBold)
          Text(pending.exerciseName, color = RelvenColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
          RepetitionCountEntry(
              key = "${session.executionId}:${pending.exerciseIndex}:${pending.setNumber}",
              isSaving = isSavingProgress,
              onConfirm = onLogRepetitions)
          TextButton(
              onClick = onSkipPendingResult,
              enabled = !isSavingProgress,
              modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.Strength_session_skip_result))
          }
        }
      }
    }

    if (completed || session.phase == StrengthSession.Phase.COMPLETE) {
      SessionResultsSummary(
          session = session,
          editingResultKey = editingResultKey,
          isSaving = isSavingProgress,
          onBeginEdit = { editingResultKey = it.editIdentity(session.executionId) },
          onEditResult = { result ->
            onEditResult(result)
            editingResultKey = null
          })
      if (completed) {
        onExport?.let { export ->
          TextButton(onClick = export, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.Strength_session_export))
          }
        }
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          Text(stringResource(R.string.Strength_session_exit))
        }
      } else {
        Text(
            stringResource(R.string.Strength_session_finish_confirmation),
            color = RelvenColors.MutedText)
        Button(
            onClick = onFinish,
            enabled = !isSavingCompletion && !isSavingProgress,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          Text(stringResource(R.string.Strength_session_finish))
        }
      }
    } else {
      exercise?.let {
        val totalSets = it.sets()
        Surface(
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(it.name(), color = RelvenColors.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(it.instructions(), color = RelvenColors.MutedText, fontSize = 16.sp)
            Text(
                stringResource(
                    R.string.Plan_calendar_strength_prescription,
                    it.sets(),
                    it.minRepetitions(),
                    it.maxRepetitions()),
                color = RelvenColors.MutedText)
            if (it.durationSeconds() != null) {
              Text(
                  stringResource(R.string.Plan_calendar_strength_timed, it.durationSeconds()!!),
                  color = RelvenColors.MutedText)
            }
            Text(
                stringResource(R.string.Plan_calendar_strength_rest, it.restSeconds()),
                color = RelvenColors.MutedText)
            Text(
                stringResource(R.string.Strength_session_set, session.setNumber, totalSets),
                color = RelvenColors.Orange,
                fontWeight = FontWeight.SemiBold)
            if (session.phase == StrengthSession.Phase.PREPARING && watchPreparationLabel != null) {
              Text(
                  watchPreparationLabel,
                  color = RelvenColors.MutedText,
                  fontSize = 14.sp,
                  modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            when (session.phase) {
              StrengthSession.Phase.PREPARING -> {
                Text(
                    "Redo att starta",
                    color = RelvenColors.MutedText,
                    fontSize = 16.sp)
                Button(
                    onClick = onBegin,
                    enabled = !isSavingProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                  Text("Starta pass")
                }
              }
              StrengthSession.Phase.REPETITIONS -> {
                if (pendingResult != null) {
                  Text(
                      stringResource(R.string.Strength_session_log_pending_first),
                      color = RelvenColors.MutedText)
                } else {
                  RepetitionCountEntry(
                      key = "${session.executionId}:${session.exerciseIndex}:${session.setNumber}",
                      isSaving = isSavingProgress,
                      onConfirm = onLogRepetitions)
                }
              }
              StrengthSession.Phase.TIMED_WORK -> {
                Text(
                    "${session.secondsRemaining}",
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = RelvenColors.Orange,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.Strength_session_timed_work), color = RelvenColors.MutedText)
              }
              StrengthSession.Phase.REST -> {
                Text(
                    stringResource(R.string.Strength_session_resting),
                    color = RelvenColors.MutedText,
                    fontSize = 16.sp)
                Text(
                    "${session.secondsRemaining}",
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = RelvenColors.Orange,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = onSkipRest, enabled = !isSavingProgress) { Text(stringResource(R.string.Strength_session_skip_rest)) }
              }
              StrengthSession.Phase.AWAITING_RESULT -> {
                Text(stringResource(R.string.Strength_session_pending_result), color = RelvenColors.MutedText)
              }
              StrengthSession.Phase.PAUSED -> {
                Text(stringResource(R.string.Strength_session_paused), color = RelvenColors.MutedText)
                Button(
                    onClick = onResume,
                    enabled = !isSavingProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                  Text(stringResource(R.string.Strength_session_resume))
                }
              }
              StrengthSession.Phase.COMPLETE -> Unit
            }
            session.results.filter { it.exerciseIndex == session.exerciseIndex }.forEach { result ->
              RecordedSetResult(
                  result = result,
                  setCount = it.sets(),
                  editing = editingResultKey == result.editIdentity(session.executionId),
                  isSaving = isSavingProgress,
                  onBeginEdit = { editingResultKey = result.editIdentity(session.executionId) },
                  onEditResult = { updated ->
                    onEditResult(updated)
                    editingResultKey = null
                  })
            }
            val nextExercise =
                if (session.setNumber < it.sets()) it
                else session.workout.exercises().getOrNull(session.exerciseIndex + 1)
            if (nextExercise != null) {
              Text(
                  stringResource(R.string.Strength_session_next, nextExercise.name()),
                  color = RelvenColors.MutedText)
            }
            if (session.phase != StrengthSession.Phase.PAUSED &&
                session.phase != StrengthSession.Phase.AWAITING_RESULT &&
                session.phase != StrengthSession.Phase.PREPARING) {
              TextButton(
                  onClick = onSkipExercise,
                  enabled = !isSavingProgress,
                  modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.Strength_session_skip_exercise))
              }
            }
          }
        }
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (session.phase != StrengthSession.Phase.PAUSED &&
            session.phase != StrengthSession.Phase.PREPARING) {
          TextButton(onClick = onPause, enabled = !isSavingProgress, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.Strength_session_pause))
          }
        }
        TextButton(onClick = onClose, enabled = !isSavingProgress, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.Strength_session_exit))
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun SessionResultsSummary(
    session: StrengthSession,
    editingResultKey: String?,
    isSaving: Boolean,
    onBeginEdit: (StrengthSession.SetResult) -> Unit,
    onEditResult: (StrengthSession.SetResult) -> Unit) {
  val summary = remember(session.executionId, session.results) { strengthSessionSummary(session) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        stringResource(
            R.string.Strength_session_summary_completion,
            summary.completedSetCount,
            summary.prescribedSetCount),
        color = RelvenColors.Orange,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold)
    val otherStatuses =
        listOfNotNull(
                summary.skippedSetCount.takeIf { it > 0 }?.let {
                  stringResource(R.string.Strength_session_summary_skipped_count, it)
                },
                summary.incompleteSetCount.takeIf { it > 0 }?.let {
                  stringResource(R.string.Strength_session_summary_incomplete_count, it)
                },
                summary.unrecordedSetCount.takeIf { it > 0 }?.let {
                  stringResource(R.string.Strength_session_summary_unrecorded_count, it)
                })
            .joinToString(" · ")
    if (otherStatuses.isNotEmpty()) Text(otherStatuses, color = RelvenColors.MutedText)

    Surface(
        color = RelvenColors.Surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        summary.totalRepetitions?.let {
          SummaryMetric(
              stringResource(R.string.Strength_session_summary_total_reps),
              stringResource(R.string.Strength_session_summary_reps_count, it))
        }
        summary.totalWorkSeconds?.let {
          SummaryMetric(
              stringResource(R.string.Strength_session_summary_work_time),
              stringResource(R.string.Strength_session_summary_seconds, it))
        }
        summary.totalRestSeconds?.let {
          SummaryMetric(
              stringResource(R.string.Strength_session_summary_rest_time),
              stringResource(R.string.Strength_session_summary_seconds, it))
        }
        if (summary.totalRepetitions == null && summary.totalWorkSeconds == null && summary.totalRestSeconds == null) {
          Text(stringResource(R.string.Strength_session_unknown_result), color = RelvenColors.MutedText)
        }
      }
    }

    summary.exercises.forEach { exerciseSummary ->
      val exerciseResults = session.results.filter { it.exerciseIndex == exerciseSummary.exerciseIndex }
      Surface(
          color = RelvenColors.Surface,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(exerciseSummary.exerciseName, color = RelvenColors.Text, fontWeight = FontWeight.SemiBold)
          Text(
              stringResource(
                  R.string.Strength_session_summary_set_progress,
                  exerciseSummary.completedSetCount,
                  exerciseSummary.prescribedSetCount),
              color = RelvenColors.MutedText)
          exerciseSummary.totalRepetitions?.let {
            SummaryMetric(
                stringResource(R.string.Strength_session_summary_total_reps),
                stringResource(R.string.Strength_session_summary_reps_count, it))
          }
          exerciseSummary.bestSetRepetitions?.let {
            SummaryMetric(
                stringResource(R.string.Strength_session_summary_best_set),
                stringResource(R.string.Strength_session_summary_reps_count, it))
          }
          exerciseSummary.averageRepetitionsPerSet?.let {
            SummaryMetric(
                stringResource(R.string.Strength_session_summary_average_set),
                stringResource(R.string.Strength_session_summary_average_value, it))
          }
          exerciseSummary.totalWorkSeconds?.let {
            SummaryMetric(
                stringResource(R.string.Strength_session_summary_work_time),
                stringResource(R.string.Strength_session_summary_seconds, it))
          }
          exerciseSummary.totalRestSeconds?.let {
            SummaryMetric(
                stringResource(R.string.Strength_session_summary_rest_time),
                stringResource(R.string.Strength_session_summary_seconds, it))
          }
          if (exerciseSummary.leftRepetitions != null && exerciseSummary.rightRepetitions != null) {
            Text(
                stringResource(
                    R.string.Strength_session_summary_sides,
                    exerciseSummary.leftRepetitions,
                    exerciseSummary.rightRepetitions,
                    requireNotNull(exerciseSummary.sideDifference)),
                color = RelvenColors.MutedText)
          }
          exerciseResults.sortedBy { it.setNumber }.forEach { result ->
            val detail =
                when (result.status) {
                  StrengthSession.SetResult.ResultStatus.SKIPPED ->
                      stringResource(R.string.Strength_session_skipped)
                  StrengthSession.SetResult.ResultStatus.INCOMPLETE ->
                      stringResource(R.string.Strength_session_incomplete)
                  StrengthSession.SetResult.ResultStatus.COMPLETED ->
                      result.repetitions?.let {
                        stringResource(R.string.Strength_session_summary_reps_count, it)
                      } ?: if (result.leftRepetitions != null && result.rightRepetitions != null) {
                        "${stringResource(R.string.Strength_session_left_side)} ${result.leftRepetitions} · " +
                            "${stringResource(R.string.Strength_session_right_side)} ${result.rightRepetitions}"
                      } else {
                        result.durationSeconds?.let {
                          stringResource(R.string.Strength_session_summary_seconds, it)
                        } ?: stringResource(R.string.Strength_session_unknown_result)
                      }
                }
            RecordedSetResult(
                result = result,
                setCount = exerciseSummary.prescribedSetCount,
                detailOverride = detail,
                editing = editingResultKey == result.editIdentity(session.executionId),
                isSaving = isSaving,
                onBeginEdit = { onBeginEdit(result) },
                onEditResult = onEditResult)
            result.notes?.takeIf(String::isNotBlank)?.let {
              Text(it, color = RelvenColors.MutedText, fontSize = 14.sp)
            }
            if (result.rpe != null || result.painRating != null) {
              Text(
                  listOfNotNull(
                          result.rpe?.let { "${stringResource(R.string.Strength_session_rpe)} $it" },
                          result.painRating?.let {
                            "${stringResource(R.string.Strength_session_discomfort)} $it/10"
                          })
                      .joinToString(" · "),
                  color = RelvenColors.MutedText,
                  fontSize = 14.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, color = RelvenColors.MutedText)
    Text(value, color = RelvenColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
  }
}

@Composable
private fun RecordedSetResult(
    result: StrengthSession.SetResult,
    setCount: Int,
    editing: Boolean,
    isSaving: Boolean,
    detailOverride: String? = null,
    onBeginEdit: () -> Unit,
    onEditResult: (StrengthSession.SetResult) -> Unit) {
  val detail =
      detailOverride ?: result.repetitions?.let { stringResource(R.string.Strength_session_summary_reps_count, it) }
          ?: if (result.leftRepetitions != null && result.rightRepetitions != null) {
            "${stringResource(R.string.Strength_session_left_side)} ${result.leftRepetitions} · " +
                "${stringResource(R.string.Strength_session_right_side)} ${result.rightRepetitions}"
          } else {
            stringResource(R.string.Strength_session_unknown_result)
          }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          stringResource(R.string.Strength_session_set, result.setNumber, setCount) + " · $detail",
          color = RelvenColors.Text,
          modifier = Modifier.weight(1f))
      if (result.status == StrengthSession.SetResult.ResultStatus.COMPLETED &&
          (result.repetitions != null || result.leftRepetitions != null || result.rightRepetitions != null)) {
        TextButton(
            onClick = onBeginEdit,
            enabled = !isSaving,
            modifier = Modifier.heightIn(min = 48.dp)) {
          Text(stringResource(R.string.Strength_session_edit_result), color = RelvenColors.Orange)
        }
      }
    }
    if (editing) {
      RepetitionCountEntry(
          key = "edit:${result.exerciseIndex}:${result.setNumber}",
          initialResult = result,
          isEditing = true,
          isSaving = isSaving,
          onConfirm = { input ->
            onEditResult(
                result.copy(
                    repetitions = input.repetitions,
                    leftRepetitions = input.leftRepetitions,
                    rightRepetitions = input.rightRepetitions,
                    rpe = input.rpe,
                    painRating = input.painRating,
                    notes = input.notes,
                    status = StrengthSession.SetResult.ResultStatus.COMPLETED))
          })
    }
  }
}

private fun StrengthSession.SetResult.editIdentity(executionId: String): String =
    "$executionId:$exerciseIndex:$setNumber"

private fun StrengthSession.currentExerciseOrNull() =
    if (phase == StrengthSession.Phase.COMPLETE) null else workout.exercises().getOrNull(exerciseIndex)

@Composable
private fun RepetitionCountEntry(
    key: String,
    initialResult: StrengthSession.SetResult? = null,
    isEditing: Boolean = false,
    isSaving: Boolean,
    onConfirm: (StrengthSession.RepetitionResultInput) -> Unit) {
  var selectedCount by remember(key) { mutableStateOf(initialResult?.repetitions) }
  var manualEntryVisible by remember(key) { mutableStateOf(false) }
  var manualValue by remember(key) { mutableStateOf(initialResult?.repetitions?.toString().orEmpty()) }
  var advancedDetailsVisible by remember(key) { mutableStateOf(false) }
  var perSide by remember(key) { mutableStateOf(initialResult?.leftRepetitions != null || initialResult?.rightRepetitions != null) }
  var leftCount by remember(key) { mutableStateOf(initialResult?.leftRepetitions) }
  var rightCount by remember(key) { mutableStateOf(initialResult?.rightRepetitions) }
  var rpe by remember(key) { mutableStateOf(initialResult?.rpe?.toInt()) }
  var discomfort by remember(key) { mutableStateOf(initialResult?.painRating) }
  var notes by remember(key) { mutableStateOf(initialResult?.notes.orEmpty()) }
  val haptic = LocalHapticFeedback.current
  fun selectCount(value: Int?) {
    selectedCount = value
    if (manualEntryVisible) manualValue = value?.toString().orEmpty()
  }

  Text(stringResource(R.string.Strength_session_how_many_repetitions), color = RelvenColors.MutedText)
  if (!perSide) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
      RepetitionAdjustmentButton(
          delta = -1,
          enabled = !isSaving && selectedCount != 0,
          onAdjust = { selectCount(adjustRepetitionCount(selectedCount, -1)) })
      Text(
          selectedCount?.toString() ?: "—",
          color = RelvenColors.Text,
          fontSize = 48.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics {
            if (selectedCount != null) liveRegion = LiveRegionMode.Polite
          })
      RepetitionAdjustmentButton(
          delta = 1,
          enabled = !isSaving && selectedCount != 10_000,
          onAdjust = { selectCount(adjustRepetitionCount(selectedCount, 1)) })
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
      listOf(5, 10, 15, 20).forEach { count ->
        FilterChip(
            selected = selectedCount == count,
            onClick = { if (!isSaving) selectCount(count) },
            label = { Text(count.toString()) },
            enabled = !isSaving)
      }
    }
    TextButton(
        onClick = {
          if (!manualEntryVisible) manualValue = selectedCount?.toString().orEmpty()
          manualEntryVisible = !manualEntryVisible
        },
        enabled = !isSaving,
        modifier = Modifier.heightIn(min = 48.dp)) {
      Text(stringResource(R.string.Strength_session_enter_manually))
    }
    if (manualEntryVisible) {
      OutlinedTextField(
          value = manualValue,
          onValueChange = { candidate ->
            if (candidate.length <= 5 && candidate.all(Char::isDigit)) {
              val parsed = parseRepetitionCount(candidate)
              if (candidate.isEmpty() || parsed != null) {
                manualValue = candidate
                selectedCount = parsed
              }
            }
          },
          label = { Text(stringResource(R.string.Strength_session_repetitions)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth())
    }
  }

  TextButton(
      onClick = { advancedDetailsVisible = !advancedDetailsVisible },
      enabled = !isSaving,
      modifier = Modifier.heightIn(min = 48.dp)) {
    Text(
        stringResource(
            if (advancedDetailsVisible) R.string.Strength_session_hide_advanced_details
            else R.string.Strength_session_advanced_details))
  }
  if (advancedDetailsVisible) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
          selected = !perSide,
          onClick = { perSide = false },
          label = { Text(stringResource(R.string.Strength_session_total_count)) },
          enabled = !isSaving)
      FilterChip(
          selected = perSide,
          onClick = { perSide = true },
          label = { Text(stringResource(R.string.Strength_session_per_side)) },
          enabled = !isSaving)
    }
    if (perSide) {
      SideRepetitionCounter(
          label = stringResource(R.string.Strength_session_left_side),
          value = leftCount,
          enabled = !isSaving,
          onValueChange = { leftCount = it })
      SideRepetitionCounter(
          label = stringResource(R.string.Strength_session_right_side),
          value = rightCount,
          enabled = !isSaving,
          onValueChange = { rightCount = it })
    }
    OptionalRating(
        label = stringResource(R.string.Strength_session_rpe),
        selected = rpe,
        enabled = !isSaving,
        onSelected = { rpe = it })
    OptionalRating(
        label = stringResource(R.string.Strength_session_discomfort),
        selected = discomfort,
        enabled = !isSaving,
        onSelected = { discomfort = it })
    OutlinedTextField(
        value = notes,
        onValueChange = { if (it.length <= 1_000) notes = it },
        label = { Text(stringResource(R.string.Strength_session_optional_notes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4)
  }

  val readyToConfirm = if (perSide) leftCount != null && rightCount != null else selectedCount != null
  Button(
      onClick = {
        if (readyToConfirm) {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onConfirm(
              StrengthSession.RepetitionResultInput(
                  repetitions = if (perSide) null else selectedCount,
                  leftRepetitions = if (perSide) leftCount else null,
                  rightRepetitions = if (perSide) rightCount else null,
                  rpe = rpe?.toDouble(),
                  painRating = discomfort,
                  notes = notes.takeIf(String::isNotBlank)))
        }
      },
      enabled = !isSaving && readyToConfirm &&
          (if (perSide) leftCount in 0..10_000 && rightCount in 0..10_000
          else selectedCount in 0..10_000),
      colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
    Text(
        if (isEditing) {
          stringResource(R.string.Strength_session_save_edit)
        } else if (perSide) {
          if (readyToConfirm) {
            "${stringResource(R.string.Strength_session_log_repetitions)} · $leftCount / $rightCount"
          } else stringResource(R.string.Strength_session_choose_count)
        } else {
          selectedCount?.let { stringResource(R.string.Strength_session_log_count, it) }
              ?: stringResource(R.string.Strength_session_choose_count)
        })
  }
}

@Composable
private fun SideRepetitionCounter(
    label: String,
    value: Int?,
    enabled: Boolean,
    onValueChange: (Int?) -> Unit) {
  var manualVisible by remember(label) { mutableStateOf(false) }
  var manualValue by remember(label) { mutableStateOf("") }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = RelvenColors.MutedText)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
      RepetitionAdjustmentButton(
          delta = -1,
          enabled = enabled && value != null && value > 0,
          onAdjust = {
            val updated = adjustRepetitionCount(value, -1)
            onValueChange(updated)
            if (manualVisible) manualValue = updated.toString()
          })
      Text(value?.toString() ?: "—", color = RelvenColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
      RepetitionAdjustmentButton(
          delta = 1,
          enabled = enabled && value != 10_000,
          onAdjust = {
            val updated = adjustRepetitionCount(value, 1)
            onValueChange(updated)
            if (manualVisible) manualValue = updated.toString()
          })
    }
    TextButton(
        onClick = {
          if (!manualVisible) manualValue = value?.toString().orEmpty()
          manualVisible = !manualVisible
        },
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp)) {
      Text(stringResource(R.string.Strength_session_enter_manually))
    }
    if (manualVisible) {
      OutlinedTextField(
          value = manualValue,
          onValueChange = { candidate ->
            if (candidate.length <= 5 && candidate.all(Char::isDigit)) {
              val parsed = parseRepetitionCount(candidate)
              if (candidate.isEmpty() || parsed != null) {
                manualValue = candidate
                onValueChange(parsed)
              }
            }
          },
          label = { Text(stringResource(R.string.Strength_session_repetitions)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun OptionalRating(
    label: String,
    selected: Int?,
    enabled: Boolean,
    onSelected: (Int?) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, color = RelvenColors.MutedText, modifier = Modifier.weight(1f))
      TextButton(onClick = { onSelected(null) }, enabled = enabled && selected != null) {
        Text(stringResource(R.string.Strength_session_clear))
      }
    }
    listOf(0..5, 6..10).forEach { range ->
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        range.forEach { value ->
          FilterChip(
              selected = selected == value,
              onClick = { if (enabled) onSelected(value) },
              label = { Text(value.toString()) },
              enabled = enabled)
        }
      }
    }
  }
}

@Composable
private fun RepetitionAdjustmentButton(delta: Int, enabled: Boolean, onAdjust: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  var repeated by remember { mutableStateOf(false) }
  val description =
      stringResource(
          if (delta > 0) R.string.Strength_session_increase_count
          else R.string.Strength_session_decrease_count)

  LaunchedEffect(isPressed, enabled) {
    if (isPressed && enabled) {
      delay(400)
      if (isPressed && enabled) {
        repeated = true
        var intervalMillis = 140L
        while (isPressed && enabled) {
          onAdjust()
          delay(intervalMillis)
          intervalMillis = (intervalMillis * 0.8).toLong().coerceAtLeast(45L)
        }
      }
    } else {
      delay(80)
      repeated = false
    }
  }

  IconButton(
      onClick = {
        if (repeated) repeated = false else onAdjust()
      },
      enabled = enabled,
      interactionSource = interactionSource,
      modifier = Modifier.size(64.dp).semantics {
        contentDescription = description
      }) {
    Icon(
        imageVector = if (delta > 0) Icons.Default.Add else Icons.Default.Remove,
        contentDescription = null,
        tint = RelvenColors.Orange,
        modifier = Modifier.size(32.dp))
  }
}

internal fun adjustRepetitionCount(current: Int?, delta: Int): Int =
    ((current ?: if (delta < 0) 1 else 0) + delta).coerceIn(0, 10_000)

internal fun parseRepetitionCount(value: String): Int? =
    if (value.isEmpty()) null else value.toIntOrNull()?.takeIf { it in 0..10_000 }

internal fun shouldSignalTimedWorkFinished(
    previousPhase: StrengthSession.Phase,
    currentPhase: StrengthSession.Phase): Boolean =
    previousPhase == StrengthSession.Phase.TIMED_WORK &&
        currentPhase != StrengthSession.Phase.TIMED_WORK && currentPhase != StrengthSession.Phase.PAUSED
