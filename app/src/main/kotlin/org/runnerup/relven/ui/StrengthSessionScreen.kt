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

@Composable
internal fun StrengthSessionScreen(
    session: StrengthSession,
    completedAtMillis: Long?,
    saveError: Boolean,
    isSavingProgress: Boolean,
    isSavingCompletion: Boolean,
    onTick: () -> Unit,
    onLogRepetitions: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit
) {
  BackHandler(onBack = onClose)
  val completed = completedAtMillis != null
  val exercise = session.currentExerciseOrNull()
  val pendingResult = session.pendingResults.firstOrNull()
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
        }
      }
    }

    if (completed || session.phase == StrengthSession.Phase.COMPLETE) {
      Text(
          stringResource(R.string.Strength_session_summary_sets, session.results.size),
          color = RelvenColors.Orange,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold)
      session.results.forEach { result ->
        val detail = result.repetitions?.let { "$it ${stringResource(R.string.Strength_session_repetitions).lowercase()}" }
            ?: "${result.durationSeconds} s"
        Text("${result.exerciseName} · ${result.setNumber}: $detail", color = RelvenColors.Text)
      }
      if (completed) {
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
            enabled = !isSavingCompletion,
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
            when (session.phase) {
              StrengthSession.Phase.REPETITIONS -> {
                RepetitionCountEntry(
                    key = "${session.executionId}:${session.exerciseIndex}:${session.setNumber}",
                    isSaving = isSavingProgress,
                    onConfirm = onLogRepetitions)
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
            val nextExercise =
                if (session.setNumber < it.sets()) it
                else session.workout.exercises().getOrNull(session.exerciseIndex + 1)
            if (nextExercise != null) {
              Text(
                  stringResource(R.string.Strength_session_next, nextExercise.name()),
                  color = RelvenColors.MutedText)
            }
          }
        }
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (session.phase != StrengthSession.Phase.PAUSED) {
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

private fun StrengthSession.currentExerciseOrNull() =
    if (phase == StrengthSession.Phase.COMPLETE) null else workout.exercises().getOrNull(exerciseIndex)

@Composable
private fun RepetitionCountEntry(key: String, isSaving: Boolean, onConfirm: (Int) -> Unit) {
  var selectedCount by remember(key) { mutableStateOf<Int?>(null) }
  var manualEntryVisible by remember(key) { mutableStateOf(false) }
  var manualValue by remember(key) { mutableStateOf("") }
  val haptic = LocalHapticFeedback.current
  fun selectCount(value: Int?) {
    selectedCount = value
    if (manualEntryVisible) manualValue = value?.toString().orEmpty()
  }

  Text(stringResource(R.string.Strength_session_how_many_repetitions), color = RelvenColors.MutedText)
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
  Button(
      onClick = {
        selectedCount?.let {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onConfirm(it)
        }
      },
      enabled = !isSaving && selectedCount?.let { it in 0..10_000 } == true,
      colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
    Text(
        selectedCount?.let { stringResource(R.string.Strength_session_log_count, it) }
            ?: stringResource(R.string.Strength_session_choose_count))
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
