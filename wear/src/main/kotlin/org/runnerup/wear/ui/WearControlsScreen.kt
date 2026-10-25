package org.runnerup.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val StrengthAccent = Color(0xFFFF6A00)
private val PrimaryText = Color(0xFFF7F8FC)
private val SecondaryText = Color(0xFFAAB1BE)
private val DisabledControl = Color(0xFF303641)

object WearControlsComposeContent {
  @JvmStatic
  fun bindStart(view: ComposeView, state: WearControlPresentationState) {
    view.setContent { WearStartScreen(state.startMode) }
  }

  @JvmStatic
  fun bindPreparedWorkout(
      view: ComposeView,
      workoutName: String,
      upcomingStep: String,
      heartRateReadinessLabel: Int,
      state: WearControlPresentationState,
      startClick: Runnable) {
    view.setContent {
      WearPreparedWorkoutScreen(workoutName, upcomingStep, heartRateReadinessLabel, state) { startClick.run() }
    }
  }

  @JvmStatic
  fun bindPreparedStrengthWorkout(
      view: ComposeView,
      workoutName: String,
      exerciseName: String,
      prescription: String,
      instructions: String,
      startClick: Runnable) {
    view.setContent {
      WearPreparedStrengthWorkoutScreen(
          workoutName, exerciseName, prescription, instructions) { startClick.run() }
    }
  }

  @JvmStatic
  fun bindActiveStrengthWorkout(
      view: ComposeView,
      state: WearStrengthWorkoutUiState,
      startClick: Runnable,
      logClick: java.util.function.IntPredicate,
      pauseClick: Runnable,
      resumeClick: Runnable,
      skipPendingClick: Runnable,
      skipExerciseClick: Runnable,
      skipRestClick: Runnable,
      finishClick: Runnable) {
    view.setContent {
      val presentation = WearStrengthWorkoutPresentationFactory.from(state.session)
      WearActiveStrengthWorkoutScreen(
          presentation,
          startClick = { startClick.run() },
          logClick = { logClick.test(it) },
          pauseClick = { pauseClick.run() },
          resumeClick = { resumeClick.run() },
          skipPendingClick = { skipPendingClick.run() },
          skipExerciseClick = { skipExerciseClick.run() },
          skipRestClick = { skipRestClick.run() },
          finishClick = { finishClick.run() })
    }
  }

  @JvmStatic
  fun bindPauseResume(
      view: ComposeView,
      state: WearControlPresentationState,
      lapClick: Runnable,
      skipClick: Runnable,
      pauseResumeClick: Runnable,
      finishClick: Runnable
  ) {
    view.setContent {
      WearPauseResumeScreen(
          state,
          onLap = { lapClick.run() },
          onSkip = { skipClick.run() },
          onPauseResume = { pauseResumeClick.run() },
          onFinish = { finishClick.run() })
    }
  }
}

