package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
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

@Composable
internal fun PlanLibraryScreen(
    state: PlanLibraryState,
    busy: Boolean,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onCreateWithAi: () -> Unit,
    onActivate: (RelvenPlanImporter.Plan) -> Unit,
    onClearActive: () -> Unit,
    onOpenCalendar: () -> Unit) {
  var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
  val displayedMonth = remember(month) { YearMonth.parse(month) }
  val weekdays = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekdays)
  val locale = Locale.getDefault()
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
      Surface(
          color = RelvenColors.Surface,
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.28f)),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                displayedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                modifier = Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = displayedMonth.minusMonths(1).toString() }) {
            Icon(Icons.Default.ChevronLeft,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_previous_period),
                tint = RelvenColors.MutedText)
            }
            IconButton(onClick = { month = displayedMonth.plusMonths(1).toString() }) {
            Icon(Icons.Default.ChevronRight,
                contentDescription = stringResource(org.runnerup.common.R.string.Plan_calendar_next_period),
                tint = RelvenColors.MutedText)
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdays.forEach { day ->
              Text(day, modifier = Modifier.weight(1f), color = RelvenColors.MutedText,
                  fontSize = 12.sp, textAlign = TextAlign.Center)
            }
          }
          emptyPlanCalendarDays(displayedMonth).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              week.forEach { day ->
                val isToday = day == java.time.LocalDate.now().dayOfMonth &&
                    displayedMonth == YearMonth.now()
                Box(
                    Modifier.weight(1f).heightIn(min = 42.dp)
                        .background(
                            if (isToday) RelvenColors.Orange.copy(alpha = 0.18f)
                            else RelvenColors.Background.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                  if (day != null) Text(day.toString(),
                      color = if (isToday) RelvenColors.Orange else RelvenColors.MutedText,
                      fontSize = 13.sp,
                      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                }
              }
            }
          }
          Text(stringResource(R.string.plan_library_calendar_empty), color = RelvenColors.MutedText, fontSize = 14.sp)
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
                "${dates.first()} – ${dates.last()}  ·  " + stringResource(
                    R.string.plan_library_counts,
                    plan.workoutNames().size,
                    plan.workoutDays().toInt(),
                    plan.restDays().toInt()),
                color = RelvenColors.MutedText, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
