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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONObject
import org.runnerup.common.R
import org.runnerup.relven.strength.CompletedStrengthSession

@Composable
internal fun PlanCalendarScreen(
    loadState: ActivePlanLoadState,
    nowMillis: Long,
    revisionStatus: PlanRevisionStatus,
    completedStrengthSessions: Map<String, CompletedStrengthSession> = emptyMap(),
    onStartStrength: (String, PlanCalendarEntry.Workout) -> Unit = { _, _ -> },
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit = {},
    onOpenWorkoutManager: () -> Unit,
    onReschedule: (String, String) -> Unit,
    onAddWorkout: (String, String) -> Unit,
    onAddRest: (String) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit,
    onEditRunningEnd: (String, String, JSONObject) -> Unit,
    onEditRunningTarget: (String, String, JSONObject?) -> Unit
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
            completedStrengthSessions,
            onStartStrength,
            onViewStrengthCompletion,
            onOpenWorkoutManager,
            onReschedule,
            onAddWorkout,
            onAddRest,
            onRemoveEntry,
            onRenamePlan,
            onRenameWorkout,
            onEditRunningEnd,
            onEditRunningTarget)
  }
}

@Composable
private fun LoadedPlanCalendar(
    plan: org.runnerup.workout.RelvenPlanImporter.Plan,
    nowMillis: Long,
    revisionStatus: PlanRevisionStatus,
    completedStrengthSessions: Map<String, CompletedStrengthSession>,
    onStartStrength: (String, PlanCalendarEntry.Workout) -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit,
    onOpenWorkoutManager: () -> Unit,
    onReschedule: (String, String) -> Unit,
    onAddWorkout: (String, String) -> Unit,
    onAddRest: (String) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit,
    onEditRunningEnd: (String, String, JSONObject) -> Unit,
    onEditRunningTarget: (String, String, JSONObject?) -> Unit
) {
  val todayDate = remember(plan.id(), nowMillis) { planLocalDate(plan.timezone(), nowMillis) }
  var selectedDate by rememberSaveable(plan.rootPlanId(), todayDate) { mutableStateOf(todayDate) }
  var month by rememberSaveable(plan.rootPlanId(), todayDate) {
    mutableStateOf(todayDate.substring(0, 7))
  }
  var savedViewMode by rememberSaveable(plan.rootPlanId()) { mutableStateOf(PlanCalendarViewMode.Month.name) }
  val viewMode = PlanCalendarViewMode.valueOf(savedViewMode)
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
  var editingSegment by remember(plan.id()) { mutableStateOf<RunningSegmentSummary?>(null) }
  var editingSegmentWorkoutId by rememberSaveable(plan.id()) { mutableStateOf<String?>(null) }
  var editingTarget by rememberSaveable(plan.id()) { mutableStateOf(false) }
  var editType by rememberSaveable(plan.id()) { mutableStateOf("time") }
  var editMin by rememberSaveable(plan.id()) { mutableStateOf("") }
  var editMax by rememberSaveable(plan.id()) { mutableStateOf("") }
  val locale = LocalLocale.current.platformLocale
  val state = remember(plan, month, selectedDate, todayDate, locale, viewMode, completedStrengthSessions) {
    planCalendarState(plan, month, selectedDate, nowMillis, locale, completedStrengthSessions, viewMode)
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
      CalendarViewModeSelector(
          selectedMode = viewMode,
          enabled = revisionStatus !is PlanRevisionStatus.Saving,
          onSelect = { selected ->
            savedViewMode = selected.name
            month = selectedDate.substring(0, 7)
          })
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
              selectedDate = shiftPlanCalendarDate(selectedDate, viewMode, -1, plan.timezone())
              month = selectedDate.substring(0, 7)
            },
            modifier = Modifier.size(48.dp),
            enabled = revisionStatus !is PlanRevisionStatus.Saving) {
          Icon(
              Icons.Default.ChevronLeft,
              contentDescription = stringResource(R.string.Plan_calendar_previous_period),
              tint = RelvenColors.Text)
        }
        Text(
            state.periodLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold)
        IconButton(
            onClick = {
              selectedDate = shiftPlanCalendarDate(selectedDate, viewMode, 1, plan.timezone())
              month = selectedDate.substring(0, 7)
            },
            modifier = Modifier.size(48.dp),
            enabled = revisionStatus !is PlanRevisionStatus.Saving) {
          Icon(
              Icons.Default.ChevronRight,
              contentDescription = stringResource(R.string.Plan_calendar_next_period),
              tint = RelvenColors.Text)
        }
      }

      if (viewMode == PlanCalendarViewMode.Month) {
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
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          state.days.forEach { day ->
            CalendarAgendaDay(
                day = day,
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

    if (viewMode == PlanCalendarViewMode.Month &&
        state.days.none { it.inDisplayedMonth && it.entries.isNotEmpty() }) {
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
        onStartStrength = { entry -> onStartStrength(plan.id(), entry) },
        onViewStrengthCompletion = onViewStrengthCompletion,
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
        },
        onEditRunningEnd = { workoutId, segment ->
          editingSegment = segment
          editingSegmentWorkoutId = workoutId
          editingTarget = false
          editType = segment.end.type
          editMin = segment.end.value?.toString().orEmpty()
        },
        onEditRunningTarget = { workoutId, segment ->
          editingSegment = segment
          editingSegmentWorkoutId = workoutId
          editingTarget = true
          editType = segment.target?.type ?: "pace"
          editMin = segment.target?.minimum?.toString().orEmpty()
          editMax = segment.target?.maximum?.toString().orEmpty()
        })
  }

  val segmentBeingEdited = editingSegment
  if (segmentBeingEdited != null) {
    val input = if (editingTarget) runningSegmentTargetInput(editType, editMin, editMax)
        else runningSegmentEndInput(editType, editMin)
    AlertDialog(
        onDismissRequest = { editingSegment = null },
        title = { Text(stringResource(if (editingTarget) R.string.Plan_calendar_edit_target else R.string.Plan_calendar_edit_end)) },
        text = {
          Column(
              Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editingTarget) {
              Column {
                listOf(listOf("pace", "speed"), listOf("heart_rate", "none")).forEach { row ->
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.filter { it != "none" || segmentBeingEdited.target != null }.forEach { type ->
                      TextButton(
                          onClick = {
                            editType = type
                            if (type == "none") { editMin = ""; editMax = "" }
                          },
                          modifier = Modifier.semantics {
                            selected = editType == type
                          }) { Text(stringResource(targetTypeLabel(type))) }
                    }
                  }
                }
              }
              if (editType != "none") {
                Text(stringResource(targetUnitLabel(editType)), color = RelvenColors.MutedText)
                OutlinedTextField(editMin, { editMin = it }, label = { Text(stringResource(R.string.Plan_calendar_minimum)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(editMax, { editMax = it }, label = { Text(stringResource(R.string.Plan_calendar_maximum)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
              }
            } else {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("time", "distance", "manual").forEach { type ->
                  TextButton(onClick = { editType = type; if (type == "manual") editMin = "" }) { Text(stringResource(endTypeLabel(type))) }
                }
              }
              if (editType != "manual") OutlinedTextField(editMin, { editMin = it }, label = { Text(if (editType == "time") stringResource(R.string.Plan_calendar_seconds) else stringResource(R.string.Plan_calendar_meters)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            if (input == null && !(editingTarget && editType == "none")) {
              Text(stringResource(R.string.Plan_calendar_edit_invalid), color = RelvenColors.Orange)
            }
          }
        },
        confirmButton = {
          TextButton(onClick = {
            val workoutId = editingSegmentWorkoutId
            if (workoutId != null) {
              if (editingTarget) onEditRunningTarget(workoutId, segmentBeingEdited.id, input)
              else input?.let { onEditRunningEnd(workoutId, segmentBeingEdited.id, it) }
            }
            editingSegment = null
          }, enabled = (input != null || (editingTarget && editType == "none" && segmentBeingEdited.target != null)) && revisionStatus !is PlanRevisionStatus.Saving) { Text(stringResource(R.string.Plan_calendar_save_edit)) }
        },
        dismissButton = { TextButton(onClick = { editingSegment = null }) { Text(stringResource(R.string.Cancel)) } })
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
private fun CalendarViewModeSelector(
    selectedMode: PlanCalendarViewMode,
    enabled: Boolean,
    onSelect: (PlanCalendarViewMode) -> Unit
) {
  val modes = listOf(
      PlanCalendarViewMode.Day to R.string.Plan_calendar_view_day,
      PlanCalendarViewMode.Week to R.string.Plan_calendar_view_week,
      PlanCalendarViewMode.Month to R.string.Plan_calendar_view_month)
  Row(
      modifier = Modifier.fillMaxWidth().background(RelvenColors.Surface, RoundedCornerShape(16.dp)).padding(4.dp)
          .selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    modes.forEach { (mode, label) ->
      val isSelected = mode == selectedMode
      val labelText = stringResource(label)
      Box(
          modifier = Modifier.weight(1f).heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) RelvenColors.Orange else Color.Transparent)
              .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(mode) }
              .semantics {
                this.selected = isSelected
                contentDescription = labelText
              },
          contentAlignment = Alignment.Center) {
        Text(
            labelText,
            color = if (isSelected) RelvenColors.Background else RelvenColors.MutedText,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun CalendarAgendaDay(day: PlanCalendarDay, enabled: Boolean, onClick: () -> Unit) {
  val entryLabels = day.entries.map { entry ->
    when (entry) {
      is PlanCalendarEntry.Workout -> entry.workoutName
      is PlanCalendarEntry.Rest -> stringResource(R.string.Rest_day)
    }
  }
  val descriptionEntries = if (entryLabels.isEmpty()) stringResource(R.string.Plan_calendar_empty_accessibility)
      else entryLabels.joinToString(separator = ", ")
  val accessibilityLabel = stringResource(R.string.Plan_calendar_day_accessibility, day.dateLabel, descriptionEntries)
  val background = if (day.isSelected) RelvenColors.Orange.copy(alpha = 0.16f) else RelvenColors.Surface
  val borderColor = if (day.isToday) RelvenColors.Orange else Color.White.copy(alpha = 0.06f)
  Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
          .clip(RoundedCornerShape(16.dp)).background(background)
          .border(1.dp, borderColor, RoundedCornerShape(16.dp))
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
          .semantics {
            contentDescription = accessibilityLabel
            selected = day.isSelected
          }.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(
        Modifier.size(8.dp).background(
            when {
              day.entries.any { it is PlanCalendarEntry.Workout } -> RelvenColors.Orange
              day.entries.any { it is PlanCalendarEntry.Rest } -> RelvenColors.MutedText
              else -> Color.Transparent
            },
            CircleShape))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(day.dateLabel, color = RelvenColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Text(
          entryLabels.ifEmpty { listOf(stringResource(R.string.Plan_calendar_no_item)) }.joinToString(" · "),
          color = if (entryLabels.isEmpty()) RelvenColors.MutedText else RelvenColors.Text,
          fontSize = 13.sp)
    }
    if (day.isToday) Text(stringResource(R.string.Plan_calendar_today), color = RelvenColors.Orange, fontSize = 12.sp)
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
    onStartStrength: (PlanCalendarEntry.Workout) -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit,
    onAdd: () -> Unit,
    onReschedule: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditWorkout: (String, String) -> Unit,
    onEditRunningEnd: (String, RunningSegmentSummary) -> Unit,
    onEditRunningTarget: (String, RunningSegmentSummary) -> Unit
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
                entry.runningSegments.forEach { segment ->
                  Column(
                      Modifier.fillMaxWidth().background(RelvenColors.SurfaceRaised, RoundedCornerShape(14.dp)).padding(14.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(segment.name ?: stringResource(phaseLabel(segment.phase)), fontWeight = FontWeight.SemiBold)
                    Text(segmentEndLabel(segment), color = RelvenColors.MutedText)
                    Text(segment.target?.let { segmentTargetLabel(it) } ?: stringResource(R.string.Plan_calendar_no_target), color = RelvenColors.MutedText)
                    if (segment.repeatCount > 1) Text(stringResource(R.string.Plan_calendar_repeat_count, segment.repeatCount), color = RelvenColors.MutedText)
                    Row {
                      TextButton(onClick = { onEditRunningEnd(entry.workoutId, segment) }, enabled = enabled) { Text(stringResource(R.string.Plan_calendar_edit_end)) }
                      TextButton(onClick = { onEditRunningTarget(entry.workoutId, segment) }, enabled = enabled) { Text(stringResource(R.string.Plan_calendar_edit_target)) }
                    }
                  }
                }
                entry.strengthDetails?.let { details ->
                  Text(
                      stringResource(
                          if (details.sport() == "prehab")
                              R.string.Plan_calendar_pre_hab_workout
                          else R.string.Plan_calendar_strength_workout),
                      color = RelvenColors.Orange,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold)
                  details.exercises().forEachIndexed { index, exercise ->
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .background(RelvenColors.SurfaceRaised, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                      Text(
                          "${index + 1}. ${exercise.name()}",
                          color = RelvenColors.Text,
                          fontWeight = FontWeight.SemiBold)
                      Text(
                          stringResource(
                              R.string.Plan_calendar_strength_prescription,
                              exercise.sets(),
                              exercise.minRepetitions(),
                              exercise.maxRepetitions()),
                          color = RelvenColors.MutedText,
                          fontSize = 13.sp)
                      if (exercise.durationSeconds() != null) {
                        Text(
                            stringResource(
                                R.string.Plan_calendar_strength_timed,
                                exercise.durationSeconds()!!),
                            color = RelvenColors.MutedText,
                            fontSize = 13.sp)
                      }
                      Text(
                          stringResource(R.string.Plan_calendar_strength_rest, exercise.restSeconds()),
                          color = RelvenColors.MutedText,
                          fontSize = 13.sp)
                      Text(exercise.instructions(), color = RelvenColors.Text, fontSize = 14.sp)
                    }
                  }
                  if (entry.completion != null) {
                    Text(
                        stringResource(R.string.Strength_session_completed),
                        color = RelvenColors.Orange,
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onViewStrengthCompletion(entry.completion) }) {
                      Text(stringResource(R.string.Strength_session_view_summary))
                    }
                  } else {
                    TextButton(onClick = { onStartStrength(entry) }, enabled = enabled) {
                      Text(stringResource(R.string.Strength_session_start))
                    }
                  }
                }
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
private fun phaseLabel(phase: String): Int = when (phase) {
  "warmup" -> R.string.Plan_calendar_phase_warmup
  "work" -> R.string.Plan_calendar_phase_work
  "recovery" -> R.string.Plan_calendar_phase_recovery
  "cooldown" -> R.string.Plan_calendar_phase_cooldown
  else -> R.string.Plan_calendar_phase_work
}

private fun targetTypeLabel(type: String): Int = when (type) {
  "pace" -> R.string.Plan_calendar_type_pace
  "speed" -> R.string.Plan_calendar_type_speed
  "heart_rate" -> R.string.Plan_calendar_type_heart_rate
  else -> R.string.Plan_calendar_type_none
}

private fun targetUnitLabel(type: String): Int = when (type) {
  "pace" -> R.string.Plan_calendar_unit_pace
  "speed" -> R.string.Plan_calendar_unit_speed
  else -> R.string.Plan_calendar_unit_heart_rate
}

private fun endTypeLabel(type: String): Int = when (type) {
  "time" -> R.string.Plan_calendar_type_time
  "distance" -> R.string.Plan_calendar_type_distance
  else -> R.string.Plan_calendar_type_manual
}

@Composable
private fun segmentEndLabel(segment: RunningSegmentSummary): String = when (segment.end.type) {
  "time" -> stringResource(R.string.Plan_calendar_end_seconds, segment.end.value ?: 0.0)
  "distance" -> stringResource(R.string.Plan_calendar_end_meters, segment.end.value ?: 0.0)
  else -> stringResource(R.string.Plan_calendar_end_manual)
}

@Composable
private fun segmentTargetLabel(target: RunningSegmentTarget): String = when (target.type) {
  "pace" -> stringResource(R.string.Plan_calendar_target_pace, target.minimum, target.maximum)
  "speed" -> stringResource(R.string.Plan_calendar_target_speed, target.minimum, target.maximum)
  else -> stringResource(R.string.Plan_calendar_target_heart_rate, target.minimum, target.maximum)
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