@Composable
internal fun WearPreparedStrengthWorkoutScreen(
    workoutName: String,
    exerciseName: String,
    prescription: String,
    instructions: String,
    onStart: () -> Unit) {
  WearControlFrame {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 12.dp)) {
      Text("STYRKEPASS", color = StrengthAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      Text("REDO ATT STARTA", color = PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      Text(workoutName, color = SecondaryText, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
      Text(exerciseName, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
      Text(prescription, color = StrengthAccent, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
      Text(instructions, color = SecondaryText, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 3)
      FilledIconButton(onClick = onStart, modifier = Modifier.size(50.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = StrengthAccent, contentColor = ScreenBackground)) {
        Icon(painterResource(org.runnerup.common.R.drawable.ic_av_play_arrow), null)
      }
      Text("STARTA PASS", color = PrimaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
internal fun WearActiveStrengthWorkoutScreen(
    state: WearStrengthWorkoutPresentation,
    startClick: () -> Unit,
    logClick: (Int) -> Boolean,
    pauseClick: () -> Unit,
    resumeClick: () -> Unit,
    skipPendingClick: () -> Unit,
    skipExerciseClick: () -> Unit,
    skipRestClick: () -> Unit,
    finishClick: () -> Unit) {
  val resultKey =
      state.pendingResultExerciseName?.let {
        "$it:${state.pendingResultSetNumber}"
      } ?: "${state.exerciseName}:${state.setNumber}"
  val repCount = remember(resultKey) { mutableIntStateOf(0) }
  var submissionLocked by remember(resultKey) { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  var finishConfirmationRequested by remember(state.workoutName) { mutableStateOf(false) }
  WearControlFrame {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
      Text(state.workoutName.uppercase(), color = StrengthAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      if (state.complete) {
        Text("PASS KLART", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("${state.completedSetCount} set loggade", color = SecondaryText, fontSize = 10.sp)
      } else {
        Text("${state.exerciseIndex + 1}/${state.exerciseCount} · ${state.phaseLabel.uppercase()}", color = SecondaryText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(state.exerciseName, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        when {
          state.phaseLabel == "Pausat" -> Text("PAUSAT", color = StrengthAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
          state.secondsRemaining > 0 -> Text(formatWatchTime(state.secondsRemaining), color = StrengthAccent, fontSize = 28.sp, fontWeight = FontWeight.Black)
          else -> Text("${state.setNumber} / ${state.setCount} set", color = StrengthAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (state.pendingResultExerciseName != null || state.manualResultExpected) {
          Text(
              if (state.pendingResultExerciseName != null) "${state.pendingResultExerciseName} · set ${state.pendingResultSetNumber}"
              else "Set ${state.setNumber} av ${state.setCount}",
              color = PrimaryText, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
          Text("REPS?", color = SecondaryText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(StrengthAccent)
                        .combinedClickable(
                            onClick = {
                              repCount.intValue = (repCount.intValue - 1).coerceAtLeast(0)
                              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onLongClick = {
                              repCount.intValue = (repCount.intValue - 10).coerceAtLeast(0)
                              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            })
                        .semantics { contentDescription = "Minska repetitioner" },
                contentAlignment = Alignment.Center) { Text("−", color = ScreenBackground, fontSize = 20.sp) }
            Text(repCount.intValue.toString(), color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.semantics { contentDescription = "${repCount.intValue} repetitioner" })
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(StrengthAccent)
                        .combinedClickable(
                            onClick = {
                              repCount.intValue = (repCount.intValue + 1).coerceAtMost(10_000)
                              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onLongClick = {
                              repCount.intValue = (repCount.intValue + 10).coerceAtMost(10_000)
                              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            })
                        .semantics { contentDescription = "Öka repetitioner" },
                contentAlignment = Alignment.Center) { Text("+", color = ScreenBackground, fontSize = 20.sp) }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(5, 10, 15, 20).forEach { value ->
              TextButton(onClick = { repCount.intValue = value; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) { Text(value.toString(), fontSize = 9.sp) }
            }
          }
          Button(
              onClick = {
                if (!submissionLocked && logClick(repCount.intValue)) {
                  submissionLocked = true
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
              },
              enabled = !submissionLocked,
              colors = strengthButtonColors(),
              modifier = Modifier.semantics { contentDescription = "Logga ${repCount.intValue} repetitioner" }) {
            Text(if (submissionLocked) "SPARAT" else "LOGGA ${repCount.intValue}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
          if (state.pendingResultExerciseName != null) {
            TextButton(onClick = skipPendingClick) { Text("Hoppa över resultat", fontSize = 8.sp) }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
          when {
            state.canStart -> Button(onClick = startClick, colors = strengthButtonColors()) { Text("STARTA") }
            state.canResume -> Button(onClick = resumeClick, colors = strengthButtonColors()) { Text("FORTSÄTT") }
            state.canPause -> Button(onClick = pauseClick, colors = strengthButtonColors()) { Text("PAUS") }
          }
          if (state.secondsRemaining > 0 && state.phaseLabel.startsWith("Vila")) {
            TextButton(onClick = skipRestClick) { Text("SKIPPA VILA", fontSize = 8.sp) }
          } else if (state.canSkip) {
            TextButton(onClick = skipExerciseClick) { Text("NÄSTA", fontSize = 8.sp) }
          }
        }
        if (finishConfirmationRequested && state.canFinish) {
          Text("Avsluta passet nu?", color = PrimaryText, fontSize = 9.sp, textAlign = TextAlign.Center)
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TextButton(onClick = { finishConfirmationRequested = false }) { Text("FORTSÄTT", fontSize = 8.sp) }
            Button(onClick = { finishConfirmationRequested = false; finishClick() }, colors = strengthButtonColors()) { Text("AVSLUTA", fontSize = 8.sp) }
          }
        } else if (state.canFinish) {
          TextButton(onClick = { finishConfirmationRequested = true }) { Text("AVSLUTA PASS", fontSize = 8.sp) }
        }
        Text("${state.completedSetCount} set klara", color = SecondaryText, fontSize = 8.sp)
      }
    }
  }
}

private fun formatWatchTime(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun strengthButtonColors() =
    ButtonDefaults.buttonColors(containerColor = StrengthAccent, contentColor = ScreenBackground)

@Composable
internal fun WearPreparedWorkoutScreen(
    workoutName: String,
    upcomingStep: String,
    heartRateReadinessLabel: Int,
    state: WearControlPresentationState,
    onStart: () -> Unit) {
  WearControlFrame {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("REDO ATT STARTA", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      Text(workoutName, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
      Text("Nästa: $upcomingStep", color = SecondaryText, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
      Text(
          stringResource(heartRateReadinessLabel),
          color = SecondaryText,
          fontSize = 9.sp,
          textAlign = TextAlign.Center,
          maxLines = 1)
      when {
        state.preparedCountdownSeconds != null ->
            Text(
                text = if (state.preparedCountdownSeconds == 0) "KÖR" else state.preparedCountdownSeconds.toString(),
                color = AccentBlue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center)
        state.preparedStartPending ->
            Text(
                "Väntar på mobilen…",
                color = SecondaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.Center)
        else -> {
          FilledIconButton(
              onClick = onStart,
              modifier = Modifier.size(60.dp).semantics { contentDescription = "Starta pass" },
              colors = controlColors()) {
            Icon(
                painter = painterResource(org.runnerup.common.R.drawable.ic_av_play_arrow),
                contentDescription = null,
                modifier = Modifier.size(30.dp))
          }
          Text("STARTA PASS", color = PrimaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

@Composable
internal fun WearStartScreen(mode: WearStartMode?) {
  WearControlFrame {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          text = if (mode == null) "ANSLUTER TILL RELVEN" else "INGET PASS FÖRBERETT",
          color = if (mode == null) SecondaryText else PrimaryText,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center)
      Text(
          text = "Öppna Relven på mobilen för att välja ett träningspass.",
          color = SecondaryText,
          fontSize = 10.sp,
          textAlign = TextAlign.Center)
    }
  }
}

@Composable
internal fun WearPauseResumeScreen(
    state: WearControlPresentationState,
    onLap: () -> Unit,
    onSkip: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit
) {
  val action = state.pauseAction
  val pauseResumeLabel =
      stringResource(
          if (action == WearPauseAction.RESUME) org.runnerup.common.R.string.Resume
          else org.runnerup.common.R.string.Pause)
  val pauseResumeIcon =
      if (action == WearPauseAction.RESUME) org.runnerup.common.R.drawable.ic_av_play_arrow
      else org.runnerup.common.R.drawable.ic_av_pause
  val enabled = action != WearPauseAction.DISABLED

  WearControlFrame {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically) {
        WearControlButton(
            label = stringResource(org.runnerup.common.R.string.Lap),
            icon = org.runnerup.common.R.drawable.ic_av_newlap,
            enabled = enabled,
            onClick = onLap)
        WearControlButton(
            label = pauseResumeLabel,
            icon = pauseResumeIcon,
            enabled = enabled,
            onClick = onPauseResume)
      }
      if (state.skipSupported) {
        TextButton(
            onClick = onSkip,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Hoppa över steg" }) {
          Text(
              stringResource(org.runnerup.common.R.string.Wear_skip_step),
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold)
        }
      }
      if (state.finishConfirmationRequested) {
        Text(
            text = stringResource(org.runnerup.common.R.string.Wear_finish_confirmation),
            color = PrimaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = state::cancelFinish, enabled = enabled) {
            Text(stringResource(org.runnerup.common.R.string.Wear_keep_workout), fontSize = 10.sp)
          }
          Button(
              onClick = { if (state.confirmFinish()) onFinish() },
              enabled = enabled,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) {
            Text(stringResource(org.runnerup.common.R.string.Wear_finish_workout), fontSize = 10.sp)
          }
        }
      } else {
        TextButton(
            onClick = state::requestFinish,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Avsluta pass" }) {
          Text(stringResource(org.runnerup.common.R.string.Wear_finish), fontSize = 10.sp)
        }
      }
    }
  }
}

@Composable
private fun WearControlButton(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(64.dp).semantics { contentDescription = label },
        colors = controlColors()) {
      Icon(
          painter = painterResource(icon),
          contentDescription = null,
          modifier = Modifier.size(28.dp))
    }
    Text(
        text = label,
        color = if (enabled) PrimaryText else SecondaryText,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center)
  }
}

@Composable
private fun controlColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = AccentBlue,
        contentColor = ScreenBackground,
        disabledContainerColor = DisabledControl,
        disabledContentColor = SecondaryText)

@Composable
private fun WearControlFrame(content: @Composable () -> Unit) {
  Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape).background(ScreenBackground),
      contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      drawCircle(color = AccentBlue, style = Stroke(width = 2.dp.toPx()))
    }
    content()
  }
}
