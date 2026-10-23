package org.runnerup.relven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.runnerup.common.R as CommonR
import org.runnerup.util.Formatter

internal data class GpsSignalState(val label: String, val color: Color)

internal class ActiveWorkoutGpsState {
  val value = mutableStateOf(gpsSignalState(null, null, false))
}

internal fun gpsSignalState(
    accuracyMeters: Float?,
    ageMillis: Long?,
    hasCoordinates: Boolean
): GpsSignalState = when {
  !hasCoordinates || ageMillis == null || ageMillis > 15_000L ->
    GpsSignalState("Väntar på GPS", Color(0xFF94A3B8))
  accuracyMeters == null || !accuracyMeters.isFinite() ->
    GpsSignalState("GPS söker", Color(0xFFFFB547))
  accuracyMeters <= 7f -> GpsSignalState("GPS stark · ±${accuracyMeters.toInt()} m", Color(0xFF35C982))
  accuracyMeters <= 15f -> GpsSignalState("GPS okej · ±${accuracyMeters.toInt()} m", Color(0xFFFFB547))
  else -> GpsSignalState("GPS svag · ±${accuracyMeters.toInt()} m", Color(0xFFFF6B6B))
}

internal fun installActiveWorkoutScreen(
    view: ComposeView,
    metrics: RunMetricsStateHolder,
    controls: RunControlsStateHolder,
    steps: RunWorkoutStepsStateHolder,
    countdown: RunCountdownStateHolder,
    mapHost: LiveWorkoutMapHost?,
    sport: String,
    showMap: Boolean,
    gps: ActiveWorkoutGpsState,
    formatter: Formatter,
) {
  view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  view.setContent {
    CompositionLocalProvider(LocalRunWorkoutFormatter provides formatter) {
      RelvenTheme {
        ActiveWorkoutScreen(metrics, controls, steps, countdown, mapHost, sport, showMap, gps)
      }
    }
  }
}

