package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import org.runnerup.R

object ManageWorkoutsScreenCompose {
  @JvmStatic
  fun install(
      view: ComposeView,
      state: WorkoutManagerScreenStateHolder,
      actions: ManageWorkoutActionsStateHolder,
      onBack: Runnable) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
      RelvenTheme {
        ManageWorkoutsScreen(state, actions, onBack::run)
      }
    }
  }
}

@Composable
private fun ManageWorkoutsScreen(
    state: WorkoutManagerScreenStateHolder,
    actions: ManageWorkoutActionsStateHolder,
    onBack: () -> Unit) {
  val presentation = state.presentation
  val visibleSources = visibleWorkoutManagerSources(presentation.sources, presentation.searchQuery)
  LazyColumn(
      modifier = Modifier.fillMaxSize().background(RelvenColors.Background).statusBarsPadding().navigationBarsPadding(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
          start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
      TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(stringResource(R.string.workout_manager_back), color = RelvenColors.Orange)
      }
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.workout_manager_title),
            color = RelvenColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.workout_manager_subtitle),
            color = RelvenColors.MutedText,
            fontSize = 14.sp)
      }
    }
    item {
      Surface(
          color = RelvenColors.Surface,
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, RelvenColors.SurfaceRaised),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) { actions.Content() }
      }
    }
    item {
      OutlinedTextField(
          value = presentation.searchQuery,
          onValueChange = state::setSearchQuery,
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.workout_manager_search)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
    }
    if (visibleSources.isEmpty()) {
      item {
        Surface(
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, RelvenColors.SurfaceRaised),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    if (presentation.sources.isEmpty()) R.string.workout_manager_empty_title
                    else R.string.workout_manager_no_search_results),
                color = RelvenColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    if (presentation.sources.isEmpty()) R.string.workout_manager_empty_body
                    else R.string.workout_manager_no_search_results_body),
                color = RelvenColors.MutedText,
                fontSize = 14.sp)
          }
        }
      }
    }
    visibleSources.forEach { source ->
      item(key = "source:${source.name}") {
        Text(
            source.name,
            color = RelvenColors.MutedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Surface(
            color = RelvenColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, RelvenColors.SurfaceRaised),
            modifier = Modifier.fillMaxWidth()) {
          Column {
            source.workouts.forEachIndexed { index, workout ->
              val selected =
                  presentation.selectedWorkoutKey == workoutManagerSelectionKey(source.name, workout.name)
              Row(
                  modifier = Modifier.fillMaxWidth()
                      .clickable(role = Role.RadioButton) { state.selectWorkout(source.name, workout.name) }
                      .background(if (selected) RelvenColors.Orange.copy(alpha = 0.12f) else Color.Transparent)
                      .padding(horizontal = 12.dp, vertical = 4.dp)
                      .heightIn(min = 56.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(
                    selected = selected,
                    onClick = { state.selectWorkout(source.name, workout.name) })
                Text(
                    workout.name,
                    color = RelvenColors.Text,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f))
              }
              if (index < source.workouts.lastIndex) {
                HorizontalDivider(
                    color = RelvenColors.SurfaceRaised.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 56.dp))
              }
            }
          }
        }
      }
    }
  }
}
