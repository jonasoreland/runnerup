package org.runnerup.relven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import org.runnerup.common.R
import org.runnerup.util.Formatter
import org.runnerup.workout.Intensity

@Composable
internal fun ActivityDetailScreen(
    loadState: ActivityDetailLoadState,
    editSaveState: ActivityEditSaveState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMap: (Long) -> Unit,
    onBeginEdit: () -> Unit,
    onSaveActivityEdit: (ActivityEditCommand) -> Unit,
    onShareActivity: (HistoryActivityDetails, ActivityShareFormat) -> Unit,
    onOpenMoreActions: (Long) -> Unit) {
  val activityId = (loadState as? ActivityDetailLoadState.Loaded)?.activity?.id
  var editing by remember(activityId) { mutableStateOf(false) }
  LaunchedEffect(editSaveState) {
    if (editSaveState is ActivityEditSaveState.Saved) editing = false
  }
  BackHandler {
    if (editing) {
      if (editSaveState !is ActivityEditSaveState.Saving) editing = false
    } else onBack()
  }
  Surface(
      modifier = Modifier.fillMaxSize(),
      color = RelvenColors.Background,
      contentColor = RelvenColors.Text) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
              if (editing) {
                if (editSaveState !is ActivityEditSaveState.Saving) editing = false
              } else onBack()
            },
            modifier = Modifier.size(48.dp)) {
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
                isEditing = editing,
                editSaveState = editSaveState,
                onBeginEdit = {
                  onBeginEdit()
                  editing = true
                },
                onCancelEdit = { editing = false },
                onSaveActivityEdit = onSaveActivityEdit,
                onOpenMap = { onOpenMap(loadState.activity.id) },
                onShareActivity = { format -> onShareActivity(loadState.activity, format) },
                onOpenMoreActions = { onOpenMoreActions(loadState.activity.id) })
      }
    }
  }
}

@Composable
private fun ActivityDetailContent(
    activity: HistoryActivityDetails,
    isEditing: Boolean,
    editSaveState: ActivityEditSaveState,
    onBeginEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveActivityEdit: (ActivityEditCommand) -> Unit,
    onOpenMap: () -> Unit,
    onShareActivity: (ActivityShareFormat) -> Unit,
    onOpenMoreActions: () -> Unit) {
  var shareDialogVisible by remember(activity.id) { mutableStateOf(false) }
  var shareFormat by remember(activity.id) { mutableStateOf(ActivityShareFormat.Tcx) }
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    ActivityReviewOverview(activity)

    if (isEditing) {
      ActivityEditForm(
          activity = activity,
          saveState = editSaveState,
          onSave = onSaveActivityEdit,
          onCancel = onCancelEdit)
    } else activity.comment?.trim()?.takeIf(String::isNotEmpty)?.let { note ->
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

    if (activity.laps.isNotEmpty()) {
      ActivityLapSection(activity.laps)
    }

    if (!isEditing) {
      ActivityReviewMapCard(activity) { onOpenMap() }
      ActivityReviewChartCard(activity.route)
      ActivityReviewSplitsCard(activity.route)
    }

    if (!isEditing) {
      OutlinedButton(
          onClick = { shareDialogVisible = true },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Home_activity_detail_share))
      }

      OutlinedButton(
          onClick = onBeginEdit,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Home_activity_detail_edit))
      }

      OutlinedButton(
          onClick = onOpenMoreActions,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Home_activity_detail_more_actions))
      }
    }
  }

  if (shareDialogVisible) {
    AlertDialog(
        onDismissRequest = { shareDialogVisible = false },
        title = { Text(stringResource(R.string.Share_activity)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.Home_activity_detail_share_format))
            Row(
                modifier =
                    Modifier.fillMaxWidth().selectable(
                        selected = shareFormat == ActivityShareFormat.Gpx,
                        role = Role.RadioButton) { shareFormat = ActivityShareFormat.Gpx },
                verticalAlignment = Alignment.CenterVertically) {
              RadioButton(
                  selected = shareFormat == ActivityShareFormat.Gpx,
                  onClick = null)
              Text("GPX")
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth().selectable(
                        selected = shareFormat == ActivityShareFormat.Tcx,
                        role = Role.RadioButton) { shareFormat = ActivityShareFormat.Tcx },
                verticalAlignment = Alignment.CenterVertically) {
              RadioButton(
                  selected = shareFormat == ActivityShareFormat.Tcx,
                  onClick = null)
              Text("TCX")
            }
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                shareDialogVisible = false
                onShareActivity(shareFormat)
              }) {
            Text(stringResource(R.string.OK))
          }
        },
        dismissButton = {
          TextButton(onClick = { shareDialogVisible = false }) {
            Text(stringResource(R.string.Cancel))
          }
        })
  }
}

