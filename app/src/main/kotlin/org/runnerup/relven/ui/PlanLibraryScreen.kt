package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.R
import org.runnerup.workout.RelvenPlanImporter

internal data class PlanLibraryState(
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val plans: List<RelvenPlanImporter.Plan> = emptyList(),
    val activePlanId: String? = null)

internal data class PendingPlanImport(
    val plan: RelvenPlanImporter.Plan,
    val summary: String)

internal fun libraryPreviewPlan(
    plans: List<RelvenPlanImporter.Plan>,
    selectedPlanId: String?,
    activePlanId: String?): RelvenPlanImporter.Plan? =
    plans.firstOrNull { it.id() == selectedPlanId }
        ?: plans.firstOrNull { it.id() == activePlanId }
        ?: plans.firstOrNull()

@Composable
internal fun PlanLibraryScreen(
    state: PlanLibraryState,
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
    onOpenCalendar: () -> Unit) {
  val weekdays = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekdays)
  val weekdayNames = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekday_names)
  val locale = LocalLocale.current.platformLocale
  var previewPlanId by rememberSaveable { mutableStateOf<String?>(null) }
  val previewPlan = remember(state.plans, previewPlanId, state.activePlanId) {
    libraryPreviewPlan(state.plans, previewPlanId, state.activePlanId)
  }
  val previewToday = previewPlan?.let { planLocalDate(it.timezone(), currentTimeMillis) }
      ?: PlanCalendarMonth.now().key() + "-01"
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
  LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
          start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
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
                enabled = previewPlan != null,
                onClick = {
                  previewPlan?.let {
                    val shifted = shiftPlanCalendarMonth(month, selectedDate, -1, it.timezone())
                    month = shifted.first
                    selectedDate = shifted.second
                  }
                }) {
            Icon(Icons.Default.ChevronLeft,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_previous_period),
                tint = RelvenColors.MutedText)
            }
            IconButton(
                enabled = previewPlan != null,
                onClick = {
                  previewPlan?.let {
                    val shifted = shiftPlanCalendarMonth(month, selectedDate, 1, it.timezone())
                    month = shifted.first
                    selectedDate = shifted.second
                  }
                }) {
            Icon(Icons.Default.ChevronRight,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_next_period),
                tint = RelvenColors.MutedText)
            }
          }
          if (previewPlan == null) {
            Text(stringResource(R.string.plan_library_calendar_no_plan), color = RelvenColors.MutedText, fontSize = 14.sp)
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
    } else if (state.plans.isEmpty()) {
      item { Text(stringResource(R.string.plan_library_no_saved), color = RelvenColors.MutedText) }
    } else {
      items(state.plans, key = { it.id() }) { plan ->
        val active = state.activePlanId == plan.id()
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
            }
            val dates = plan.days().map { it.date() }.sorted()
            Text(
                "${dates.first()} – ${dates.last()}  ·  " +
                    pluralStringResource(R.plurals.plan_library_workouts, plan.workoutNames().size, plan.workoutNames().size) +
                    " · " + pluralStringResource(R.plurals.plan_library_training_days, plan.workoutDays().toInt(), plan.workoutDays().toInt()) +
                    " · " + pluralStringResource(R.plurals.plan_library_rest_days, plan.restDays().toInt(), plan.restDays().toInt()),
                color = RelvenColors.MutedText, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                  enabled = !busy,
                  onClick = { previewPlanId = plan.id() }) {
                Text(stringResource(
                    if (previewPlan?.id() == plan.id()) R.string.plan_library_previewing
                    else R.string.plan_library_preview_calendar))
              }
              if (!active) TextButton(enabled = !busy, onClick = { onActivate(plan) }) {
                Text(stringResource(R.string.plan_library_activate))
              }
              else TextButton(enabled = !busy, onClick = onClearActive) {
                Text(stringResource(R.string.plan_library_clear))
              }
              if (active) TextButton(onClick = onOpenCalendar) {
                Text(stringResource(R.string.plan_library_calendar))
              }
            }
          }
        }
      }
    }
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
