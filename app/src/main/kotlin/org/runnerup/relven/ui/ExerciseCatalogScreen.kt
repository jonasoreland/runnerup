package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import org.runnerup.common.R
import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.exercise.ExerciseCategory
import org.runnerup.relven.exercise.ExerciseEntry
import org.runnerup.relven.exercise.CustomExerciseInput
import org.runnerup.relven.strength.ExerciseProgressSession

internal sealed interface ExerciseCatalogLoadState {
  data object Loading : ExerciseCatalogLoadState

  data class Loaded(val catalog: ExerciseCatalog) : ExerciseCatalogLoadState

  data object Unavailable : ExerciseCatalogLoadState
}

@Composable
internal fun ExerciseCatalogScreen(
    loadState: ExerciseCatalogLoadState,
    progressionState: ExerciseProgressionLoadState = ExerciseProgressionLoadState.Loading,
    onRetry: () -> Unit,
    onRetryProgression: () -> Unit = {},
    onSaveCustomExercise: suspend (String?, CustomExerciseInput) -> String = { id, _ -> id.orEmpty() },
    favoriteExerciseIds: Set<String> = emptySet(),
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> }
) {
  when (loadState) {
    ExerciseCatalogLoadState.Loading ->
        CatalogMessage(stringResource(R.string.Exercise_catalog_loading))
    ExerciseCatalogLoadState.Unavailable ->
        CatalogError(onRetry)
    is ExerciseCatalogLoadState.Loaded ->
        CatalogBrowser(
            loadState.catalog,
            progressionState,
            onRetryProgression,
            onSaveCustomExercise,
            favoriteExerciseIds,
            onToggleFavorite)
  }
}

@Composable
private fun CatalogBrowser(
    catalog: ExerciseCatalog,
    progressionState: ExerciseProgressionLoadState,
    onRetryProgression: () -> Unit,
    onSaveCustomExercise: suspend (String?, CustomExerciseInput) -> String,
    favoriteExerciseIds: Set<String>,
    onToggleFavorite: (String, Boolean) -> Unit) {
  var query by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  var categoryKey by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  var favoritesOnly by rememberSaveable(catalog.libraryRevision) { mutableStateOf(false) }
  var selectedExerciseId by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  val selectedExercise =
      ExerciseCatalogViewState(selectedExerciseId = selectedExerciseId.ifBlank { null })
          .selectedExercise(catalog)
  var editing by rememberSaveable(catalog.libraryRevision) { mutableStateOf(false) }
  var formSaving by remember { mutableStateOf(false) }
  var formError by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  if (editing) {
    CustomExerciseEditor(
        initial = selectedExercise?.customInput,
        saving = formSaving,
        error = formError,
        onCancel = { editing = false; formError = null },
        onSave = { input ->
          scope.launch {
            formSaving = true
            formError = null
            try {
              val savedId = onSaveCustomExercise(selectedExercise?.exerciseId, input)
              editing = false
              selectedExerciseId = savedId
            } catch (_: Exception) {
              formError = "save"
            } finally {
              formSaving = false
            }
          }
        })
    return
  }

  if (selectedExercise != null) {
    ExerciseDetail(
        selectedExercise,
        progressionState,
        onRetryProgression,
        onBack = { selectedExerciseId = "" },
        onEdit = if (selectedExercise.isCustom) { { editing = true } } else null)
    return
  }

  val category = ExerciseCategory.fromKey(categoryKey)
  val state = ExerciseCatalogViewState(
      query = query,
      category = category,
      favoriteExerciseIds = favoriteExerciseIds,
      favoritesOnly = favoritesOnly)
  val matches = state.visibleEntries(catalog)
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
      Text(
          stringResource(R.string.Exercise_catalog_title),
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold)
      Text(
          stringResource(R.string.Exercise_catalog_subtitle, catalog.exercises.size),
          color = RelvenColors.MutedText,
          fontSize = 14.sp)
      Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Exercise_custom_add))
      }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.Exercise_catalog_search_label)) },
        placeholder = { Text(stringResource(R.string.Exercise_catalog_search_hint)) },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(
                  Icons.Default.Close,
                  contentDescription = stringResource(R.string.Exercise_catalog_clear_search))
            }
          }
        })
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
          selected = favoritesOnly,
          onClick = { favoritesOnly = !favoritesOnly },
          label = { Text(stringResource(R.string.Exercise_catalog_favorites)) })
      CategoryChip(null, categoryKey.isEmpty()) { categoryKey = "" }
      ExerciseCategory.entries.forEach { item ->
        CategoryChip(item, categoryKey == item.key) { categoryKey = item.key }
      }
    }
    if (matches.isEmpty()) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(
          stringResource(
              if (favoritesOnly) R.string.Exercise_catalog_no_favorites
              else R.string.Exercise_catalog_no_results),
            color = RelvenColors.MutedText,
            fontSize = 15.sp)
      }
    } else {
      LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(matches, key = ExerciseEntry::exerciseId) { entry ->
          ExerciseRow(
              entry,
              isFavorite = entry.exerciseId in favoriteExerciseIds,
              onClick = { selectedExerciseId = entry.exerciseId },
              onToggleFavorite = { onToggleFavorite(entry.exerciseId, it) })
        }
      }
    }
  }
}

