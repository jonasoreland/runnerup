package org.runnerup.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val PrimaryText = Color(0xFFF7F8FC)
private val SecondaryText = Color(0xFFAAB1BE)

class WearWorkoutPresentationState {
  internal var display by mutableStateOf(wearWorkoutDisplay(emptyMap(), emptyMap(), 0, 0, false))
    private set

  fun update(
      headers: Map<String, String>,
      data: Map<String, String>,
      screen: Int,
      rowCount: Int,
      isPaused: Boolean) {
    display = wearWorkoutDisplay(headers, data, screen, rowCount, isPaused)
  }
}

object WearWorkoutComposeContent {
  @JvmStatic
  fun bind(view: ComposeView, state: WearWorkoutPresentationState) {
    view.setContent { WearWorkoutScreen(state.display) }
  }
}

@Composable
internal fun WearWorkoutScreen(display: WearWorkoutDisplay) {
  Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape),
      contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      drawCircle(color = AccentBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
    Column(
        modifier = Modifier.fillMaxWidth(0.76f).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val phase = display.phaseLabel
      if (phase != null || display.isPaused) {
        Surface(color = Color(0xFF202633), shape = RoundedCornerShape(50)) {
          Text(
              text =
                  listOfNotNull(
                          phase,
                          if (display.isPaused)
                              stringResource(org.runnerup.common.R.string.Activity_paused)
                          else null)
                      .joinToString(" · "),
              color = AccentBlue,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
      }
      val primary = display.metrics.firstOrNull()
      if (primary != null) {
        Text(
            text = primary.value,
            color = PrimaryText,
            fontSize =
                when {
                  primary.value.length > 8 -> 20.sp
                  primary.value.length > 6 -> 27.sp
                  else -> 36.sp
                },
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(
            text = primary.label,
            color = SecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
      val secondary = display.metrics.drop(1)
      if (secondary.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
          secondary.forEach { metric ->
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                  text = metric.value,
                  color = PrimaryText,
                  fontSize = if (metric.value.length > 7) 12.sp else 15.sp,
                  lineHeight = 18.sp,
                  fontWeight = FontWeight.SemiBold,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)
              Text(
                  text = metric.label,
                  color = SecondaryText,
                  fontSize = 8.sp,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}
