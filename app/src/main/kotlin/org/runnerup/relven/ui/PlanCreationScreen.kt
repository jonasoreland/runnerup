package org.runnerup.relven.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.runnerup.R

@Composable
internal fun PlanCreationScreen(
    busy: Boolean,
    errorMessage: String?,
    activationPending: Boolean,
    onBack: () -> Unit,
    onRetryActivation: () -> Unit,
    onDraftChanged: () -> Unit,
    onCreate: (String, String, Int, List<PlanDraftSession>) -> Unit) {
  val context = LocalContext.current
  val locale = LocalLocale.current.platformLocale
  val wireDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
  var name by rememberSaveable { mutableStateOf("") }
  var startDate by rememberSaveable { mutableStateOf(wireDateFormat.format(Date())) }
  var weeksText by rememberSaveable { mutableStateOf("4") }
  var days by rememberSaveable {
    mutableStateOf(mapOf(
        Calendar.MONDAY to PlanWorkoutType.Easy,
        Calendar.WEDNESDAY to PlanWorkoutType.Intervals,
        Calendar.SATURDAY to PlanWorkoutType.LongRun))
  }
  var validationMessage by remember { mutableStateOf<String?>(null) }
  var dirty by rememberSaveable { mutableStateOf(false) }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  val handleBack: () -> Unit = {
    if (!busy) {
      if (dirty && !activationPending) confirmDiscard = true else onBack()
    }
  }
  val markDraftChanged: () -> Unit = {
    dirty = true
    validationMessage = null
    onDraftChanged()
  }
  BackHandler(onBack = handleBack)
  val weekdays = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekdays)
  val weekdayNames = stringArrayResource(org.runnerup.common.R.array.Plan_calendar_weekday_names)
  val nameError = stringResource(R.string.plan_create_name_error)
  val durationError = stringResource(R.string.plan_create_duration_error)
  val daysError = stringResource(R.string.plan_create_days_error)
  val dateMillis = remember(startDate) {
    runCatching { wireDateFormat.parse(startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
  }
  val displayDate = remember(dateMillis, locale) {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(Date(dateMillis))
  }
  val weeks = weeksText.toIntOrNull()
  val validName = name.trim().isNotEmpty() && name.trim().length <= 80
  val validWeeks = weeks != null && weeks in 1..16
  Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 132.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)) {
    TextButton(onClick = handleBack, enabled = !busy) { Text(stringResource(R.string.plan_create_back)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.plan_create_title), fontSize = 29.sp, fontWeight = FontWeight.Bold)
      Text(stringResource(R.string.plan_create_subtitle), color = RelvenColors.MutedText, fontSize = 14.sp)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(120); validationMessage = null; markDraftChanged() },
        enabled = !busy && !activationPending,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.plan_create_name)) },
        singleLine = true,
        isError = name.isNotBlank() && !validName,
        supportingText = {
          if (name.isNotBlank() && !validName) Text(stringResource(R.string.plan_create_name_error))
        })
    Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.plan_create_start), fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
              val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
              DatePickerDialog(
                      context,
                      { _, year, month, day ->
                        val chosen = Calendar.getInstance().apply {
                          set(year, month, day, 12, 0, 0)
                          set(Calendar.MILLISECOND, 0)
                        }
                        startDate = wireDateFormat.format(chosen.time)
                        markDraftChanged()
                      },
                      calendar.get(Calendar.YEAR),
                      calendar.get(Calendar.MONTH),
                      calendar.get(Calendar.DAY_OF_MONTH))
                  .show()
            },
            enabled = !busy && !activationPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
          Text(displayDate)
        }
        OutlinedTextField(
            value = weeksText,
            onValueChange = {
              weeksText = it.filter(Char::isDigit).take(2)
              validationMessage = null
              markDraftChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !activationPending,
            label = { Text(stringResource(R.string.plan_create_duration)) },
            suffix = { Text(stringResource(R.string.plan_create_weeks_suffix)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = weeksText.isNotBlank() && !validWeeks,
            supportingText = {
              if (weeksText.isNotBlank() && !validWeeks) Text(stringResource(R.string.plan_create_duration_error))
            })
      }
    }
    Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.plan_create_training_days), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.plan_create_training_days_help), color = RelvenColors.MutedText, fontSize = 13.sp)
        weekdays.forEachIndexed { index, label ->
          val dayOfWeek = ((index + 1) % 7) + 1
          val type = days[dayOfWeek]
          Surface(
              color = RelvenColors.Background.copy(alpha = 0.45f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = weekdayNames[index]
                    selected = type != null
                  }.clickable(
                      enabled = !busy && !activationPending,
                      role = Role.Checkbox,
                      onClick = {
                        days = if (type == null) days + (dayOfWeek to PlanWorkoutType.Easy)
                        else days - dayOfWeek
                        validationMessage = null
                        markDraftChanged()
                      }).padding(vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = if (type != null) RelvenColors.Orange else RelvenColors.Surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RelvenColors.Text.copy(alpha = 0.12f)),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                  Text(label, color = if (type != null) RelvenColors.Background else RelvenColors.Text,
                      fontWeight = FontWeight.SemiBold)
                }
                Text(weekdayNames[index], color = RelvenColors.MutedText, fontSize = 14.sp)
              }
              if (type != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                  PlanWorkoutType.values().forEach { option ->
                    val selected = type == option
                    val labelText = stringResource(when (option) {
                      PlanWorkoutType.Easy -> R.string.plan_create_easy
                      PlanWorkoutType.Intervals -> R.string.plan_create_intervals
                      PlanWorkoutType.LongRun -> R.string.plan_create_long_run
                    })
                    Surface(
                        color = if (selected) RelvenColors.Orange.copy(alpha = 0.17f) else RelvenColors.Surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) RelvenColors.Orange else ColorBorder),
                        modifier = Modifier.weight(1f).semantics { this.selected = selected }.clickable(
                            enabled = !busy && !activationPending,
                            role = Role.RadioButton,
                            onClick = {
                              days = days + (dayOfWeek to option)
                              markDraftChanged()
                            })) {
                      Text(labelText, modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
                          color = if (selected) RelvenColors.Orange else RelvenColors.MutedText,
                          fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                  }
                }
                Text(
                    stringResource(when (type) {
                      PlanWorkoutType.Easy -> R.string.plan_create_easy_summary
                      PlanWorkoutType.Intervals -> R.string.plan_create_intervals_summary
                      PlanWorkoutType.LongRun -> R.string.plan_create_long_run_summary
                    }),
                    color = RelvenColors.MutedText,
                    fontSize = 12.sp)
              }
            }
          }
        }
      }
    }
    Surface(
        color = RelvenColors.Surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
      Text(
          stringResource(
              R.string.plan_create_preview,
              pluralStringResource(R.plurals.plan_create_weeks, weeks ?: 0, weeks ?: 0),
              pluralStringResource(R.plurals.plan_create_training_days_count, days.size, days.size)),
          modifier = Modifier.padding(16.dp), color = RelvenColors.MutedText, fontSize = 14.sp)
    }
    (validationMessage ?: errorMessage)?.let { message ->
      Text(
          message,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          color = MaterialTheme.colorScheme.error,
          fontSize = 14.sp)
    }
    if (activationPending) {
      Button(
          onClick = onRetryActivation,
          enabled = !busy,
          colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
          modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Text(stringResource(if (busy) R.string.plan_create_saving else R.string.plan_create_retry_activation))
      }
    } else Button(
        onClick = {
          validationMessage = when {
            !validName -> nameError
            !validWeeks -> durationError
            days.isEmpty() -> daysError
            else -> null
          }
          if (validationMessage == null && weeks != null) {
            onCreate(name.trim(), startDate, weeks, days.map { PlanDraftSession(it.key, it.value) })
          }
        },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
      Text(stringResource(if (busy) R.string.plan_create_saving else R.string.plan_create_save))
    }
    OutlinedButton(onClick = handleBack, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
      Text(stringResource(R.string.plan_library_cancel))
    }
  }
  if (confirmDiscard) {
    AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.plan_create_discard_title)) },
        text = { Text(stringResource(R.string.plan_create_discard_message)) },
        confirmButton = {
          TextButton(onClick = { confirmDiscard = false; onBack() }) {
            Text(stringResource(R.string.plan_create_discard))
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(R.string.plan_create_keep_editing))
          }
        })
  }
}

private val ColorBorder get() = RelvenColors.Text.copy(alpha = 0.12f)
