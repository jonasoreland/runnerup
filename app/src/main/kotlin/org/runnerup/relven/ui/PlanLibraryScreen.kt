package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import org.runnerup.R
import org.runnerup.workout.RelvenPlanImporter

internal data class PlanLibraryState(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val plans: List<RelvenPlanImporter.Plan> = emptyList(),
    val activePlanId: String? = null,
    val archivedRootPlanIds: Set<String> = emptySet())

internal data class PlanLibraryGroup(
    val rootPlanId: String,
    val revisions: List<RelvenPlanImporter.Plan>,
    val activePlan: RelvenPlanImporter.Plan?,
    val archived: Boolean) {
  val displayPlan: RelvenPlanImporter.Plan
    get() = activePlan ?: revisions.first()
  val isActive: Boolean
    get() = activePlan != null
}

internal fun groupSavedPlans(
    plans: List<RelvenPlanImporter.Plan>,
    activePlanId: String?,
    archivedRootPlanIds: Set<String>): List<PlanLibraryGroup> =
    plans.groupBy { it.rootPlanId() }
        .map { (rootPlanId, revisions) ->
          val ordered = revisions.sortedWith(
              compareByDescending<RelvenPlanImporter.Plan> { it.revisionNumber() }
                  .thenBy { it.id() })
          PlanLibraryGroup(
              rootPlanId = rootPlanId,
              revisions = ordered,
              activePlan = ordered.firstOrNull { it.id() == activePlanId },
              archived = rootPlanId in archivedRootPlanIds)
        }
        .sortedBy { it.displayPlan.name().lowercase() }

internal data class PendingPlanImport(
    val plan: RelvenPlanImporter.Plan,
    val summary: String)

internal fun libraryPreviewPlan(
    plans: List<RelvenPlanImporter.Plan>,
    selectedPlanId: String?,
    activePlanId: String?): RelvenPlanImporter.Plan? =
    plans.firstOrNull { it.id() == selectedPlanId }
        ?: plans.firstOrNull { it.id() == activePlanId }
        ?: groupSavedPlans(plans, null, emptySet()).firstOrNull()?.displayPlan

