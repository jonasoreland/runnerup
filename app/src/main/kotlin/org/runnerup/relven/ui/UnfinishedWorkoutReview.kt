package org.runnerup.relven.ui

import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.R as AppR
import org.runnerup.common.R as CommonR
import org.runnerup.workout.Sport
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** Java bridge lets legacy Tracker result codes keep owning the unfinished workout lifecycle. */
interface UnfinishedWorkoutReviewActions {
  fun onNoteChanged(note: String)
  fun onResume()
  fun onSave()
  fun onDiscard()
  fun onOpenMap()
  fun onSportChanged(sport: Int)
  fun onManualDistanceChanged(distanceMeters: String)
}

object UnfinishedWorkoutReviewAdapter {
  @JvmStatic
  fun install(
      view: ComposeView,
      database: SQLiteDatabase,
      activityId: Long,
      initialNote: String,
      lifecycleOwner: LifecycleOwner,
      uploadState: ActivityUploadStateHolder,
      actions: UnfinishedWorkoutReviewActions
  ) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    val loadState = androidx.compose.runtime.mutableStateOf<ActivityDetailLoadState>(ActivityDetailLoadState.Loading)
    view.setContent {
      RelvenTheme {
        when (val state = loadState.value) {
          ActivityDetailLoadState.Loading ->
              Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
                CircularProgressIndicator(
                    color = RelvenColors.Orange,
                    modifier = Modifier.statusBarsPadding().padding(24.dp))
              }
          ActivityDetailLoadState.NotFound, ActivityDetailLoadState.Unavailable ->
              Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
                Text(
                    stringResource(CommonR.string.Home_activity_detail_unavailable),
                    color = RelvenColors.Text,
                    modifier = Modifier.statusBarsPadding().padding(24.dp))
              }
          is ActivityDetailLoadState.Loaded ->
              UnfinishedWorkoutReviewScreen(state.activity, initialNote, uploadState, actions)
          }
      }
    }
    lifecycleOwner.lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { readHistoryActivityDetails(database, activityId) }
      }
      loadState.value =
          result.fold(
              onSuccess = { activity ->
                activity?.let(ActivityDetailLoadState::Loaded)
                    ?: ActivityDetailLoadState.NotFound
              },
              onFailure = { ActivityDetailLoadState.Unavailable })
    }
  }
}

