package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R

@Composable
internal fun PlanCalendarScreen(
    loadState: ActivePlanLoadState,
    nowMillis: Long,
    revisionStatus: PlanRevisionStatus,
    onOpenWorkoutManager: () -> Unit,
    onReschedule: (String, String) -> Unit,
    onAddWorkout: (String, String) -> Unit,
    onAddRest: (String) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit
) {
  when (loadState) {
    ActivePlanLoadState.Loading -> PlanCalendarMessage(stringResource(R.string.Today_plan_loading))
    ActivePlanLoadState.NoActivePlan ->
        PlanCalendarEmpty(
            title = stringResource(R.string.No_active_plan),
            body = stringResource(R.string.Plan_calendar_no_active_plan_hint),
            action = stringResource(R.string.Plan_calendar_open_library),
            onAction = onOpenWorkoutManager)
    ActivePlanLoadState.Unavailable ->
        PlanCalendarMessage(stringResource(R.string.Today_plan_unavailable))
    is ActivePlanLoadState.Loaded ->
        LoadedPlanCalendar(
            loadState.plan,
            nowMillis,
            revisionStatus,
            onOpenWorkoutManager,
            onReschedule,
            onAddWorkout,
            onAddRest,
            onRemoveEntry,
            onRenamePlan,
            onRenameWorkout)
  }
}

