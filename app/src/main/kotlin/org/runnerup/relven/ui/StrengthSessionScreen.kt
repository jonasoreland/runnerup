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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
  var repetitions by remember(session.exerciseIndex, session.setNumber) {
    mutableStateOf(exercise?.let { ((it.minRepetitions() + it.maxRepetitions()) / 2).toString() }.orEmpty())
  }

  LaunchedEffect(session.phase, session.exerciseIndex, session.setNumber, session.secondsRemaining, isSavingProgress) {
    if (!completed && !isSavingProgress && (session.phase == StrengthSession.Phase.TIMED_WORK || session.phase == StrengthSession.Phase.REST)) {
      delay(1_000)
      onTick()
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
                OutlinedTextField(
                    value = repetitions,
                    onValueChange = { candidate ->
                      if (candidate.length <= 3 && candidate.all(Char::isDigit)) repetitions = candidate
                    },
                    label = { Text(stringResource(R.string.Strength_session_repetitions)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { repetitions.toIntOrNull()?.let(onLogRepetitions) },
                    enabled = !isSavingProgress && repetitions.toIntOrNull()?.let { it in 1..500 } == true,
                    colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                  Text(stringResource(R.string.Strength_session_log_repetitions))
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