@Composable
private fun CategoryChip(category: ExerciseCategory?, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
      selected = selected,
      onClick = onClick,
      label = {
        if (category == null) {
          Text(stringResource(R.string.Exercise_category_all))
        } else {
          Text(categoryLabel(category))
        }
      })
}

@Composable
private fun ExerciseRow(
    entry: ExerciseEntry,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, RelvenColors.Text.copy(alpha = 0.07f))) {
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
      Column(
          Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(categoryLabel(entry.category), color = RelvenColors.Orange, fontSize = 12.sp)
        MuscleGroupLabels(entry.muscleGroups, color = RelvenColors.MutedText, fontSize = 13.sp)
      }
      IconButton(onClick = { onToggleFavorite(!isFavorite) }) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.Exercise_catalog_remove_favorite
                else R.string.Exercise_catalog_add_favorite),
            tint = if (isFavorite) RelvenColors.Orange else RelvenColors.MutedText)
      }
    }
  }
}

@Composable
private fun ExerciseDetail(
    entry: ExerciseEntry,
    progressionState: ExerciseProgressionLoadState,
    onRetryProgression: () -> Unit,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null) {
  Column(
      modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.Exercise_catalog_back))
      }
      Text(stringResource(R.string.Exercise_catalog_back), color = RelvenColors.MutedText)
    }
    if (onEdit != null) {
      Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.Exercise_custom_edit))
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(entry.name, fontSize = 30.sp, fontWeight = FontWeight.Bold)
      Text(categoryLabel(entry.category), color = RelvenColors.Orange, fontSize = 14.sp)
    }
    DetailSection(title = stringResource(R.string.Exercise_catalog_muscle_groups)) {
      MuscleGroupLabels(entry.muscleGroups, color = RelvenColors.MutedText, fontSize = 15.sp)
    }
    DetailSection(title = stringResource(R.string.Exercise_catalog_equipment)) {
      Text(
          if (entry.equipment.isEmpty()) stringResource(R.string.Exercise_catalog_no_equipment)
          else entry.equipment.joinToString(", "),
          color = RelvenColors.MutedText,
          fontSize = 15.sp)
    }
    DetailSection(title = stringResource(R.string.Exercise_catalog_instructions)) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entry.instructions.forEachIndexed { index, instruction ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = RelvenColors.SurfaceRaised, modifier = Modifier.size(28.dp)) {
              Box(contentAlignment = Alignment.Center) {
                Text("${index + 1}", color = RelvenColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
            Text(instruction, color = RelvenColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
          }
        }
      }
    }
    DetailSection(title = stringResource(R.string.Exercise_progression_title)) {
      when (progressionState) {
        ExerciseProgressionLoadState.Loading ->
            Text(stringResource(R.string.Exercise_progression_loading), color = RelvenColors.MutedText)
        ExerciseProgressionLoadState.Unavailable -> {
          Text(stringResource(R.string.Exercise_progression_unavailable), color = RelvenColors.MutedText)
          Button(onClick = onRetryProgression) {
            Text(stringResource(R.string.Exercise_progression_retry))
          }
        }
        is ExerciseProgressionLoadState.Loaded -> {
          if (progressionState.unavailableRecordCount > 0) {
            Text(
                stringResource(
                    R.string.Exercise_progression_partial, progressionState.unavailableRecordCount),
                color = RelvenColors.MutedText)
          }
          val history = progressionState.sessionsByExerciseId[entry.exerciseId].orEmpty()
          if (history.isEmpty()) {
            Text(stringResource(R.string.Exercise_progression_empty), color = RelvenColors.MutedText)
          } else {
            history.forEach { session -> ExerciseProgressCard(session) }
          }
        }
      }
    }
  }
}

