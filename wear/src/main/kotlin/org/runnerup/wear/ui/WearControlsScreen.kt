package org.runnerup.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
