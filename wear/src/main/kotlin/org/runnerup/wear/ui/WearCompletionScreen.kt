package org.runnerup.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val PrimaryText = Color(0xFFF7F8FC)

object WearCompletionComposeContent {
  @JvmStatic
  fun bind(view: ComposeView) {
    view.setContent { WearCompletionScreen() }
  }
}

@Composable
internal fun WearCompletionScreen() {
  Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape).background(ScreenBackground),
      contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      drawCircle(color = AccentBlue, style = Stroke(width = 2.dp.toPx()))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compact = maxWidth < 200.dp || maxHeight < 200.dp
      Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center) {
        PhoneSaveMark(Modifier.size(if (compact) 54.dp else 64.dp))
        Text(
            text = stringResource(org.runnerup.common.R.string.Saving_on_phone),
            color = PrimaryText,
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(top = if (compact) 8.dp else 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
      }
    }
  }
}

@Composable
private fun PhoneSaveMark(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    drawCircle(color = AccentBlue)
    val phoneWidth = size.width * 0.34f
    val phoneHeight = size.height * 0.62f
    val phoneLeft = (size.width - phoneWidth) / 2f
    val phoneTop = (size.height - phoneHeight) / 2f
    val corner = CornerRadius(size.width * 0.06f)
    drawRoundRect(
        color = ScreenBackground,
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = corner)
    drawRoundRect(
        color = PrimaryText,
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = corner,
        style = Stroke(width = size.width * 0.035f))
    drawLine(
        color = PrimaryText,
        start = Offset(phoneLeft + phoneWidth * 0.28f, phoneTop + phoneHeight * 0.16f),
        end = Offset(phoneLeft + phoneWidth * 0.72f, phoneTop + phoneHeight * 0.16f),
        strokeWidth = size.width * 0.025f)
    drawCircle(
        color = PrimaryText,
        radius = size.width * 0.025f,
        center = Offset(phoneLeft + phoneWidth / 2f, phoneTop + phoneHeight * 0.86f))
  }
}
