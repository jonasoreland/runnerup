package org.runnerup.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.hr.HeartRatePermissionState

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val PrimaryText = Color(0xFFF7F8FC)
private val SecondaryText = Color(0xFFAAB1BE)

object WearHeartRatePermissionComposeContent {
  @JvmStatic
  fun bind(
      view: ComposeView,
      state: HeartRatePermissionState.State,
      sdkInt: Int,
      onAction: Runnable
  ) {
    val presentation = wearHeartRatePermissionPresentation(state, sdkInt)
    view.setContent { WearHeartRatePermissionScreen(presentation) { onAction.run() } }
  }
}

@Composable
internal fun WearHeartRatePermissionScreen(
    presentation: WearHeartRatePermissionPresentation, onAction: () -> Unit) {
  WearPermissionFrame {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compact = isCompactWearPermissionLayout(maxWidth.value, maxHeight.value)
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(
                      horizontal = if (compact) 18.dp else 22.dp,
                      vertical = if (compact) 10.dp else 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(presentation.titleResource),
            color = PrimaryText,
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(
            text = stringResource(presentation.detailsResource),
            color = SecondaryText,
            fontSize = if (compact) 9.sp else 10.sp,
            lineHeight = if (compact) 11.sp else 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (compact) 5.dp else 7.dp))
        presentation.actionResource?.let { actionResource ->
          Button(
              onClick = onAction,
              modifier = Modifier.padding(top = if (compact) 6.dp else 9.dp),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = AccentBlue, contentColor = ScreenBackground)) {
            Text(
                text = stringResource(actionResource),
                fontSize = if (compact) 9.sp else 10.sp,
                lineHeight = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            }
      }
    }
  }
}

}

@Composable
private fun WearPermissionFrame(content: @Composable () -> Unit) {
  Box(
      modifier = Modifier.fillMaxSize().clip(CircleShape).background(ScreenBackground),
      contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      drawCircle(color = AccentBlue, style = Stroke(width = 2.dp.toPx()))
    }
    content()
  }
}
