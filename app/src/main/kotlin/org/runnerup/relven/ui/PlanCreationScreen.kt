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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
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
import org.runnerup.relven.exercise.ExerciseEntry

@Composable
internal fun PlanCreationScreen(
    busy: Boolean,
    errorMessage: String?,
    activationPending: Boolean,
    exerciseCatalogLoadState: ExerciseCatalogLoadState,
    onBack: () -> Unit,
    onRetryActivation: () -> Unit,
    onRetryExerciseCatalog: () -> Unit,
    onDraftChanged: () -> Unit,
    onCreate: (String, String, Int, List<PlanDraftSession>, List<PlanDraftStrengthExercise>) -> Unit) {
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
  var strengthExercises by rememberSaveable(
      stateSaver = listSaver<List<PlanDraftStrengthExercise>, String>(
          save = { values -> values.flatMap { listOf(
              it.exerciseId, it.sets.toString(), it.minRepetitions.toString(),
              it.maxRepetitions.toString(), it.restSeconds.toString(),
              it.durationSeconds?.toString() ?: "-") } },
          restore = { values -> values.chunked(6).mapNotNull { row ->
            if (row.size != 6) null else runCatching {
              PlanDraftStrengthExercise(
                  row[0], row[1].toInt(), row[2].toInt(), row[3].toInt(), row[4].toInt(),
                  row[5].takeUnless { it == "-" }?.toInt())
            }.getOrNull()
          } })) { mutableStateOf(emptyList()) }
  var invalidStrengthInputKeys by rememberSaveable(
      stateSaver = listSaver<Set<String>, String>(
          save = { it.toList() },
          restore = { it.toSet() })) { mutableStateOf(emptySet()) }
  var showExercisePicker by rememberSaveable { mutableStateOf(false) }
  var exerciseQuery by rememberSaveable { mutableStateOf("") }
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
  val strengthExercisesError = stringResource(R.string.plan_create_strength_exercises_error)
  val dateMillis = remember(startDate) {
    runCatching { wireDateFormat.parse(startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
  }
  val displayDate = remember(dateMillis, locale) {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(Date(dateMillis))
  }
  val weeks = weeksText.toIntOrNull()
  val validName = name.trim().isNotEmpty() && name.trim().length <= 80
  val validWeeks = weeks != null && weeks in 1..16
  val hasStrengthDays = days.values.any {
    it == PlanWorkoutType.Strength || it == PlanWorkoutType.Prehab
  }
  val validStrengthPrescription = invalidStrengthInputKeys.isEmpty() && strengthExercises.isNotEmpty() && strengthExercises.all {
    it.sets in 1..100 && it.minRepetitions in 1..500 &&
        it.maxRepetitions in it.minRepetitions..500 && it.restSeconds in 0..3600 &&
        (it.durationSeconds?.let { seconds -> seconds in 1..86_400 } ?: true)
  }
  val availableExercises = (exerciseCatalogLoadState as? ExerciseCatalogLoadState.Loaded)
      ?.catalog?.exercises?.filter { !it.isArchived && it.equipment.isEmpty() }.orEmpty()
  Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp).padding(top = 24.dp),
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
        when (exerciseCatalogLoadState) {
          ExerciseCatalogLoadState.Loading ->
              Text(stringResource(org.runnerup.common.R.string.Exercise_catalog_loading), color = RelvenColors.MutedText)
          ExerciseCatalogLoadState.Unavailable -> {
            Text(stringResource(R.string.plan_create_strength_catalog_unavailable),
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetryExerciseCatalog, enabled = !busy) {
              Text(stringResource(R.string.plan_create_strength_retry_catalog))
            }
          }
          is ExerciseCatalogLoadState.Loaded -> if (availableExercises.isEmpty()) {
            Text(stringResource(R.string.plan_create_strength_no_exercises), color = RelvenColors.MutedText)
          }
        }
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
                PlanWorkoutType.values().toList().chunked(3).forEach { rowOptions ->
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                  rowOptions.forEach { option ->
                    val selected = type == option
                    val labelText = stringResource(when (option) {
                      PlanWorkoutType.Easy -> R.string.plan_create_easy
                      PlanWorkoutType.Intervals -> R.string.plan_create_intervals
                      PlanWorkoutType.LongRun -> R.string.plan_create_long_run
                      PlanWorkoutType.Strength -> R.string.plan_create_strength
                      PlanWorkoutType.Prehab -> R.string.plan_create_prehab
                    })
                    Surface(
                        color = if (selected) RelvenColors.Orange.copy(alpha = 0.17f) else RelvenColors.Surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) RelvenColors.Orange else ColorBorder),
                        modifier = Modifier.weight(1f).semantics { this.selected = selected }.clickable(
                            enabled = !busy && !activationPending &&
                                (option !in setOf(PlanWorkoutType.Strength, PlanWorkoutType.Prehab) ||
                                    (exerciseCatalogLoadState is ExerciseCatalogLoadState.Loaded &&
                                        availableExercises.isNotEmpty())),
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
                }
                Text(stringResource(when (type) {
                  PlanWorkoutType.Easy -> R.string.plan_create_easy_summary
                  PlanWorkoutType.Intervals -> R.string.plan_create_intervals_summary
                  PlanWorkoutType.LongRun -> R.string.plan_create_long_run_summary
                  PlanWorkoutType.Strength -> R.string.plan_create_strength_summary
                  PlanWorkoutType.Prehab -> R.string.plan_create_prehab_summary
                }), color = RelvenColors.MutedText, fontSize = 12.sp)
              }
            }
          }
        }
      }
    }
    if (hasStrengthDays) {
      Surface(color = RelvenColors.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.plan_create_strength_exercises), fontWeight = FontWeight.SemiBold)
          Text(stringResource(R.string.plan_create_strength_exercises_help),
              color = RelvenColors.MutedText, fontSize = 13.sp)
          when (exerciseCatalogLoadState) {
            ExerciseCatalogLoadState.Loading -> Unit
            ExerciseCatalogLoadState.Unavailable -> Unit
            is ExerciseCatalogLoadState.Loaded -> {
              if (availableExercises.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showExercisePicker = true },
                    enabled = !busy && !activationPending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                  Text(stringResource(R.string.plan_create_strength_add_exercise))
                }
                strengthExercises.forEach { prescription ->
                  val exercise = availableExercises.firstOrNull { it.exerciseId == prescription.exerciseId }
                  if (exercise != null) {
                    key(prescription.exerciseId) {
                      StrengthExercisePrescriptionCard(
                          exercise = exercise,
                          prescription = prescription,
                          enabled = !busy && !activationPending,
                          onChange = { updated ->
                            strengthExercises = strengthExercises.map {
                              if (it.exerciseId == updated.exerciseId) updated else it
                            }
                            markDraftChanged()
                          },
                          onRemove = {
                            strengthExercises = strengthExercises.filterNot { it.exerciseId == prescription.exerciseId }
                            invalidStrengthInputKeys = invalidStrengthInputKeys.filterNot {
                              it.startsWith("${prescription.exerciseId}:")
                            }.toSet()
                            markDraftChanged()
                          },
                          onInputValidityChange = { fieldKey, valid ->
                            invalidStrengthInputKeys = if (valid) {
                              invalidStrengthInputKeys - fieldKey
                            } else {
                              invalidStrengthInputKeys + fieldKey
                            }
                          })
                    }
                  }
                }
              }
            }
          }
          if (validationMessage == strengthExercisesError) {
            Text(strengthExercisesError, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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
            hasStrengthDays && !validStrengthPrescription -> strengthExercisesError
            else -> null
          }
          if (validationMessage == null && weeks != null) {
            onCreate(
                name.trim(), startDate, weeks,
                days.map { PlanDraftSession(it.key, it.value) }, strengthExercises)
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
  if (showExercisePicker) {
    val query = exerciseQuery.trim().lowercase(locale)
    val visibleExercises = availableExercises.filter { exercise ->
      query.isBlank() || exercise.name.lowercase(locale).contains(query) ||
          exercise.instructions.any { it.lowercase(locale).contains(query) }
    }
    AlertDialog(
        onDismissRequest = { showExercisePicker = false },
        title = { Text(stringResource(R.string.plan_create_strength_picker_title)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = exerciseQuery,
                onValueChange = { exerciseQuery = it },
                label = { Text(stringResource(R.string.plan_create_strength_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
              items(visibleExercises, key = ExerciseEntry::exerciseId) { exercise ->
                val selected = strengthExercises.any { it.exerciseId == exercise.exerciseId }
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }.clickable(
                        enabled = !busy && !activationPending,
                        role = Role.Checkbox,
                        onClick = {
                          strengthExercises = if (selected) {
                            strengthExercises.filterNot { it.exerciseId == exercise.exerciseId }
                          } else {
                            strengthExercises + PlanDraftStrengthExercise(exercise.exerciseId)
                          }
                          if (selected) {
                            invalidStrengthInputKeys = invalidStrengthInputKeys.filterNot {
                              it.startsWith("${exercise.exerciseId}:")
                            }.toSet()
                          }
                          markDraftChanged()
                        }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(
                      checked = selected,
                      enabled = !busy && !activationPending,
                      onCheckedChange = null)
                  Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showExercisePicker = false }) {
            Text(stringResource(R.string.plan_create_strength_done))
          }
        })
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

@Composable
private fun StrengthExercisePrescriptionCard(
    exercise: ExerciseEntry,
    prescription: PlanDraftStrengthExercise,
    enabled: Boolean,
    onChange: (PlanDraftStrengthExercise) -> Unit,
    onRemove: () -> Unit,
    onInputValidityChange: (String, Boolean) -> Unit
) {
  Surface(color = RelvenColors.Background.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onRemove, enabled = enabled) {
          Text(stringResource(R.string.plan_create_strength_remove))
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StrengthNumberField(
            fieldKey = "${prescription.exerciseId}:sets",
            value = prescription.sets,
            label = stringResource(R.string.plan_create_strength_sets),
            minValue = 1,
            maxValue = 100,
            enabled = enabled,
            onInputValidityChange = onInputValidityChange,
            onValueChange = { onChange(prescription.copy(sets = it)) },
            modifier = Modifier.weight(1f))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.plan_create_strength_timed), modifier = Modifier.weight(1f), fontSize = 13.sp)
          Switch(
              checked = prescription.durationSeconds != null,
              enabled = enabled,
              onCheckedChange = { timed ->
                val hiddenFields = if (timed) {
                  listOf("minRepetitions", "maxRepetitions")
                } else {
                  listOf("duration")
                }
                hiddenFields.forEach { field ->
                  onInputValidityChange("${prescription.exerciseId}:$field", true)
                }
                onChange(prescription.copy(durationSeconds = if (timed) 30 else null))
              })
        }
      }
      if (prescription.durationSeconds != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StrengthNumberField(
              fieldKey = "${prescription.exerciseId}:duration",
              value = prescription.durationSeconds,
              label = stringResource(R.string.plan_create_strength_duration),
              minValue = 1,
              maxValue = 86_400,
              enabled = enabled,
              onInputValidityChange = onInputValidityChange,
              onValueChange = { onChange(prescription.copy(durationSeconds = it)) },
              modifier = Modifier.weight(1f))
          StrengthNumberField(
              fieldKey = "${prescription.exerciseId}:rest",
              value = prescription.restSeconds,
              label = stringResource(R.string.plan_create_strength_rest),
              minValue = 0,
              maxValue = 3600,
              enabled = enabled,
              onInputValidityChange = onInputValidityChange,
              onValueChange = { onChange(prescription.copy(restSeconds = it)) },
              modifier = Modifier.weight(1f))
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StrengthNumberField(
              fieldKey = "${prescription.exerciseId}:minRepetitions",
              value = prescription.minRepetitions,
              label = stringResource(R.string.plan_create_strength_min_reps),
              minValue = 1,
              maxValue = 500,
              enabled = enabled,
              onInputValidityChange = onInputValidityChange,
              onValueChange = {
                onChange(prescription.copy(minRepetitions = it, maxRepetitions = maxOf(it, prescription.maxRepetitions)))
              },
              modifier = Modifier.weight(1f))
          StrengthNumberField(
              fieldKey = "${prescription.exerciseId}:maxRepetitions",
              value = prescription.maxRepetitions,
              label = stringResource(R.string.plan_create_strength_max_reps),
              minValue = prescription.minRepetitions,
              maxValue = 500,
              enabled = enabled,
              onInputValidityChange = onInputValidityChange,
              onValueChange = {
                onChange(prescription.copy(maxRepetitions = it, minRepetitions = minOf(it, prescription.minRepetitions)))
              },
              modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StrengthNumberField(
            fieldKey = "${prescription.exerciseId}:rest",
            value = prescription.restSeconds,
            label = stringResource(R.string.plan_create_strength_rest),
            minValue = 0,
            maxValue = 3600,
            enabled = enabled,
            onInputValidityChange = onInputValidityChange,
            onValueChange = { onChange(prescription.copy(restSeconds = it)) },
            modifier = Modifier.weight(1f))
        }
      }
      val valid = prescription.sets in 1..100 &&
          prescription.restSeconds in 0..3600 &&
          (prescription.durationSeconds?.let { it in 1..86_400 } ?:
              (prescription.minRepetitions in 1..500 &&
                  prescription.maxRepetitions in prescription.minRepetitions..500))
      if (!valid) {
        Text(stringResource(R.string.plan_create_strength_prescription_error),
            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun StrengthNumberField(
    fieldKey: String,
    value: Int,
    label: String,
    minValue: Int,
    maxValue: Int,
    enabled: Boolean,
    onInputValidityChange: (String, Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
  var text by rememberSaveable(label) { mutableStateOf(value.toString()) }
  LaunchedEffect(value) { text = value.toString() }
  LaunchedEffect(fieldKey, text, minValue, maxValue) {
    onInputValidityChange(fieldKey, isPlanStrengthNumberInputValid(text, minValue, maxValue))
  }
  OutlinedTextField(
      value = text,
      onValueChange = { input ->
        text = input.filter(Char::isDigit).take(5)
        onInputValidityChange(fieldKey, isPlanStrengthNumberInputValid(text, minValue, maxValue))
        text.toIntOrNull()?.let(onValueChange)
      },
      enabled = enabled,
      modifier = modifier,
      label = { Text(label) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true)
}

internal fun isPlanStrengthNumberInputValid(input: String, min: Int, max: Int): Boolean =
    input.toIntOrNull()?.let { it in min..max } == true

private val ColorBorder get() = RelvenColors.Text.copy(alpha = 0.12f)
