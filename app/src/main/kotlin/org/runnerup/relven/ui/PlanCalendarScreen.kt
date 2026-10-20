package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
    onOpenWorkoutManager: () -> Unit
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
        LoadedPlanCalendar(loadState.plan, nowMillis, onOpenWorkoutManager)
  }
}

@Composable
private fun LoadedPlanCalendar(
    plan: org.runnerup.workout.RelvenPlanImporter.Plan,
    nowMillis: Long,
    onOpenWorkoutManager: () -> Unit
) {
  val todayDate = remember(plan.id(), nowMillis) { planLocalDate(plan.timezone(), nowMillis) }
  var selectedDate by rememberSaveable(plan.id(), todayDate) { mutableStateOf(todayDate) }
  var month by rememberSaveable(plan.id(), todayDate) {
    mutableStateOf(todayDate.substring(0, 7))
  }
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
      Text(plan.name(), color = RelvenColors.MutedText, fontSize = 14.sp)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
              val shifted = shiftPlanCalendarMonth(month, selectedDate, -1, plan.timezone())
              month = shifted.first
              selectedDate = shifted.second
            },
            modifier = Modifier.size(48.dp)) {
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
            modifier = Modifier.size(48.dp)) {
          Icon(
              Icons.Default.ChevronRight,
              contentDescription = stringResource(R.string.Plan_calendar_next_month),
              tint = RelvenColors.Text)
        }
      }

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          week.forEach { day ->
            CalendarDateCell(
                day = day,
                modifier = Modifier.weight(1f),
                onClick = {
                  selectedDate = day.date
                  month = day.date.substring(0, 7)
                })
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
        entries = state.selectedEntries)
  }
}

@Composable
private fun CalendarDateCell(
    day: PlanCalendarDay,
    modifier: Modifier,
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
          .clickable(role = Role.Button, onClick = onClick),
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
    entries: List<PlanCalendarEntry>
) {
  Surface(
      shape = RoundedCornerShape(24.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
      modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(dateLabel, color = RelvenColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Text(planName, color = RelvenColors.MutedText, fontSize = 14.sp)
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
              }
              is PlanCalendarEntry.Rest -> {
                Text(stringResource(R.string.Rest_day), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (entry.notes.isNotBlank()) {
                  Text(entry.notes, color = RelvenColors.MutedText, fontSize = 14.sp)
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