@Composable
private fun LoadedPlanCalendar(
    plan: org.runnerup.workout.RelvenPlanImporter.Plan,
    nowMillis: Long,
    revisionStatus: PlanRevisionStatus,
    onOpenWorkoutManager: () -> Unit,
    onReschedule: (String, String) -> Unit,
    onAddWorkout: (String, String) -> Unit,
    onAddRest: (String) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit
) {
  val todayDate = remember(plan.id(), nowMillis) { planLocalDate(plan.timezone(), nowMillis) }
  var selectedDate by rememberSaveable(plan.rootPlanId(), todayDate) { mutableStateOf(todayDate) }
  var month by rememberSaveable(plan.rootPlanId(), todayDate) {
    mutableStateOf(todayDate.substring(0, 7))
  }
  var reschedulingSessionId by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var rescheduleSourceDate by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var rescheduleTargetDate by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var showAddActions by rememberSaveable(plan.id()) { mutableStateOf(false) }
  var pendingRemovalId by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var showRenameDialog by rememberSaveable(plan.id()) { mutableStateOf(false) }
  var editedPlanName by rememberSaveable(plan.id()) { mutableStateOf(plan.name()) }
  var planNameTouched by rememberSaveable(plan.id()) { mutableStateOf(false) }
  var editingWorkoutId by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var editedWorkoutName by rememberSaveable(plan.id()) { mutableStateOf("") }
  var workoutNameTouched by rememberSaveable(plan.id()) { mutableStateOf(false) }
  val locale = LocalLocale.current.platformLocale
  val state = remember(plan, month, selectedDate, todayDate, locale) {
    planCalendarState(plan, month, selectedDate, nowMillis, locale)
  }
  val weekdayLabels = stringArrayResource(R.array.Plan_calendar_weekdays)
  val weekdayNames = stringArrayResource(R.array.Plan_calendar_weekday_names)
  Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(R.string.Plan_calendar_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            plan.name(),
            color = RelvenColors.MutedText,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        TextButton(
            onClick = {
              editedPlanName = plan.name()
              planNameTouched = false
              showRenameDialog = true
            },
            enabled = revisionStatus !is PlanRevisionStatus.Saving) {
          Text(stringResource(R.string.Plan_calendar_rename))
        }
      }
      if (plan.revisionNumber() > 0) {
        Text(
            stringResource(R.string.Plan_calendar_revision_number, plan.revisionNumber()),
            color = RelvenColors.MutedText,
            fontSize = 12.sp)
      }
    }

    when (revisionStatus) {
      PlanRevisionStatus.Saving ->
        Text(
            stringResource(R.string.Plan_calendar_revision_saving),
            color = RelvenColors.MutedText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      PlanRevisionStatus.Saved ->
        Text(
            stringResource(R.string.Plan_calendar_revision_saved),
            color = RelvenColors.Orange,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      PlanRevisionStatus.Conflict ->
        Text(
            stringResource(R.string.Plan_calendar_revision_conflict),
            color = RelvenColors.Orange,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      PlanRevisionStatus.Failed ->
        Text(
            stringResource(R.string.Plan_calendar_revision_failed),
            color = RelvenColors.Orange,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      PlanRevisionStatus.Idle -> Unit
    }

    val reschedulingSession =
        plan.days().firstOrNull { it.id() == reschedulingSessionId }
    val reschedulingLabel =
        reschedulingSession?.let { session ->
          if (session.kind() == "rest") stringResource(R.string.Rest_day)
          else plan.workoutNames()[session.workoutId()].orEmpty()
        }
    if (reschedulingSession != null && reschedulingLabel != null) {
      Column(
          modifier = Modifier.fillMaxWidth().background(RelvenColors.Surface, RoundedCornerShape(16.dp))
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.Plan_calendar_reschedule_prompt, reschedulingLabel),
            color = RelvenColors.Text,
            fontWeight = FontWeight.SemiBold)
        rescheduleTargetDate?.let {
          Text(
              state.selectedDateLabel,
              color = RelvenColors.Orange,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.semantics {
                contentDescription = state.selectedDateLabel
              })
        }
        if (rescheduleTargetDate != null && rescheduleTargetDate == rescheduleSourceDate) {
          Text(
              stringResource(R.string.Plan_calendar_reschedule_choose_different_date),
              color = RelvenColors.MutedText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
              onClick = {
                selectedDate = rescheduleSourceDate ?: todayDate
                month = selectedDate.substring(0, 7)
                reschedulingSessionId = null
                rescheduleSourceDate = null
                rescheduleTargetDate = null
              },
              enabled = revisionStatus !is PlanRevisionStatus.Saving) {
            Text(stringResource(R.string.Plan_calendar_cancel_reschedule))
          }
          Button(
              onClick = {
                rescheduleTargetDate?.let { target -> onReschedule(reschedulingSession.id(), target) }
              },
              enabled =
                  rescheduleTargetDate != null &&
                      rescheduleTargetDate != rescheduleSourceDate &&
                      revisionStatus !is PlanRevisionStatus.Saving,
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
            Text(
                if (revisionStatus is PlanRevisionStatus.Saving)
                    stringResource(R.string.Plan_calendar_revision_saving)
                else stringResource(R.string.Plan_calendar_save_revision))
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
              val shifted = shiftPlanCalendarMonth(month, selectedDate, -1, plan.timezone())
              month = shifted.first
              selectedDate = shifted.second
            },
            modifier = Modifier.size(48.dp),
            enabled = revisionStatus !is PlanRevisionStatus.Saving) {
          Icon(
              Icons.Default.ChevronLeft,
              contentDescription = stringResource(R.string.Plan_calendar_previous_month),
              tint = RelvenColors.Text)
        }
        Text(
            state.monthLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold)
        IconButton(
            onClick = {
              val shifted = shiftPlanCalendarMonth(month, selectedDate, 1, plan.timezone())
              month = shifted.first
              selectedDate = shifted.second
            },
            modifier = Modifier.size(48.dp),
            enabled = revisionStatus !is PlanRevisionStatus.Saving) {
          Icon(
              Icons.Default.ChevronRight,
              contentDescription = stringResource(R.string.Plan_calendar_next_month),
              tint = RelvenColors.Text)
        }
      }

      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gridWidth = maxOf(maxWidth, 308.dp)
        val centeredOffset: Dp = -((gridWidth - maxWidth) / 2)
        Column(Modifier.requiredWidth(gridWidth).offset(x = centeredOffset)) {
          Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEachIndexed { index, weekday ->
              Text(
                  weekday,
                  modifier = Modifier.weight(1f).heightIn(min = 36.dp).semantics {
                    contentDescription = weekdayNames[index]
                  },
                  textAlign = TextAlign.Center,
                  color = RelvenColors.MutedText,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold)
            }
          }

          state.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
              week.forEach { day ->
                CalendarDateCell(
                    day = day,
                    modifier = Modifier.weight(1f),
                    enabled = revisionStatus !is PlanRevisionStatus.Saving,
                    onClick = {
                      selectedDate = day.date
                      month = day.date.substring(0, 7)
                      if (reschedulingSessionId != null) rescheduleTargetDate = day.date
                    })
              }
            }
          }
        }
      }
    }

    if (state.days.none { it.inDisplayedMonth && it.entries.isNotEmpty() }) {
      Text(
          stringResource(R.string.Plan_calendar_empty_month),
          color = RelvenColors.MutedText,
          fontSize = 14.sp)
    }

    SelectedPlanDay(
        dateLabel = state.selectedDateLabel,
        planName = state.planName,
        entries = state.selectedEntries,
        totalEntries = plan.days().size,
        reschedulingSessionId = reschedulingSessionId,
        enabled = revisionStatus !is PlanRevisionStatus.Saving,
        onAdd = { showAddActions = true },
        onReschedule = { sessionId ->
          reschedulingSessionId = sessionId
          rescheduleSourceDate = state.selectedDate
          rescheduleTargetDate = null
        },
        onRemove = { sessionId -> pendingRemovalId = sessionId },
        onEditWorkout = { workoutId, currentName ->
          editingWorkoutId = workoutId
          editedWorkoutName = currentName
          workoutNameTouched = false
        })
  }

  if (showRenameDialog) {
    val validName = canRenamePlanName(editedPlanName, plan.name())
    AlertDialog(
        onDismissRequest = { showRenameDialog = false },
        title = { Text(stringResource(R.string.Plan_calendar_rename_title)) },
        text = {
          OutlinedTextField(
              value = editedPlanName,
              onValueChange = {
                editedPlanName = it
                planNameTouched = true
              },
              label = { Text(stringResource(R.string.Plan_calendar_rename_label)) },
              singleLine = true,
              isError = planNameTouched && !validName,
              supportingText = {
                if (planNameTouched && !validName) {
                  Text(stringResource(R.string.Plan_calendar_rename_invalid))
                }
              })
        },
        confirmButton = {
          TextButton(
              onClick = {
                showRenameDialog = false
                onRenamePlan(editedPlanName.trim())
              },
              enabled = validName && revisionStatus !is PlanRevisionStatus.Saving) {
            Text(stringResource(R.string.Plan_calendar_rename_save))
          }
        },
        dismissButton = {
          TextButton(onClick = { showRenameDialog = false }) {
            Text(stringResource(R.string.Cancel))
          }
        })
  }

  val workoutBeingEdited = editingWorkoutId?.let { plan.workoutNames()[it] }
  if (editingWorkoutId != null && workoutBeingEdited != null) {
    val validName = canRenameWorkoutName(editedWorkoutName, workoutBeingEdited)
    AlertDialog(
        onDismissRequest = { editingWorkoutId = null },
        title = { Text(stringResource(R.string.Plan_calendar_rename_workout_title)) },
        text = {
          OutlinedTextField(
              value = editedWorkoutName,
              onValueChange = {
                editedWorkoutName = it
                workoutNameTouched = true
              },
              label = { Text(stringResource(R.string.Plan_calendar_rename_workout_label)) },
              singleLine = true,
              isError = workoutNameTouched && !validName,
              supportingText = {
                if (workoutNameTouched && !validName) {
                  Text(stringResource(R.string.Plan_calendar_rename_workout_invalid))
                }
              })
        },
        confirmButton = {
          TextButton(
              onClick = {
                editingWorkoutId?.let { workoutId ->
                  onRenameWorkout(workoutId, editedWorkoutName.trim())
                }
                editingWorkoutId = null
              },
              enabled = validName && revisionStatus !is PlanRevisionStatus.Saving) {
            Text(stringResource(R.string.Plan_calendar_rename_workout_save))
          }
        },
        dismissButton = {
          TextButton(onClick = { editingWorkoutId = null }) {
            Text(stringResource(R.string.Cancel))
          }
        })
  }

  if (showAddActions) {
    AlertDialog(
        onDismissRequest = { showAddActions = false },
        title = { Text(stringResource(R.string.Plan_calendar_add_title, state.selectedDateLabel)) },
        text = {
          Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextButton(
                onClick = {
                  showAddActions = false
                  onAddRest(state.selectedDate)
                },
                enabled = revisionStatus !is PlanRevisionStatus.Saving &&
                    canAddRestOnDate(state.selectedEntries)) {
              Text(stringResource(R.string.Plan_calendar_add_rest))
            }
            plan.workoutNames().toSortedMap().forEach { (workoutId, workoutName) ->
              TextButton(
                  onClick = {
                    showAddActions = false
                    onAddWorkout(state.selectedDate, workoutId)
                  },
                  enabled = revisionStatus !is PlanRevisionStatus.Saving &&
                      canAddWorkoutOnDate(state.selectedEntries)) {
                Text(stringResource(R.string.Plan_calendar_add_workout_named, workoutName))
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showAddActions = false }) {
            Text(stringResource(R.string.Cancel))
          }
        })
  }

  val pendingEntry =
      state.selectedEntries.firstOrNull { it.sessionId() == pendingRemovalId }
  if (pendingEntry != null) {
    val entryLabel =
        when (pendingEntry) {
          is PlanCalendarEntry.Workout -> pendingEntry.workoutName
          is PlanCalendarEntry.Rest -> stringResource(R.string.Rest_day)
        }
    AlertDialog(
        onDismissRequest = { pendingRemovalId = null },
        title = { Text(stringResource(R.string.Plan_calendar_remove_title, entryLabel)) },
        text = { Text(stringResource(R.string.Plan_calendar_remove_confirmation)) },
        confirmButton = {
          TextButton(
              onClick = {
                pendingRemovalId = null
                onRemoveEntry(pendingEntry.sessionId())
              },
              enabled = revisionStatus !is PlanRevisionStatus.Saving &&
                  canRemovePlanEntry(plan.days().size)) {
            Text(stringResource(R.string.Plan_calendar_remove))
          }
        },
        dismissButton = {
          TextButton(onClick = { pendingRemovalId = null }) {
            Text(stringResource(R.string.Cancel))
          }
        })
  }
}