@Composable
private fun CustomExerciseEditor(
    initial: CustomExerciseInput?,
    saving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSave: (CustomExerciseInput) -> Unit
) {
  val formStateSaver = listSaver<CustomExerciseFormState, Any>(
      save = { form ->
        listOf(
            form.nameEnglish,
            form.nameSwedish,
            form.category.key,
            form.movementPattern,
            form.muscleGroups.joinToString(","),
            form.equipment,
            form.instructionsEnglish,
            form.instructionsSwedish,
            form.error.orEmpty())
      },
      restore = { saved ->
        CustomExerciseFormState(
            nameEnglish = saved[0] as String,
            nameSwedish = saved[1] as String,
            category = ExerciseCategory.fromKey(saved[2] as String) ?: ExerciseCategory.LOWER_BODY,
            movementPattern = saved[3] as String,
            muscleGroups = (saved[4] as String).split(',').filter(String::isNotBlank).toSet(),
            equipment = saved[5] as String,
            instructionsEnglish = saved[6] as String,
            instructionsSwedish = saved[7] as String,
            error = (saved[8] as String).ifBlank { null })
      })
  var state by rememberSaveable(initial, stateSaver = formStateSaver) {
    mutableStateOf(
        initial?.let {
          CustomExerciseFormState(
              it.nameEnglish, it.nameSwedish, it.category, it.movementPattern,
              it.muscleGroups.toSet(), it.equipment.joinToString(", "),
              it.instructionsEnglish.joinToString("\n"), it.instructionsSwedish.joinToString("\n"))
        } ?: CustomExerciseFormState())
  }
  val muscles = listOf("abdominals", "back", "calves", "chest", "glutes", "hamstrings", "hips", "obliques", "quadriceps", "shoulders", "triceps")
  Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.Exercise_catalog_back)) }
      Text(stringResource(if (initial == null) R.string.Exercise_custom_add else R.string.Exercise_custom_edit), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
    FormField(R.string.Exercise_custom_name_en, state.nameEnglish) { state = state.copy(nameEnglish = it) }
    FormField(R.string.Exercise_custom_name_sv, state.nameSwedish) { state = state.copy(nameSwedish = it) }
    Text(stringResource(R.string.Exercise_custom_category), fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      ExerciseCategory.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEach { category ->
            FilterChip(selected = state.category == category, onClick = { state = state.copy(category = category) }, label = { Text(categoryLabel(category)) })
          }
        }
      }
    }
    FormField(R.string.Exercise_custom_movement, state.movementPattern) { state = state.copy(movementPattern = it) }
    Text(stringResource(R.string.Exercise_catalog_muscle_groups), fontWeight = FontWeight.SemiBold)
    muscles.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        row.forEach { key ->
          FilterChip(selected = key in state.muscleGroups, onClick = { state = state.copy(muscleGroups = if (key in state.muscleGroups) state.muscleGroups - key else state.muscleGroups + key) }, label = { Text(muscleGroupLabel(key)) })
        }
      }
    }
    FormField(R.string.Exercise_custom_equipment, state.equipment) { state = state.copy(equipment = it) }
    FormField(R.string.Exercise_custom_instructions_en, state.instructionsEnglish, minLines = 3) { state = state.copy(instructionsEnglish = it) }
    FormField(R.string.Exercise_custom_instructions_sv, state.instructionsSwedish, minLines = 3) { state = state.copy(instructionsSwedish = it) }
    if (state.toInput() == null) Text(stringResource(R.string.Exercise_custom_required), color = RelvenColors.MutedText)
    if (error != null) Text(stringResource(R.string.Exercise_custom_save_error), color = RelvenColors.Orange, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Button(onClick = { state.toInput()?.let(onSave) }, enabled = !saving && state.toInput() != null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
      Text(stringResource(if (saving) R.string.Exercise_custom_saving else R.string.Exercise_custom_save))
    }
  }
}

