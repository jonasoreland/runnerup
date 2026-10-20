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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
  fun bindStart(view: ComposeView, state: WearControlPresentationState, startClick: Runnable) {
    view.setContent { WearStartScreen(state.startMode) { startClick.run() } }
  }

  @JvmStatic
  fun bindPauseResume(
      view: ComposeView,
      state: WearControlPresentationState,
      lapClick: Runnable,
      pauseResumeClick: Runnable
  ) {
    view.setContent {
      WearPauseResumeScreen(
          state.pauseAction,
          onLap = { lapClick.run() },
          onPauseResume = { pauseResumeClick.run() })
    }
  }
}

@Composable
internal fun WearStartScreen(mode: WearStartMode?, onStart: () -> Unit) {
  val title =
      when (mode) {
        WearStartMode.GPS_READY -> stringResource(org.runnerup.common.R.string.Start_GPS)
        WearStartMode.ACTIVITY_READY -> stringResource(org.runnerup.common.R.string.Start_activity)
        null -> stringResource(org.runnerup.common.R.string.Start)
      }

  WearControlFrame {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
      FilledIconButton(
          onClick = onStart,
          enabled = mode != null,
          modifier = Modifier.size(76.dp).semantics { contentDescription = title },
          colors = controlColors()) {
        Icon(
            painter = painterResource(org.runnerup.common.R.drawable.ic_av_play_arrow),
            contentDescription = null,
            modifier = Modifier.size(34.dp))
      }
      Text(
          text = title,
          color = if (mode == null) SecondaryText else PrimaryText,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center)
    }
  }
}

@Composable
internal fun WearPauseResumeScreen(
    action: WearPauseAction,
    onLap: () -> Unit,
    onPauseResume: () -> Unit
) {
  val pauseResumeLabel =
      stringResource(
          if (action == WearPauseAction.RESUME) org.runnerup.common.R.string.Resume
          else org.runnerup.common.R.string.Pause)
  val pauseResumeIcon =
      if (action == WearPauseAction.RESUME) org.runnerup.common.R.drawable.ic_av_play_arrow
      else org.runnerup.common.R.drawable.ic_av_pause
  val enabled = action != WearPauseAction.DISABLED

  WearControlFrame {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
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
