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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val PrimaryText = Color(0xFFF7F8FC)

object WearConnectWaitingComposeContent {
  @JvmStatic
  fun bind(view: ComposeView) {
    view.setContent { WearConnectWaitingScreen() }
  }
}

internal fun isCompactWearConnectLayout(widthDp: Float, heightDp: Float): Boolean =
    widthDp < 220f || heightDp < 220f

@Composable
internal fun WearConnectWaitingScreen() {
  Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape).background(ScreenBackground),
      contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      drawCircle(color = AccentBlue, style = Stroke(width = 2.dp.toPx()))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compact = isCompactWearConnectLayout(maxWidth.value, maxHeight.value)
      Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 24.dp else 36.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center) {
        Text(
            text = "RELVEN",
            color = AccentBlue,
            fontSize = if (compact) 10.sp else 12.sp,
            letterSpacing = if (compact) 2.sp else 3.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip)
        PhoneGlyph(Modifier.padding(top = if (compact) 8.dp else 12.dp))
        Text(
            text = stringResource(org.runnerup.common.R.string.Waiting_for_phone),
            modifier = Modifier.padding(top = if (compact) 7.dp else 10.dp),
            color = PrimaryText,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun PhoneGlyph(modifier: Modifier = Modifier) {
  Canvas(modifier.size(42.dp, 56.dp)) {
    val stroke = 2.5.dp.toPx()
    val inset = 3.dp.toPx()
    drawRoundRect(
        color = AccentBlue,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
        style = Stroke(width = stroke))
    drawLine(
        color = AccentBlue,
        start = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.19f),
        end = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.19f),
        strokeWidth = stroke)
    drawCircle(
        color = AccentBlue,
        radius = 1.8.dp.toPx(),
        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.84f))
  }
}