@Composable
private fun FormField(label: Int, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, minLines = minLines)
}

@Composable
private fun ExerciseProgressCard(session: ExerciseProgressSession) {
  val locale = LocalConfiguration.current.locales[0]
  val formattedDate =
      remember(session.finishedAtMillis, locale) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            .format(Date(session.finishedAtMillis))
      }
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, RelvenColors.Text.copy(alpha = 0.07f)),
      modifier = Modifier.fillMaxWidth()) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
          formattedDate,
          color = RelvenColors.Text,
          fontWeight = FontWeight.SemiBold)
      session.sets.forEach { result ->
        val label =
            result.repetitions?.let {
              stringResource(R.string.Exercise_progression_repetition_set, result.setNumber, it)
            } ?: stringResource(
                R.string.Exercise_progression_timed_set,
                result.setNumber,
                requireNotNull(result.durationSeconds))
        Text(label, color = RelvenColors.MutedText)
      }
    }
  }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    content()
  }
}

@Composable
private fun MuscleGroupLabels(
    muscleGroups: List<String>,
    color: androidx.compose.ui.graphics.Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
  val labels = ArrayList<String>(muscleGroups.size)
  muscleGroups.forEach { labels.add(muscleGroupLabel(it)) }
  Text(labels.joinToString(" · "), color = color, fontSize = fontSize)
}

@Composable
private fun CatalogMessage(message: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(message, color = RelvenColors.MutedText, fontSize = 15.sp)
  }
}

@Composable
private fun CatalogError(onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        stringResource(R.string.Exercise_catalog_unavailable),
        color = RelvenColors.Text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.Exercise_catalog_unavailable_hint),
        color = RelvenColors.MutedText,
        fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
      Text(stringResource(R.string.Exercise_catalog_retry))
    }
  }
}

@Composable
private fun categoryLabel(category: ExerciseCategory): String =
    stringResource(
        when (category) {
          ExerciseCategory.LOWER_BODY -> R.string.Exercise_category_lower_body
          ExerciseCategory.UPPER_BODY -> R.string.Exercise_category_upper_body
          ExerciseCategory.CORE -> R.string.Exercise_category_core
          ExerciseCategory.WARMUP_MOBILITY -> R.string.Exercise_category_warmup_mobility
        })

@Composable
private fun muscleGroupLabel(key: String): String =
    stringResource(
        when (key) {
          "abdominals" -> R.string.Exercise_muscle_abdominals
          "back" -> R.string.Exercise_muscle_back
          "calves" -> R.string.Exercise_muscle_calves
          "chest" -> R.string.Exercise_muscle_chest
          "glutes" -> R.string.Exercise_muscle_glutes
          "hamstrings" -> R.string.Exercise_muscle_hamstrings
          "hips" -> R.string.Exercise_muscle_hips
          "obliques" -> R.string.Exercise_muscle_obliques
          "quadriceps" -> R.string.Exercise_muscle_quadriceps
          "shoulders" -> R.string.Exercise_muscle_shoulders
          "triceps" -> R.string.Exercise_muscle_triceps
          else -> R.string.Exercise_muscle_other
        })
