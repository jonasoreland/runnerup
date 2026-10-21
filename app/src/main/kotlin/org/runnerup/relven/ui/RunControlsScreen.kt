package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R as CommonR

data class RunControlsPresentation(val isPaused: Boolean, val buttonsLocked: Boolean)

class RunControlsStateHolder(
  private val onLapAction: Runnable,
  private val onTogglePauseAction: Runnable,
  private val onStopAction: Runnable,
) {
  var presentation by mutableStateOf(RunControlsPresentation(isPaused = false, buttonsLocked = false))
    private set

  fun update(isPaused: Boolean, buttonsLocked: Boolean) {
    presentation = RunControlsPresentation(isPaused, buttonsLocked)
  }

  fun onLap() = onLapAction.run()

  fun onTogglePause() = onTogglePauseAction.run()

  fun onStop() = onStopAction.run()

  @Composable
  fun Content() {
    RunControls(presentation, ::onLap, ::onTogglePause, ::onStop)
  }
}

object RunControlsCompose {
  @JvmStatic
  fun install(view: ComposeView, state: RunControlsStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun RunControls(
  presentation: RunControlsPresentation,
  onLap: () -> Unit,
  onTogglePause: () -> Unit,
  onStop: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      RunControlButton(
        label = stringResource(CommonR.string.Lap),
        icon = CommonR.drawable.ic_av_newlap,
        color = RelvenColors.SurfaceRaised,
        modifier = Modifier.weight(1f),
        onClick = onLap,
      )
      RunControlButton(
        label =
          stringResource(
            if (presentation.isPaused) CommonR.string.Resume else CommonR.string.Pause,
          ),
        icon =
          if (presentation.isPaused) CommonR.drawable.ic_av_play_arrow
          else CommonR.drawable.ic_av_pause,
        color = if (presentation.isPaused) ResumeGreen else RelvenColors.Orange,
        modifier = Modifier.weight(1f),
        onClick = onTogglePause,
      )
    }
    RunControlButton(
      label = stringResource(CommonR.string.Stop),
      icon = CommonR.drawable.ic_av_stop,
      color = if (presentation.buttonsLocked) LockedStop else StopRed,
      modifier = Modifier.fillMaxWidth(),
      onClick = onStop,
    )
  }
}

@Composable
private fun RunControlButton(
  label: String,
  icon: Int,
  color: Color,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 54.dp),
    shape = RoundedCornerShape(10.dp),
    colors = ButtonDefaults.buttonColors(containerColor = color),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
      Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
  }
}

private val ResumeGreen = Color(0xFF15743C)
private val StopRed = Color(0xFFAB373A)
private val LockedStop = Color(0xFF626B78)