@Composable
private fun CalendarDateCell(
    day: PlanCalendarDay,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
  val entryLabels =
      day.entries.map { entry ->
        when (entry) {
          is PlanCalendarEntry.Workout ->
              stringResource(R.string.Plan_calendar_workout_accessibility, entry.workoutName)
          is PlanCalendarEntry.Rest -> stringResource(R.string.Plan_calendar_rest_accessibility)
        }
      }
  val entryLabel =
      if (entryLabels.isEmpty()) stringResource(R.string.Plan_calendar_empty_accessibility)
      else entryLabels.joinToString(separator = ", ")
  val accessibilityLabel =
      buildString {
        append(stringResource(R.string.Plan_calendar_day_accessibility, day.dateLabel, entryLabel))
        if (day.isToday) append(", ").append(stringResource(R.string.Plan_calendar_today))
        if (day.isSelected) append(", ").append(stringResource(R.string.Plan_calendar_selected))
      }
  val emphasis = calendarDateEmphasis(day)
  val selectedColor =
      if (emphasis == CalendarDateEmphasis.Selected || emphasis == CalendarDateEmphasis.TodayAndSelected) {
        RelvenColors.Orange
      } else {
        Color.Transparent
      }
  val outlineColor =
      when (emphasis) {
        CalendarDateEmphasis.Today -> RelvenColors.Orange
        CalendarDateEmphasis.TodayAndSelected -> Color.White
        else -> Color.Transparent
      }
  val textColor =
      when {
        day.isSelected -> Color.White
        day.inDisplayedMonth -> RelvenColors.Text
        else -> RelvenColors.MutedText.copy(alpha = 0.5f)
      }

  Column(
      modifier = modifier.heightIn(min = 48.dp).semantics {
        contentDescription = accessibilityLabel
        selected = day.isSelected
      }.border(1.dp, outlineColor, CircleShape).clip(CircleShape).background(selectedColor)
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center) {
    Text(day.dayNumber.toString(), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    val markerColor =
        when {
          day.entries.any { it is PlanCalendarEntry.Workout } ->
              if (day.isSelected) Color.White else RelvenColors.Orange
          day.entries.any { it is PlanCalendarEntry.Rest } ->
              if (day.isSelected) Color.White else RelvenColors.MutedText
          else -> Color.Transparent
        }
    Box(Modifier.size(5.dp).background(markerColor, CircleShape))
  }
}

@Composable
private fun SelectedPlanDay(
    dateLabel: String,
    planName: String,
    entries: List<PlanCalendarEntry>,
    totalEntries: Int,
    reschedulingSessionId: String?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onReschedule: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditWorkout: (String, String) -> Unit
) {
  Surface(
      shape = RoundedCornerShape(24.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
      modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(dateLabel, color = RelvenColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Text(planName, color = RelvenColors.MutedText, fontSize = 14.sp)
      TextButton(onClick = onAdd, enabled = enabled && reschedulingSessionId == null) {
        Text(stringResource(R.string.Plan_calendar_add_item))
      }
      if (entries.isEmpty()) {
        Text(stringResource(R.string.Plan_calendar_no_item), fontSize = 18.sp, fontWeight = FontWeight.Bold)
      } else {
        entries.forEach { entry ->
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (entry) {
              is PlanCalendarEntry.Workout -> {
                Text(entry.workoutName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (entry.notes.isNotBlank()) {
                  Text(entry.notes, color = RelvenColors.MutedText, fontSize = 14.sp)
                }
                if (reschedulingSessionId == null) {
                  EditWorkoutAction(entry.workoutId, entry.workoutName, enabled, onEditWorkout)
                  RescheduleAction(entry.sessionId, entry.workoutName, enabled, onReschedule)
                  RemoveAction(entry.sessionId, entry.workoutName, totalEntries, enabled, onRemove)
                }
              }
              is PlanCalendarEntry.Rest -> {
                Text(stringResource(R.string.Rest_day), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (entry.notes.isNotBlank()) {
                  Text(entry.notes, color = RelvenColors.MutedText, fontSize = 14.sp)
                }
                if (reschedulingSessionId == null) {
                  RescheduleAction(
                      entry.sessionId,
                      stringResource(R.string.Rest_day),
                      enabled,
                      onReschedule)
                  RemoveAction(
                      entry.sessionId,
                      stringResource(R.string.Rest_day),
                      totalEntries,
                      enabled,
                      onRemove)
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
private fun EditWorkoutAction(
    workoutId: String,
    workoutName: String,
    enabled: Boolean,
    onEditWorkout: (String, String) -> Unit
) {
  val accessibilityLabel =
      stringResource(R.string.Plan_calendar_rename_workout_accessibility, workoutName)
  TextButton(
      onClick = { onEditWorkout(workoutId, workoutName) },
      enabled = enabled,
      modifier = Modifier.semantics {
        contentDescription = accessibilityLabel
      }) {
    Text(stringResource(R.string.Plan_calendar_rename_workout))
  }
}

@Composable
private fun RemoveAction(
    sessionId: String,
    entryName: String,
    totalEntries: Int,
    enabled: Boolean,
    onRemove: (String) -> Unit
) {
  val accessibilityLabel = stringResource(R.string.Plan_calendar_remove_accessibility, entryName)
  TextButton(
      onClick = { onRemove(sessionId) },
      enabled = enabled && canRemovePlanEntry(totalEntries),
      modifier = Modifier.semantics { contentDescription = accessibilityLabel }) {
    Text(stringResource(R.string.Plan_calendar_remove))
  }
}

@Composable
private fun RescheduleAction(
    sessionId: String,
    entryName: String,
    enabled: Boolean,
    onReschedule: (String) -> Unit
) {
  val accessibilityLabel = stringResource(R.string.Plan_calendar_reschedule_accessibility, entryName)
  TextButton(
      onClick = { onReschedule(sessionId) },
      enabled = enabled,
      modifier = Modifier.semantics {
        contentDescription = accessibilityLabel
      }) {
    Text(stringResource(R.string.Plan_calendar_reschedule))
  }
}

private fun PlanCalendarEntry.sessionId(): String =
    when (this) {
      is PlanCalendarEntry.Workout -> sessionId
      is PlanCalendarEntry.Rest -> sessionId
    }

@Composable
private fun PlanCalendarEmpty(title: String, body: String, action: String, onAction: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp).padding(top = 32.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(stringResource(R.string.Plan_calendar_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(body, color = RelvenColors.MutedText, fontSize = 15.sp)
    Button(
        onClick = onAction,
        colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
      Text(action)
    }
  }
}

@Composable
private fun PlanCalendarMessage(message: String) {
  Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp).padding(top = 32.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(stringResource(R.string.Plan_calendar_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text(message, color = RelvenColors.MutedText, fontSize = 15.sp)
  }
}
