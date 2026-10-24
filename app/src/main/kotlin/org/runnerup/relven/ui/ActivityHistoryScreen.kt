package org.runnerup.relven.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.strengthSessionSummary

internal sealed interface ActivityHistoryLoadState {
  data object Loading : ActivityHistoryLoadState

  data class Loaded(val activities: List<HistoryActivity>) : ActivityHistoryLoadState

  data object Unavailable : ActivityHistoryLoadState
}

@Composable
internal fun ActivityHistoryScreen(
    loadState: ActivityHistoryLoadState,
    onRetry: () -> Unit,
    onAddActivity: () -> Unit,
    onImportActivity: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    completedStrengthSessions: List<CompletedStrengthSession> = emptyList(),
    onImportStrengthSession: () -> Unit = {},
    onOpenStrengthSession: (CompletedStrengthSession) -> Unit = {}) {
  var selectedFilter by rememberSaveable { mutableStateOf(HistorySportFilter.ALL) }
  val context = LocalContext.current
  val formatter = remember(context, LocalConfiguration.current) { Formatter(context) }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(horizontal = 24.dp)
              .padding(top = 28.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
          stringResource(R.string.Home_history_title),
          fontSize = 28.sp,
          lineHeight = 34.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics { heading() })
      Row {
        IconButton(onClick = onImportStrengthSession) {
          Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.Strength_session_import), tint = RelvenColors.Orange)
        }
        IconButton(onClick = onImportActivity) {
          Icon(
              Icons.Default.FileOpen,
              contentDescription = stringResource(R.string.Home_history_import),
              tint = RelvenColors.Orange)
        }
        IconButton(onClick = onAddActivity) {
          Icon(
              Icons.Default.Add,
              contentDescription = stringResource(R.string.Home_history_add),
              tint = RelvenColors.Orange)
        }
      }
    }

    when (loadState) {
      ActivityHistoryLoadState.Loading ->
          Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            HistoryMessage(loading = true)
          }
      ActivityHistoryLoadState.Unavailable ->
          Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            HistoryMessage(
                body = stringResource(R.string.Home_history_unavailable), onRetry = onRetry)
          }
      is ActivityHistoryLoadState.Loaded -> {
        val activities = loadState.activities
        val filterCounts = remember(activities) { historyFilterCounts(activities) }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HistorySportFilter.entries.forEach { filter ->
            val count = filterCounts.getValue(filter)
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = {
                  Text("${stringResource(filter.labelResource())} · $count", maxLines = 1)
                })
          }
        }
        val visibleActivities = remember(activities, selectedFilter) {
          filteredHistory(activities, selectedFilter)
        }
        when {
          activities.isEmpty() && completedStrengthSessions.isEmpty() ->
              Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                HistoryMessage(
                    title = stringResource(R.string.Home_history_empty_title),
                    body = stringResource(R.string.Home_history_empty_message),
                    action = stringResource(R.string.Home_history_add),
                    onAction = onAddActivity)
              }
          visibleActivities.isEmpty() && completedStrengthSessions.isEmpty() ->
              Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                HistoryMessage(body = stringResource(R.string.Home_history_filter_empty))
              }
          else ->
              LazyColumn(
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (completedStrengthSessions.isNotEmpty()) {
                      item(key = "strength-session-heading") {
                        Text(stringResource(R.string.Strength_session_history), color = RelvenColors.Orange, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                      }
                      itemsIndexed(completedStrengthSessions, key = { _, session -> "strength-${session.state.executionId}" }) { _, session ->
                        val summary = remember(session) { strengthSessionSummary(session.state) }
                        val date = remember(session.finishedAtMillis, LocalConfiguration.current) {
                          DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.finishedAtMillis))
                        }
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenStrengthSession(session) }.padding(vertical = 14.dp)) {
                          Text(session.state.workout.name(), color = RelvenColors.Text, fontWeight = FontWeight.SemiBold)
                          Text(date, color = RelvenColors.MutedText, fontSize = 12.sp)
                          val metrics = buildList {
                            add(stringResource(R.string.Strength_session_history_metrics, summary.completedSetCount, summary.prescribedSetCount))
                            summary.totalRepetitions?.let { add(stringResource(R.string.Strength_session_history_repetitions, it)) }
                            if (summary.totalRepetitions == null) {
                              summary.totalWorkSeconds?.let { add(stringResource(R.string.Strength_session_history_duration, it)) }
                            }
                          }
                          Text(metrics.joinToString(" · "), color = RelvenColors.MutedText, fontSize = 13.sp)
                        }
                      }
                    }
                    if (visibleActivities.isNotEmpty()) {
                    itemsIndexed(visibleActivities, key = { _, activity -> activity.id }) {
                        index,
                        activity ->
                      HistoryActivityRow(
                          activity, formatter, onClick = { onOpenActivity(activity.id) })
                      if (index < visibleActivities.lastIndex) {
                        Surface(
                            color = RelvenColors.MutedText.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                      }
                    }
                    }
                  }
        }
      }
    }
  }
}

@Composable
private fun HistoryActivityRow(
    activity: HistoryActivity,
    formatter: Formatter,
    onClick: () -> Unit) {
  val sport = Sport.textOf(LocalResources.current, activity.sport)
  val date =
      remember(activity.startedAtSeconds, LocalConfiguration.current) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(activity.startedAtSeconds * 1000L))
      }
  val metrics = buildList {
    activity.distanceMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { add(formatter.formatDistance(Formatter.Format.TXT_SHORT, it.toLong())) }
    activity.durationSeconds
        ?.takeIf { it >= 0L }
        ?.let { duration -> add(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, duration)) }
    val duration = activity.durationSeconds
    val distance = activity.distanceMeters
    if (duration != null && duration > 0L && distance != null && distance.isFinite() && distance > 0.0) {
      add(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, distance / duration))
    }
  }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(role = Role.Button, onClick = onClick)
              .padding(vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically) {
    Surface(color = RelvenColors.Orange, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
    Column(
        modifier = Modifier.weight(1f).padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(sport, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(date, fontSize = 12.sp, color = RelvenColors.MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (activity.unfinished) {
        Text(
            stringResource(R.string.Home_history_interrupted),
            fontSize = 12.sp,
            color = RelvenColors.Orange,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
      if (metrics.isNotEmpty()) {
        Text(
            metrics.joinToString("  ·  "),
            fontSize = 13.sp,
            color = RelvenColors.MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun HistoryMessage(
    loading: Boolean = false,
    title: String? = null,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null) {
  Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
    Column(
        modifier =
            Modifier.semantics {
              if (loading || onRetry != null) liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (loading) {
        CircularProgressIndicator(color = RelvenColors.Orange, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.Home_history_loading), color = RelvenColors.MutedText)
      } else {
        title?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        body?.let { Text(it, color = RelvenColors.MutedText) }
        if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        if (onRetry != null) {
          TextButton(onClick = onRetry) { Text(stringResource(R.string.Home_history_retry)) }
        }
      }
    }
  }
}

@Composable
private fun HistorySportFilter.labelResource(): Int =
    when (this) {
      HistorySportFilter.ALL -> R.string.Home_history_filter_all
      HistorySportFilter.RUNNING -> R.string.Home_history_filter_running
      HistorySportFilter.CYCLING -> R.string.Home_history_filter_cycling
      HistorySportFilter.WALKING -> R.string.Home_history_filter_walking
      HistorySportFilter.STRENGTH -> R.string.Home_history_filter_strength
      HistorySportFilter.OTHER -> R.string.Home_history_filter_other
    }
