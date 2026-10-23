package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import org.runnerup.common.R as CommonR

data class RunMetricValues(
    val time: String,
    val distance: String,
    val pace: String,
    val heartRate: String
)

data class RunMetricsPresentation(
    val total: RunMetricValues,
    val lap: RunMetricValues,
    val interval: RunMetricValues?,
    val currentPace: String,
    val currentHeartRate: String?,
    val paceLabel: String,
    val heartRateNotice: String?
)

/** Do not present near-zero GPS drift as a plausible current running pace. */
internal fun currentPaceDisplay(speedMetersPerSecond: Double, formattedPace: String): String =
    if (speedMetersPerSecond.isFinite() && speedMetersPerSecond in 0.5..12.0) formattedPace else "--:--"

fun runMetricsPresentation(
    total: RunMetricValues,
    lap: RunMetricValues,
    interval: RunMetricValues,
    currentPace: String,
    currentHeartRate: String,
    paceLabel: String,
    showInterval: Boolean,
    heartRateAvailable: Boolean,
    heartRateNotice: String?
): RunMetricsPresentation =
    RunMetricsPresentation(
        total = total.copy(heartRate = total.heartRate.takeIf { heartRateAvailable }.orEmpty()),
        lap = lap.copy(heartRate = lap.heartRate.takeIf { heartRateAvailable }.orEmpty()),
        interval = interval.takeIf { showInterval }?.let {
          it.copy(heartRate = it.heartRate.takeIf { heartRateAvailable }.orEmpty())
        },
        currentPace = currentPace,
        currentHeartRate = currentHeartRate.takeIf { heartRateAvailable },
        paceLabel = paceLabel,
        heartRateNotice = heartRateNotice.takeIf { !heartRateAvailable })

class RunMetricsStateHolder {
  var presentation by mutableStateOf(
      RunMetricsPresentation(
          total = RunMetricValues("", "", "", ""),
          lap = RunMetricValues("", "", "", ""),
          interval = null,
          currentPace = "",
          currentHeartRate = null,
          paceLabel = "",
          heartRateNotice = null))
    private set

  fun update(value: RunMetricsPresentation) {
    presentation = value
  }

  @Composable
  fun Content() {
    RunMetricsScreen(presentation)
  }
}

object RunMetricsCompose {
  @JvmStatic
  fun install(view: ComposeView, state: RunMetricsStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun RunMetricsScreen(presentation: RunMetricsPresentation) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp)) {
    MetricSection(
        title = stringResource(CommonR.string.Total),
        values = presentation.total,
        paceLabel = presentation.paceLabel)
    presentation.heartRateNotice?.let { notice ->
      Text(
          text = notice,
          color = RelvenColors.MutedText,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp))
    }
    presentation.interval?.let {
      MetricSection(
          title = stringResource(CommonR.string.Interval),
          values = it,
          paceLabel = presentation.paceLabel)
    }
    MetricSection(
        title = stringResource(CommonR.string.Lap),
        values = presentation.lap,
        paceLabel = presentation.paceLabel)
    MetricSection(
        title = stringResource(CommonR.string.Current),
        values = RunMetricValues("", "", presentation.currentPace, presentation.currentHeartRate.orEmpty()),
        paceLabel = presentation.paceLabel,
        currentOnly = true)
  }
}

@Composable
private fun MetricSection(
    title: String,
    values: RunMetricValues,
    paceLabel: String,
    currentOnly: Boolean = false
) {
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
      Text(
          text = title,
          color = RelvenColors.Text,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis)
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (!currentOnly) {
          MetricCell(stringResource(CommonR.string.time), values.time, Modifier.weight(1f))
          MetricCell(stringResource(CommonR.string.distance), values.distance, Modifier.weight(1f))
        }
        MetricCell(paceLabel, values.pace, Modifier.weight(1.15f))
        if (values.heartRate.isNotEmpty()) {
          MetricCell(
              stringResource(CommonR.string.metrics_heartrate),
              values.heartRate,
              Modifier.weight(0.8f))
        }
      }
    }
  }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier) {
  Column(modifier = modifier.padding(start = 3.dp)) {
    Text(
        text = label,
        color = RelvenColors.MutedText,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    Text(
        text = value,
        color = RelvenColors.Text,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
  }
}
