package org.runnerup.relven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.runnerup.common.R
import org.runnerup.BuildConfig
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

@Composable
internal fun ActivityDetailScreen(
    loadState: ActivityDetailLoadState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMapAndDetails: (Long) -> Unit) {
  BackHandler(onBack = onBack)
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(RelvenColors.Background)
              .statusBarsPadding()
              .navigationBarsPadding()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp)
              .padding(top = 12.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.Home_activity_detail_back),
            tint = RelvenColors.Orange)
      }
      Text(
          stringResource(R.string.Home_activity_detail_title),
          fontSize = 24.sp,
          lineHeight = 30.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics { heading() })
    }

    when (loadState) {
      ActivityDetailLoadState.Loading -> ActivityDetailMessage(loading = true)
      ActivityDetailLoadState.NotFound ->
          ActivityDetailMessage(
              message = stringResource(R.string.Home_activity_detail_not_found),
              onBack = onBack)
      ActivityDetailLoadState.Unavailable ->
          ActivityDetailMessage(
              message = stringResource(R.string.Home_activity_detail_unavailable),
              onBack = onBack,
              onRetry = onRetry)
      is ActivityDetailLoadState.Loaded ->
          ActivityDetailContent(
              loadState.activity,
              onOpenMapAndDetails = { onOpenMapAndDetails(loadState.activity.id) })
    }
  }
}

@Composable
private fun ActivityDetailContent(
    activity: HistoryActivityDetails,
    onOpenMapAndDetails: () -> Unit) {
  val context = LocalContext.current
  val formatter = remember(context, LocalConfiguration.current) { Formatter(context) }
  val sport = Sport.textOf(LocalResources.current, activity.sport)
  val date =
      remember(activity.startedAtSeconds, LocalConfiguration.current) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(activity.startedAtSeconds * 1000L))
      }
  val measurements = activityDetailMeasurements(activity)
  val hasMapRoute = activityHasMapRoute(
      activity, mapEnabled = BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED)
  val metrics = buildList {
    measurements.distanceMeters?.let {
      add(
          stringResource(R.string.Home_activity_detail_distance) to
              formatter.formatDistance(Formatter.Format.TXT_LONG, it.toLong()))
    }
    measurements.durationSeconds?.let {
      add(
          stringResource(R.string.Home_activity_detail_duration) to
              formatter.formatElapsedTime(Formatter.Format.TXT_LONG, it))
    }
    measurements.averageSpeedMetersPerSecond?.let {
      add(
          formatter.formatVelocityLabel() to
              formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, it))
    }
    measurements.averageHeartRate?.let {
      add(stringResource(R.string.Home_activity_detail_average_hr) to heartRateLabel(it))
    }
    measurements.maximumHeartRate?.let {
      add(stringResource(R.string.Home_activity_detail_maximum_hr) to heartRateLabel(it))
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(sport, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
      Text(date, fontSize = 14.sp, color = RelvenColors.MutedText)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      metrics.chunked(2).forEach { rowMetrics ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          rowMetrics.forEach { (label, value) ->
            ActivityMetric(label, value, Modifier.weight(1f))
          }
          if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
        }
      }
    }

    activity.comment?.trim()?.takeIf(String::isNotEmpty)?.let { note ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.Home_activity_detail_note),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(16.dp)) {
          Text(note, modifier = Modifier.fillMaxWidth().padding(16.dp), lineHeight = 22.sp)
        }
      }
    }

    Button(
        onClick = onOpenMapAndDetails,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
      if (hasMapRoute) {
        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
      }
      Text(
          stringResource(
              if (!hasMapRoute) R.string.Home_activity_detail_open_full
              else R.string.Home_activity_detail_open_map),
          color = RelvenColors.Background,
          fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun ActivityMetric(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier.heightIn(min = 92.dp),
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(16.dp)) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          label,
          fontSize = 12.sp,
          color = RelvenColors.MutedText,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis)
      Text(
          value,
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun ActivityDetailMessage(
    message: String? = null,
    loading: Boolean = false,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null) {
  Column(
      modifier =
          Modifier.fillMaxWidth().padding(vertical = 28.dp).semantics {
            liveRegion = LiveRegionMode.Polite
          },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (loading) {
      CircularProgressIndicator(color = RelvenColors.Orange, modifier = Modifier.size(28.dp))
      Text(stringResource(R.string.Home_activity_detail_loading), color = RelvenColors.MutedText)
    } else {
      message?.let { Text(it, color = RelvenColors.MutedText) }
      if (onRetry != null) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.Home_history_retry)) }
      }
      if (onBack != null) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.Home_activity_detail_back)) }
      }
    }
  }
}

@Composable
private fun heartRateLabel(beatsPerMinute: Int): String =
    stringResource(R.string.Home_activity_detail_bpm, beatsPerMinute)
