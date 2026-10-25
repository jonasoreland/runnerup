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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel
import org.runnerup.service.WearConnectionStatus

private val ScreenBackground = Color(0xFF03050B)
private val AccentBlue = Color(0xFF9BCBFF)
private val PrimaryText = Color(0xFFF7F8FC)
private val SecondaryText = Color(0xFFAAB1BE)

object WearConnectWaitingComposeContent {
  @JvmStatic
  fun bind(
      view: ComposeView,
      workoutName: String? = null,
      upcomingStep: String? = null,
      trackerState: TrackerState? = null,
      connectionStatus: ValueModel<WearConnectionStatus>? = null,
      onRetry: Runnable = Runnable {}
  ) {
    view.setContent {
      WearConnectWaitingScreen(
          workoutName, upcomingStep, trackerState, connectionStatus, onRetry::run)
    }
  }
}

internal fun preparedWorkoutConnectionStatus(trackerState: TrackerState?): String =
    when (trackerState) {
      TrackerState.CONNECTING -> "GPS ansluter · sensorer ej kontrollerade"
      TrackerState.CONNECTED -> "GPS ansluten · övriga sensorer ej kontrollerade"
      TrackerState.INITIALIZED -> "Telefon ansluten · GPS ej startad"
      TrackerState.ERROR -> "Trackerfel · sensorer ej kontrollerade"
      TrackerState.CLEANUP -> "Tracker återansluter · sensorer ej kontrollerade"
      else -> "Väntar på telefonen · sensorer ej kontrollerade"
    }

internal fun wearTransportStatusResourceId(status: WearConnectionStatus): Int =
    when (status) {
      WearConnectionStatus.CONNECTING -> org.runnerup.common.R.string.Wear_connection_connecting
      WearConnectionStatus.CONNECTED -> org.runnerup.common.R.string.Wear_connection_connected
      WearConnectionStatus.DISCONNECTED -> org.runnerup.common.R.string.Wear_connection_disconnected
    }

internal fun isCompactWearConnectLayout(widthDp: Float, heightDp: Float): Boolean =
    widthDp < 220f || heightDp < 220f

@Composable
internal fun WearConnectWaitingScreen(
    workoutName: String?,
    upcomingStep: String?,
    trackerState: TrackerState? = null,
    connectionStatus: ValueModel<WearConnectionStatus>? = null,
    onRetry: () -> Unit = {}
) {
  var transportStatus by
      remember(connectionStatus) {
        mutableStateOf(connectionStatus?.get() ?: WearConnectionStatus.CONNECTING)
      }
  DisposableEffect(connectionStatus) {
    val listener =
        ValueModel.ChangeListener<WearConnectionStatus> { _, _, newValue ->
          transportStatus = newValue ?: WearConnectionStatus.DISCONNECTED
        }
    connectionStatus?.registerChangeListener(listener)
    transportStatus = connectionStatus?.get() ?: WearConnectionStatus.CONNECTING
    onDispose { connectionStatus?.unregisterChangeListener(listener) }
  }
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
            text = if (workoutName == null) stringResource(org.runnerup.common.R.string.Waiting_for_phone) else "PASS MOTTAGET",
            modifier = Modifier.padding(top = if (compact) 7.dp else 10.dp),
            color = PrimaryText,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text(
            text = stringResource(wearTransportStatusResourceId(transportStatus)),
            modifier = Modifier.padding(top = if (compact) 3.dp else 5.dp),
            color = SecondaryText,
            fontSize = if (compact) 7.sp else 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (workoutName != null) {
          Text(
              text = workoutName,
              modifier = Modifier.padding(top = 4.dp),
              color = PrimaryText,
              fontSize = if (compact) 11.sp else 13.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis)
          Text(
              text = "Nästa: ${upcomingStep.orEmpty()}",
              modifier = Modifier.padding(top = 3.dp),
              color = AccentBlue,
              fontSize = if (compact) 9.sp else 11.sp,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis)
          Text(
              text = "Starta passet på mobilen när du är redo",
              modifier = Modifier.padding(top = 3.dp),
              color = SecondaryText,
              fontSize = if (compact) 8.sp else 10.sp,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis)
          Text(
              text = preparedWorkoutConnectionStatus(trackerState),
              modifier = Modifier.padding(top = 3.dp),
              color = SecondaryText,
              fontSize = if (compact) 7.sp else 9.sp,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = if (compact) 5.dp else 8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentBlue, contentColor = ScreenBackground)) {
          Text(
              text = stringResource(org.runnerup.common.R.string.Wear_retry_connection),
              fontSize = if (compact) 8.sp else 10.sp,
              maxLines = 1,
              overflow = TextOverflow.Clip)
        }
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