@Composable
internal fun ActivityLapSection(laps: List<ActivityLapDetails>) {
  val context = LocalContext.current
  val formatter = remember(context, LocalConfiguration.current) { Formatter(context) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        stringResource(R.string.Home_activity_detail_splits),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })
    laps.forEach { lap ->
      Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.Home_activity_detail_split_number, lap.number),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold)
            val intensity = Intensity.valueOf(lap.intensity)
            if (intensity != null && intensity != Intensity.ACTIVE) {
              Text(
                  stringResource(intensity.getTextId()),
                  color = RelvenColors.Orange,
                  fontSize = 13.sp)
            }
          }
          val distance = lap.distanceMeters?.takeIf { it > 0.0 }
          val duration = lap.durationSeconds?.takeIf { it > 0L }
          val speed = if (distance != null && duration != null) distance / duration else null
          val lapMetrics =
              listOf(
                  stringResource(R.string.Home_activity_detail_distance) to
                      (distance?.let {
                        formatter.formatDistance(Formatter.Format.TXT_LONG, it.toLong())
                      } ?: stringResource(R.string.Home_activity_detail_unavailable_value)),
                  stringResource(R.string.Home_activity_detail_duration) to
                      (duration?.let { formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, it) }
                          ?: stringResource(R.string.Home_activity_detail_unavailable_value)),
                  formatter.formatVelocityLabel() to
                      (speed?.let {
                        formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, it)
                      } ?: stringResource(R.string.Home_activity_detail_unavailable_value)))
          BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metricRows = if (maxWidth < 320.dp) lapMetrics.chunked(2) else listOf(lapMetrics)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              metricRows.forEach { rowMetrics ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  rowMetrics.forEach { (label, value) ->
                    ActivityLapMetric(label, value, Modifier.weight(1f))
                  }
                  if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                }
              }
            }
          }
          val targets = buildList {
            lap.plannedDurationSeconds?.takeIf { it > 0 }?.let {
              add(
                  stringResource(R.string.Home_activity_detail_duration) + " " +
                      formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, it))
            }
            lap.plannedDistanceMeters?.takeIf { it > 0.0 }?.let {
              add(
                  stringResource(R.string.Home_activity_detail_distance) + " " +
                      formatter.formatDistance(Formatter.Format.TXT_LONG, it.toLong()))
            }
            lap.plannedPace?.takeIf { it > 0.0 }?.let {
              add(
                  formatter.formatVelocityLabel() + " " +
                      formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, it))
            }
          }
          if (targets.isNotEmpty()) {
            Text(
                stringResource(org.runnerup.R.string.activity_review_target, targets.joinToString(" · ")),
                color = RelvenColors.MutedText,
                fontSize = 12.sp)
          }
          lap.averageHeartRate?.let { heartRate ->
            Text(
                stringResource(R.string.Home_activity_detail_average_hr) + " · " +
                    stringResource(R.string.Home_activity_detail_bpm, heartRate),
                color = RelvenColors.MutedText,
                fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun ActivityLapMetric(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        label,
        fontSize = 11.sp,
        color = RelvenColors.MutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    Text(
        value,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
  }
}

@Composable
internal fun ActivityMetric(label: String, value: String, modifier: Modifier = Modifier) {
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
internal fun heartRateLabel(beatsPerMinute: Int): String =
    stringResource(R.string.Home_activity_detail_bpm, beatsPerMinute)