@Composable
private fun UnfinishedWorkoutReviewScreen(
    activity: HistoryActivityDetails,
    initialNote: String,
    uploadState: ActivityUploadStateHolder,
    actions: UnfinishedWorkoutReviewActions
) {
  var note by rememberSaveable(activity.id) { mutableStateOf(initialNote) }
  var sport by rememberSaveable(activity.id) { mutableIntStateOf(activity.sport) }
  var manualDistanceMeters by rememberSaveable(activity.id) {
    mutableStateOf(activity.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }?.toLong()?.toString().orEmpty())
  }
  var sportMenuExpanded by rememberSaveable(activity.id) { mutableStateOf(false) }
  var confirmDiscard by remember { mutableStateOf(false) }
  var saving by rememberSaveable(activity.id) { mutableStateOf(false) }
  BackHandler { actions.onResume() }
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onClick = { if (!saving) actions.onResume() }, enabled = !saving) {
          Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(AppR.string.activity_review_resume),
              tint = RelvenColors.Orange)
        }
        Text(
            stringResource(AppR.string.activity_review_title),
            color = RelvenColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold)
      }
      val editedDistance =
          manualDistanceMeters.toLongOrNull()?.toDouble()?.takeIf { it.isFinite() && it > 0.0 }
      val reviewActivity =
          activity.copy(sport = sport, distanceMeters = editedDistance ?: activity.distanceMeters)
      ActivityReviewOverview(
          reviewActivity)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(AppR.string.activity_review_activity_type),
            color = RelvenColors.MutedText)
        androidx.compose.foundation.layout.Box {
          OutlinedButton(
              onClick = { sportMenuExpanded = true },
              enabled = !saving,
              modifier = Modifier.fillMaxWidth()) {
            Text(Sport.textOf(LocalResources.current, sport))
          }
          DropdownMenu(
              expanded = sportMenuExpanded,
              onDismissRequest = { sportMenuExpanded = false }) {
            Sport.values().forEach { value ->
              DropdownMenuItem(
                  text = { Text(Sport.textOf(LocalResources.current, value.dbValue)) },
                  onClick = {
                    sport = value.dbValue
                    sportMenuExpanded = false
                    actions.onSportChanged(sport)
                  })
            }
          }
        }
      }
      if (Sport.hasManualDistance(sport)) {
        OutlinedTextField(
            value = manualDistanceMeters,
            onValueChange = { value ->
              manualDistanceMeters = value
              actions.onManualDistanceChanged(value)
            },
            label = { Text(stringResource(AppR.string.activity_review_manual_distance_meters)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
      }
      if (!Sport.isWithoutGps(sport) && reviewActivity.route.size >= 2) {
        ActivityReviewMapCard(reviewActivity, actions::onOpenMap)
        ActivityReviewChartCard(reviewActivity.route)
        ActivityReviewSplitsCard(reviewActivity.route)
      } else if (!Sport.isWithoutGps(sport)) {
        Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(18.dp)) {
          Text(
              stringResource(AppR.string.activity_review_no_route),
              color = RelvenColors.MutedText,
              modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
      }
      if (activity.laps.isNotEmpty()) ActivityLapSection(activity.laps)
      OutlinedTextField(
          value = note,
          onValueChange = {
            note = it
            actions.onNoteChanged(it)
          },
          label = { Text(stringResource(CommonR.string.Home_activity_detail_note)) },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          shape = RoundedCornerShape(16.dp))
      ActivityUploadReviewOptions(uploadState)
      Button(
          onClick = {
            if (!saving) {
              saving = true
              actions.onSave()
            }
          },
          enabled = !saving,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = RelvenColors.Orange,
                  contentColor = RelvenColors.Background)) {
        if (saving) {
          CircularProgressIndicator(
              modifier = Modifier.padding(end = 10.dp),
              color = RelvenColors.Background,
              strokeWidth = 2.dp)
          Text(stringResource(AppR.string.activity_review_saving), fontWeight = FontWeight.Bold)
        } else Text(stringResource(CommonR.string.Save), fontWeight = FontWeight.Bold)
      }
      OutlinedButton(
          onClick = { if (!saving) actions.onResume() },
          enabled = !saving,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp)) {
        Text(stringResource(AppR.string.activity_review_resume))
      }
      TextButton(
          onClick = { if (!saving) confirmDiscard = true },
          enabled = !saving,
          modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(CommonR.string.Discard), color = RelvenColors.MutedText)
      }
    }
  }
  if (confirmDiscard) {
    AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(CommonR.string.Discard)) },
        text = { Text(stringResource(AppR.string.activity_review_discard_confirmation)) },
        confirmButton = {
          TextButton(onClick = actions::onDiscard) {
            Text(stringResource(CommonR.string.Discard), color = RelvenColors.Orange)
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(CommonR.string.No))
          }
        })
  }
}

@Composable
private fun ActivityUploadReviewOptions(state: ActivityUploadStateHolder) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(18.dp)) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = { expanded = !expanded }) {
        Text(
            stringResource(AppR.string.activity_review_upload_options),
            color = RelvenColors.Orange)
      }
      if (expanded) {
        if (state.services.isEmpty()) {
          Text(
              stringResource(AppR.string.activity_review_no_upload_services),
              color = RelvenColors.MutedText)
        }
        state.services.forEach { service ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (service.uploaded) {
              Text("✓", color = RelvenColors.Orange)
              Text(service.name, modifier = Modifier.weight(1f), color = RelvenColors.Text)
              service.externalUrl?.let { url ->
                TextButton(onClick = { state.openUploadedActivity(service.name) }) {
                  Text(stringResource(AppR.string.activity_review_uploaded), color = RelvenColors.MutedText)
                }
              }
            } else {
              Checkbox(
                  checked = service.selected,
                  onCheckedChange = { state.selectionChanged(service.name, it) })
              Text(service.name, color = RelvenColors.Text)
            }
          }
        }
        OutlinedButton(
            onClick = state::manageAccounts,
            modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(AppR.string.activity_review_manage_uploads))
        }
      }
    }
  }
}