@Composable
internal fun PlanLibraryScreen(
    state: PlanLibraryState,
    favoriteWorkouts: List<FavoriteWorkout>,
    currentTimeMillis: Long,
    busy: Boolean,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onCreatePlan: () -> Unit,
    onCreateWithAi: () -> Unit,
    pendingActivation: Boolean,
    onRetryPendingActivation: () -> Unit,
    onActivate: (RelvenPlanImporter.Plan) -> Unit,
    onClearActive: () -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onStartFavoriteWorkout: (FavoriteWorkout) -> Unit,
    onRemoveFavoriteWorkout: (String) -> Unit,
    onOpenCalendar: () -> Unit) {
  val weekdays = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekdays)
  val weekdayNames = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekday_names)
  val locale = LocalLocale.current.platformLocale
  var previewPlanId by rememberSaveable { mutableStateOf<String?>(null) }
  val previewPlan = remember(state.plans, previewPlanId, state.activePlanId) {
    libraryPreviewPlan(state.plans, previewPlanId, state.activePlanId)
  }
  val previewToday = previewPlan?.let { planLocalDate(it.timezone(), currentTimeMillis) }
      ?: planLocalDate(TimeZone.getDefault().id, currentTimeMillis)
  var selectedDate by rememberSaveable(previewPlan?.id()) { mutableStateOf(previewToday) }
  var month by rememberSaveable(previewPlan?.id()) {
    mutableStateOf(previewToday.substring(0, 7))
  }
  val previewCalendar = remember(previewPlan?.id(), month, selectedDate, currentTimeMillis, locale) {
    previewPlan?.let {
      planCalendarState(
          it,
          month,
          selectedDate,
          currentTimeMillis,
          locale,
          viewMode = PlanCalendarViewMode.Month)
    }
  }
  val planGroups = remember(state.plans, state.activePlanId, state.archivedRootPlanIds) {
    groupSavedPlans(state.plans, state.activePlanId, state.archivedRootPlanIds)
  }
  val activePlanGroups = remember(planGroups) { planGroups.filterNot { it.archived } }
  val archivedPlanGroups = remember(planGroups) { planGroups.filter { it.archived } }
  var showArchivedPlans by rememberSaveable { mutableStateOf(false) }
  var expandedHistoryRootPlanId by rememberSaveable { mutableStateOf<String?>(null) }
  var archiveConfirmationRootPlanId by rememberSaveable { mutableStateOf<String?>(null) }
  var favoriteRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
  LazyColumn(
      modifier = Modifier.fillMaxSize().background(RelvenColors.Background),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
          start = 24.dp, end = 24.dp, top = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.plan_library_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.plan_library_subtitle), color = RelvenColors.MutedText, fontSize = 15.sp)
      }
    }
    item {
      Button(
          onClick = onCreatePlan, enabled = !busy && !pendingActivation,
          colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
          modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Text(stringResource(R.string.plan_create_title))
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.plan_favorite_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (favoriteWorkouts.isEmpty()) {
          Text(stringResource(R.string.plan_favorite_empty), color = RelvenColors.MutedText, fontSize = 14.sp)
        }
      }
    }
    items(favoriteWorkouts, key = FavoriteWorkout::id) { favorite ->
      Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(favorite.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
          Text(
              stringResource(
                  if (favorite.sport == "running") R.string.plan_favorite_running
                  else R.string.plan_favorite_strength),
              color = RelvenColors.MutedText,
              fontSize = 13.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStartFavoriteWorkout(favorite) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
              Text(stringResource(R.string.plan_favorite_start))
            }
            TextButton(enabled = !busy, onClick = { favoriteRemovalId = favorite.id }) {
              Text(stringResource(R.string.plan_favorite_remove), color = RelvenColors.MutedText)
            }
          }
        }
      }
    }
    item {
      Surface(
          color = RelvenColors.Surface,
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.28f)),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(stringResource(R.string.plan_library_calendar_title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
              Text(
                  previewPlan?.name() ?: stringResource(R.string.plan_library_calendar_no_plan),
                  color = RelvenColors.MutedText,
                  fontSize = 13.sp)
            }
            IconButton(
                enabled = !busy,
                onClick = {
                  val shifted = shiftPlanCalendarMonth(
                      month, selectedDate, -1, previewPlan?.timezone() ?: TimeZone.getDefault().id)
                  month = shifted.first
                  selectedDate = shifted.second
                }) {
            Icon(Icons.Default.ChevronLeft,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_previous_period),
                tint = RelvenColors.MutedText)
            }
            IconButton(
                enabled = !busy,
                onClick = {
                  val shifted = shiftPlanCalendarMonth(
                      month, selectedDate, 1, previewPlan?.timezone() ?: TimeZone.getDefault().id)
                  month = shifted.first
                  selectedDate = shifted.second
                }) {
            Icon(Icons.Default.ChevronRight,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_next_period),
                tint = RelvenColors.MutedText)
            }
          }
          if (previewPlan == null) {
            Text(PlanCalendarMonth.parse(month).label(locale), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
              weekdays.forEachIndexed { index, day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f).semantics { contentDescription = weekdayNames[index] },
                    color = RelvenColors.MutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center)
              }
            }
            emptyPlanCalendarDateStrings(PlanCalendarMonth.parse(month)).chunked(7).forEach { week ->
              Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                  EmptyCalendarDateCell(
                      date = date,
                      todayDate = previewToday,
                      selectedDate = selectedDate,
                      modifier = Modifier.weight(1f),
                      enabled = !busy,
                      onClick = { date?.let { selectedDate = it } })
                }
              }
            }
            Surface(
                color = RelvenColors.Background.copy(alpha = 0.48f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val dateLabel = SimpleDateFormat("EEEE d MMMM yyyy", locale).format(
                    Calendar.getInstance().apply {
                      timeZone = TimeZone.getDefault()
                      val parts = selectedDate.split('-').map(String::toInt)
                      set(parts[0], parts[1] - 1, parts[2], 12, 0, 0)
                      set(Calendar.MILLISECOND, 0)
                    }.time)
                Text(dateLabel.replaceFirstChar { it.titlecase(locale) }, color = RelvenColors.Orange,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(org.runnerup.common.R.string.Plan_calendar_no_item), color = RelvenColors.MutedText)
              }
            }
          } else {
            Text(previewCalendar?.periodLabel.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
              weekdays.forEachIndexed { index, day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f).semantics {
                      contentDescription = weekdayNames[index]
                    },
                    color = RelvenColors.MutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center)
              }
            }
            previewCalendar?.days?.chunked(7)?.forEach { week ->
              Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                  CalendarDateCell(
                      day = day,
                      modifier = Modifier.weight(1f),
                      enabled = !busy,
                      onClick = {
                        selectedDate = day.date
                        month = day.date.substring(0, 7)
                      })
                }
              }
            }
            Surface(
                color = RelvenColors.Background.copy(alpha = 0.48f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(previewCalendar?.selectedDateLabel.orEmpty(), color = RelvenColors.Orange,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val entries = previewCalendar?.selectedEntries.orEmpty()
                if (entries.isEmpty()) {
                  Text(stringResource(org.runnerup.common.R.string.Plan_calendar_no_item), color = RelvenColors.MutedText)
                } else {
                  entries.forEach { entry ->
                    when (entry) {
                      is PlanCalendarEntry.Workout -> {
                        Text(entry.workoutName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (entry.notes.isNotBlank()) Text(entry.notes, color = RelvenColors.MutedText, fontSize = 13.sp)
                      }
                      is PlanCalendarEntry.Rest -> {
                        Text(stringResource(org.runnerup.common.R.string.Rest_day), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (entry.notes.isNotBlank()) Text(entry.notes, color = RelvenColors.MutedText, fontSize = 13.sp)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    item {
      Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.plan_library_create_ai), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
          Text(stringResource(R.string.plan_library_create_ai_help), color = RelvenColors.MutedText, fontSize = 14.sp)
          Button(
              onClick = onCreateWithAi, enabled = !busy,
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(if (busy) R.string.plan_library_busy else R.string.plan_library_create_ai))
          }
        }
      }
    }
    item {
      OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.plan_library_import))
      }
    }
    if (pendingActivation) {
      item {
        Surface(
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.plan_create_activation_error), color = RelvenColors.MutedText)
            Button(
                onClick = onRetryPendingActivation,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
              Text(stringResource(if (busy) R.string.plan_create_saving else R.string.plan_create_retry_activation))
            }
          }
        }
      }
    }
    item { Text(stringResource(R.string.plan_library_saved), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
    if (state.loading) {
      item { Text(stringResource(R.string.plan_library_loading), color = RelvenColors.MutedText) }
    } else if (state.unavailable) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.plan_library_error), color = RelvenColors.MutedText)
          TextButton(onClick = onRetry) { Text(stringResource(R.string.plan_library_retry)) }
        }
      }
    } else {
      item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
              modifier = Modifier.weight(1f),
              selected = !showArchivedPlans,
              onClick = { showArchivedPlans = false },
              label = {
                Text(stringResource(R.string.plan_library_saved_filter, activePlanGroups.size))
              })
          FilterChip(
              modifier = Modifier.weight(1f),
              selected = showArchivedPlans,
              onClick = { showArchivedPlans = true },
              label = {
                Text(stringResource(R.string.plan_library_archived_filter, archivedPlanGroups.size))
              })
        }
      }
      val visibleGroups = if (showArchivedPlans) archivedPlanGroups else activePlanGroups
      if (visibleGroups.isEmpty()) {
        item {
          Text(
              stringResource(
                  if (showArchivedPlans) R.string.plan_library_no_archived
                  else R.string.plan_library_no_saved),
              color = RelvenColors.MutedText)
        }
      }
      items(visibleGroups, key = { it.rootPlanId }) { group ->
        val plan = group.displayPlan
        val active = group.isActive
        Surface(
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, if (active) RelvenColors.Orange.copy(alpha = 0.55f)
                else RelvenColors.Text.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(plan.name(), modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
              if (active) Text(stringResource(R.string.plan_library_active), color = RelvenColors.Orange,
                  fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              if (group.archived) Text(stringResource(R.string.plan_library_archived), color = RelvenColors.MutedText,
                  fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            val dates = plan.days().map { it.date() }.sorted()
            Text(
                "${dates.first()} – ${dates.last()}  ·  " +
                    pluralStringResource(R.plurals.plan_library_workouts, plan.workoutNames().size, plan.workoutNames().size) +
                    " · " + pluralStringResource(R.plurals.plan_library_training_days, plan.workoutDays().toInt(), plan.workoutDays().toInt()) +
                    " · " + pluralStringResource(R.plurals.plan_library_rest_days, plan.restDays().toInt(), plan.restDays().toInt()),
                color = RelvenColors.MutedText, fontSize = 13.sp)
            if (group.revisions.size > 1) {
              Text(
                  pluralStringResource(
                      R.plurals.plan_library_revision_count,
                      group.revisions.size,
                      group.revisions.size),
                  color = RelvenColors.MutedText,
                  fontSize = 13.sp)
            }
            if (active) {
              Button(
                  enabled = !busy,
                  onClick = onOpenCalendar,
                  colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                  modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.plan_library_calendar))
              }
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = { previewPlanId = plan.id() }) {
                  Text(
                      stringResource(
                          if (previewPlan?.id() == plan.id()) R.string.plan_library_previewing_short
                          else R.string.plan_library_preview_short),
                      maxLines = 1,
                      textAlign = TextAlign.Center)
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = onClearActive) {
                  Text(stringResource(R.string.plan_library_clear), maxLines = 2, textAlign = TextAlign.Center)
                }
              }
            } else {
              if (!group.archived) {
                Button(
                    enabled = !busy,
                    onClick = { onActivate(plan) },
                    colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                  Text(stringResource(R.string.plan_library_activate))
                }
              }
              TextButton(
                  modifier = Modifier.fillMaxWidth(),
                  enabled = !busy,
                  onClick = { previewPlanId = plan.id() }) {
                Text(stringResource(R.string.plan_library_preview_calendar))
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (group.archived) {
                TextButton(enabled = !busy, onClick = { onRestore(group.rootPlanId) }) {
                  Text(stringResource(R.string.plan_library_restore))
                }
              } else if (!active) {
                TextButton(enabled = !busy, onClick = { archiveConfirmationRootPlanId = group.rootPlanId }) {
                  Text(stringResource(R.string.plan_library_archive))
                }
              }
              if (group.revisions.size > 1) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                      expandedHistoryRootPlanId =
                          if (expandedHistoryRootPlanId == group.rootPlanId) null else group.rootPlanId
                    }) {
                  Text(stringResource(
                      if (expandedHistoryRootPlanId == group.rootPlanId)
                        R.string.plan_library_hide_history else R.string.plan_library_history))
                }
              }
            }
            if (expandedHistoryRootPlanId == group.rootPlanId) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.revisions.forEach { revision ->
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (revision.revisionNumber() == 0) {
                          stringResource(R.string.plan_library_original)
                        } else {
                          stringResource(R.string.plan_library_revision_number, revision.revisionNumber())
                        },
                        modifier = Modifier.weight(1f),
                        color = if (revision.id() == state.activePlanId) RelvenColors.Orange
                            else RelvenColors.MutedText,
                        fontSize = 13.sp)
                    TextButton(enabled = !busy, onClick = { previewPlanId = revision.id() }) {
                      Text(stringResource(R.string.plan_library_preview_calendar))
                    }
                    if (!group.archived && revision.id() != state.activePlanId) {
                      TextButton(enabled = !busy, onClick = { onActivate(revision) }) {
                        Text(stringResource(R.string.plan_library_activate))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  val archiveConfirmationGroup = planGroups.firstOrNull {
    it.rootPlanId == archiveConfirmationRootPlanId
  }
  if (archiveConfirmationGroup != null) {
    AlertDialog(
        onDismissRequest = { if (!busy) archiveConfirmationRootPlanId = null },
        title = { Text(stringResource(R.string.plan_library_archive_title)) },
        text = {
          Text(stringResource(R.string.plan_library_archive_message, archiveConfirmationGroup.displayPlan.name()))
        },
        confirmButton = {
          TextButton(enabled = !busy, onClick = {
            archiveConfirmationRootPlanId = null
            onArchive(archiveConfirmationGroup.rootPlanId)
          }) { Text(stringResource(R.string.plan_library_archive)) }
        },
        dismissButton = {
          TextButton(enabled = !busy, onClick = { archiveConfirmationRootPlanId = null }) {
            Text(stringResource(R.string.plan_library_cancel))
          }
        })
  }
  favoriteRemovalId?.let { favoriteId ->
    val favorite = favoriteWorkouts.firstOrNull { it.id == favoriteId }
    AlertDialog(
        onDismissRequest = { if (!busy) favoriteRemovalId = null },
        title = { Text(stringResource(R.string.plan_favorite_remove_title)) },
        text = { Text(stringResource(R.string.plan_favorite_remove_message, favorite?.name.orEmpty())) },
        confirmButton = {
          TextButton(enabled = !busy, onClick = {
            onRemoveFavoriteWorkout(favoriteId)
            favoriteRemovalId = null
          }) { Text(stringResource(R.string.plan_favorite_remove)) }
        },
        dismissButton = {
          TextButton(enabled = !busy, onClick = { favoriteRemovalId = null }) {
            Text(stringResource(R.string.plan_library_cancel))
          }
        })
  }
}

@Composable
private fun EmptyCalendarDateCell(
    date: String?,
    todayDate: String,
    selectedDate: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
  if (date == null) {
    Box(modifier.heightIn(min = 48.dp))
    return
  }
  val locale = LocalLocale.current.platformLocale
  val dateLabel = remember(date, locale) {
    val parts = date.split('-').map(String::toInt)
    val calendar = Calendar.getInstance().apply {
      set(parts[0], parts[1] - 1, parts[2], 12, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    SimpleDateFormat("EEEE d MMMM yyyy", locale).format(calendar.time)
        .replaceFirstChar { it.titlecase(locale) }
  }
  val isSelected = date == selectedDate
  val isToday = date == todayDate
  val background = if (isSelected) RelvenColors.Orange else Color.Transparent
  val outline = if (isToday && isSelected) Color.White else if (isToday) RelvenColors.Orange else Color.Transparent
  val accessibilityLabel = stringResource(
      org.runnerup.common.R.string.Plan_calendar_day_accessibility,
      dateLabel,
      stringResource(org.runnerup.common.R.string.Plan_calendar_empty_accessibility))
  Box(
      modifier = modifier.heightIn(min = 48.dp).semantics {
        contentDescription = accessibilityLabel
        selected = isSelected
      }.border(1.dp, outline, CircleShape).clip(CircleShape).background(background)
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center) {
    Text(date.substringAfterLast('-').toInt().toString(),
        color = if (isSelected) Color.White else RelvenColors.Text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium)
  }
}

@Composable
internal fun PlanImportReviewDialog(
    pending: PendingPlanImport,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit) {
  AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      title = { Text(stringResource(R.string.plan_library_import_title)) },
      text = { Text(pending.summary) },
      confirmButton = {
        TextButton(enabled = !busy, onClick = onSave) { Text(stringResource(R.string.plan_library_save)) }
      },
      dismissButton = {
        TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.plan_library_cancel)) }
      })
}
