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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R
import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.exercise.ExerciseCategory
import org.runnerup.relven.exercise.ExerciseEntry

internal sealed interface ExerciseCatalogLoadState {
  data object Loading : ExerciseCatalogLoadState

  data class Loaded(val catalog: ExerciseCatalog) : ExerciseCatalogLoadState

  data object Unavailable : ExerciseCatalogLoadState
}

@Composable
internal fun ExerciseCatalogScreen(
    loadState: ExerciseCatalogLoadState,
    onRetry: () -> Unit
) {
  when (loadState) {
    ExerciseCatalogLoadState.Loading ->
        CatalogMessage(stringResource(R.string.Exercise_catalog_loading))
    ExerciseCatalogLoadState.Unavailable ->
        CatalogError(onRetry)
    is ExerciseCatalogLoadState.Loaded -> CatalogBrowser(loadState.catalog)
  }
}

@Composable
private fun CatalogBrowser(catalog: ExerciseCatalog) {
  var query by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  var categoryKey by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  var selectedExerciseId by rememberSaveable(catalog.libraryRevision) { mutableStateOf("") }
  val selectedExercise =
      ExerciseCatalogViewState(selectedExerciseId = selectedExerciseId.ifBlank { null })
          .selectedExercise(catalog)

  if (selectedExercise != null) {
    ExerciseDetail(selectedExercise, onBack = { selectedExerciseId = "" })
    return
  }

  val category = ExerciseCategory.fromKey(categoryKey)
  val state = ExerciseCatalogViewState(query, category)
  val matches = state.visibleEntries(catalog)
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 116.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          stringResource(R.string.Exercise_catalog_title),
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold)
      Text(
          stringResource(R.string.Exercise_catalog_subtitle, catalog.exercises.size),
          color = RelvenColors.MutedText,
          fontSize = 14.sp)
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
      CategoryChip(null, categoryKey.isEmpty()) { categoryKey = "" }
      ExerciseCategory.entries.forEach { item ->
        CategoryChip(item, categoryKey == item.key) { categoryKey = item.key }
      }
    }
    if (matches.isEmpty()) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.Exercise_catalog_no_results),
            color = RelvenColors.MutedText,
            fontSize = 15.sp)
      }
    } else {
      LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(matches, key = ExerciseEntry::exerciseId) { entry ->
          ExerciseRow(entry) { selectedExerciseId = entry.exerciseId }
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
private fun ExerciseRow(entry: ExerciseEntry, onClick: () -> Unit) {
  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(role = Role.Button, onClick = onClick)
              .semantics(mergeDescendants = true) {},
      shape = RoundedCornerShape(20.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, RelvenColors.Text.copy(alpha = 0.07f))) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
      Text(categoryLabel(entry.category), color = RelvenColors.Orange, fontSize = 12.sp)
      MuscleGroupLabels(entry.muscleGroups, color = RelvenColors.MutedText, fontSize = 13.sp)
    }
  }
}

@Composable
private fun ExerciseDetail(entry: ExerciseEntry, onBack: () -> Unit) {
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
  for (muscleGroup in muscleGroups) labels.add(muscleGroupLabel(muscleGroup))
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
