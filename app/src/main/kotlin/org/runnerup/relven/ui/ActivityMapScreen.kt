package org.runnerup.relven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.runnerup.common.R
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

internal sealed interface ActivityMapScreenState {
  data object Loading : ActivityMapScreenState

  data object NotFound : ActivityMapScreenState

  data object RouteUnavailable : ActivityMapScreenState

  data object Unavailable : ActivityMapScreenState

  data class Loaded(val activity: HistoryActivityDetails) : ActivityMapScreenState
}

@Composable
internal fun ActivityMapScreen(
    state: ActivityMapScreenState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenFullDetails: (Long) -> Unit,
    activityId: Long,
    mapContent: @Composable (Modifier) -> Unit) {
  BackHandler(onBack = onBack)
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(RelvenColors.Background)
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(horizontal = 18.dp)
              .padding(top = 8.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.Home_activity_detail_back),
            tint = RelvenColors.Orange)
      }
      Text(
          stringResource(R.string.Activity_map_title),
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics { heading() })
    }

    when (state) {
      ActivityMapScreenState.Loading -> ActivityMapMessage(loading = true)
      ActivityMapScreenState.NotFound ->
          ActivityMapMessage(
              message = stringResource(R.string.Home_activity_detail_not_found), onBack = onBack)
      ActivityMapScreenState.RouteUnavailable ->
          ActivityMapMessage(
              message = stringResource(R.string.Activity_map_no_route),
              onBack = onBack,
              onOpenFullDetails = { onOpenFullDetails(activityId) })
      ActivityMapScreenState.Unavailable ->
          ActivityMapMessage(
              message = stringResource(R.string.Activity_map_unavailable),
              onBack = onBack,
              onOpenFullDetails = { onOpenFullDetails(activityId) },
              onRetry = onRetry)
      is ActivityMapScreenState.Loaded -> {
        ActivityMapSummary(state.activity)
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(20.dp)) {
          Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
            mapContent(Modifier.fillMaxSize())
          }
        }
        Button(
            onClick = { onOpenFullDetails(state.activity.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
          Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
          Text(
              stringResource(R.string.Activity_map_full_details),
              color = RelvenColors.Background,
              fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
private fun ActivityMapSummary(activity: HistoryActivityDetails) {
  val context = LocalContext.current
  val formatter = androidx.compose.runtime.remember(context, LocalConfiguration.current) {
    Formatter(context)
  }
  val date =
      androidx.compose.runtime.remember(activity.startedAtSeconds, LocalConfiguration.current) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(activity.startedAtSeconds * 1000L))
      }
  val measurements = activityDetailMeasurements(activity)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        Sport.textOf(LocalResources.current, activity.sport),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    Text(date, fontSize = 13.sp, color = RelvenColors.MutedText)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      measurements.distanceMeters?.let {
        Text(
            formatter.formatDistance(Formatter.Format.TXT_LONG, it.toLong()),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium)
      }
      measurements.durationSeconds?.let {
        Text(
            formatter.formatElapsedTime(Formatter.Format.TXT_LONG, it),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun ColumnScope.ActivityMapMessage(
    message: String? = null,
    loading: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenFullDetails: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null) {
  Column(
      modifier = Modifier.fillMaxWidth().weight(1f).semantics {
        liveRegion = LiveRegionMode.Polite
      },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center) {
    if (loading) {
      CircularProgressIndicator(color = RelvenColors.Orange, modifier = Modifier.size(28.dp))
      Text(
          stringResource(R.string.Home_activity_detail_loading),
          modifier = Modifier.padding(top = 12.dp),
          color = RelvenColors.MutedText)
    } else {
      message?.let { Text(it, color = RelvenColors.MutedText) }
      if (onRetry != null) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.Home_history_retry)) }
      }
      if (onOpenFullDetails != null) {
        TextButton(onClick = onOpenFullDetails) {
          Text(stringResource(R.string.Activity_map_full_details))
        }
      }
      if (onBack != null) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.Home_activity_detail_back)) }
      }
    }
  }
}