@Composable
private fun ActiveWorkoutScreen(
    metrics: RunMetricsStateHolder,
    controls: RunControlsStateHolder,
    steps: RunWorkoutStepsStateHolder,
    countdown: RunCountdownStateHolder,
    mapHost: LiveWorkoutMapHost?,
    sport: String,
    showMap: Boolean,
    gps: ActiveWorkoutGpsState,
) {
  var mapExpanded by remember { mutableStateOf(false) }
  var confirmStop by remember { mutableStateOf(false) }
  val safe = WindowInsets.safeDrawing.asPaddingValues()
  Box(Modifier.fillMaxSize().background(RelvenColors.Background)) {
    if (showMap && mapHost != null) {
      AndroidView(factory = { mapHost.view }, modifier = Modifier.fillMaxSize())
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x110D141E), Color(0x44151C26), Color(0xAA0D141E)))))
    }
    Box(
        Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(safe.calculateTopPadding())
            .background(RelvenColors.Background.copy(alpha = 0.96f)))
    Column(
        Modifier.fillMaxSize().padding(top = safe.calculateTopPadding()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
      Column(
          Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = RelvenColors.Background.copy(alpha = 0.96f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
              Text(sport, color = RelvenColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
              Text(
                  if (controls.presentation.isPaused) "PASS PAUSAT" else "PASS PÅGÅR",
                  color = if (controls.presentation.isPaused) RelvenColors.Orange else RelvenColors.MutedText,
                  fontSize = 11.sp,
                  letterSpacing = 1.1.sp)
            }
            if (showMap) {
              TextButton(onClick = { mapExpanded = !mapExpanded }) {
                Text(if (mapExpanded) "Mätvärden" else "Karta", color = RelvenColors.Text)
              }
            }
          }
        }
        if (!mapExpanded) {
          Surface(color = RelvenColors.Surface.copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              val total = metrics.presentation.total
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryMetric("TID", total.time, Modifier.weight(1f))
                PrimaryMetric("DISTANS", total.distance, Modifier.weight(1f))
                PrimaryMetric(metrics.presentation.paceLabel.uppercase(), metrics.presentation.currentPace, Modifier.weight(1f))
              }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryMetric("Snitt", total.pace, Modifier.weight(1f))
                SecondaryMetric("Varv", metrics.presentation.lap.pace, Modifier.weight(1f))
                metrics.presentation.total.heartRate.takeIf(String::isNotBlank)?.let {
                  SecondaryMetric("Snittpuls", it, Modifier.weight(1f))
                }
                metrics.presentation.currentHeartRate?.takeIf(String::isNotBlank)?.let {
                  SecondaryMetric("Puls nu", it, Modifier.weight(1f))
                }
              }
              val interval = metrics.presentation.interval
              if (interval != null) {
                Text("INTERVALL  ${interval.time}  ·  ${interval.distance}  ·  ${interval.pace}", color = RelvenColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              metrics.presentation.heartRateNotice?.let { Text(it, color = RelvenColors.MutedText, fontSize = 12.sp) }
            }
          }
          Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Surface(color = RelvenColors.Background.copy(alpha = 0.94f), shape = CircleShape) {
                Text("●  ${gps.value.value.label}", color = gps.value.value.color, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
              }
              if (showMap && mapHost != null) {
                Button(
                    onClick = mapHost::recenter,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelvenColors.Surface, contentColor = RelvenColors.Text),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                  Text("Centrera", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
              }
            }
            if (showMap) {
              Surface(color = RelvenColors.Background.copy(alpha = 0.94f), shape = RoundedCornerShape(6.dp)) {
                Text(stringResource(CommonR.string.route_map_attribution), color = RelvenColors.Text, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
              }
            }
          }
          countdown.Content()
          if (steps.presentation.items.isNotEmpty()) {
            Surface(color = RelvenColors.Surface.copy(alpha = 0.94f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
              Column(Modifier.padding(vertical = 6.dp)) { steps.Content() }
            }
          }
        }
      }
      Column(
        Modifier.fillMaxWidth()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
          .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (controls.presentation.buttonsLocked) {
          Text("Knappar låsta · tryck snabbt två gånger för att låsa upp", color = RelvenColors.Text, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          WorkoutButton("Varv", CommonR.drawable.ic_av_newlap, RelvenColors.SurfaceRaised, Modifier.weight(1f), controls::onLap, enabled = !controls.presentation.buttonsLocked)
          WorkoutButton(if (controls.presentation.isPaused) "Fortsätt" else "Pausa", if (controls.presentation.isPaused) CommonR.drawable.ic_av_play_arrow else CommonR.drawable.ic_av_pause, if (controls.presentation.isPaused) Color(0xFF15743C) else RelvenColors.Orange, Modifier.weight(1f), controls::onTogglePause, enabled = !controls.presentation.buttonsLocked)
        }
        WorkoutButton("Avsluta pass", CommonR.drawable.ic_av_stop, Color(0xFFAB373A), Modifier.fillMaxWidth(), { confirmStop = true }, enabled = !controls.presentation.buttonsLocked)
      }
    }
  }
  if (confirmStop) {
    AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Avsluta pass?") },
        text = { Text("Du kan granska och spara passet efteråt.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; controls.onStop() }) { Text("Avsluta") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Fortsätt springa") } })
  }
}

@Composable
private fun PrimaryMetric(label: String, value: String, modifier: Modifier) {
  Column(modifier) {
    Text(label, color = RelvenColors.MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(value, color = RelvenColors.Text, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun SecondaryMetric(label: String, value: String, modifier: Modifier) {
  Column(modifier) {
    Text(label, color = RelvenColors.MutedText, fontSize = 11.sp)
    Text(value, color = RelvenColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun WorkoutButton(label: String, icon: Int, color: Color, modifier: Modifier, action: () -> Unit, enabled: Boolean = true) {
  Button(onClick = action, enabled = enabled, modifier = modifier.height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = RelvenColors.SurfaceHigh)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
      Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
  }
}
