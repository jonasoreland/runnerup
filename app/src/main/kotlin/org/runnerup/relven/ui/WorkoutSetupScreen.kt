package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R as CommonR

class WorkoutSetupStateHolder(
  initialState: WorkoutSetupState,
  private val listener: WorkoutSetupListener,
) {
  var state by mutableStateOf(initialState)
    private set

  fun update(value: WorkoutSetupState) {
    state = value
  }

  @Composable
  fun Content() {
    WorkoutSetupContent(state, listener)
  }
}

object WorkoutSetupCompose {
  @JvmStatic
  fun install(view: ComposeView, state: WorkoutSetupStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun WorkoutSetupContent(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  Column(
    Modifier.fillMaxWidth()
      .background(RelvenColors.Background)
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 160.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (state.isGym) {
      GymWorkoutSetup(state, listener)
    } else {
      when (state.selectedTab) {
        0 -> BasicSetup(state, listener)
        1 -> IntervalSetup(state, listener)
        else -> AdvancedSetup(state, listener)
      }
    }
  }
}

@Composable
private fun GymWorkoutSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(org.runnerup.R.string.gym_workout_title))
    OutlinedTextField(
      value = state.gymName,
      onValueChange = listener::onGymNameChanged,
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(org.runnerup.R.string.gym_workout_name)) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
    )
    Text(
      pluralStringResource(
        org.runnerup.R.plurals.gym_workout_exercise_count,
        state.gymDraft.size,
        state.gymDraft.size,
      ),
      color = RelvenColors.MutedText,
      fontSize = 13.sp,
    )
  }

  if (state.gymDraft.isNotEmpty()) GymWorkoutDraftSection(state, listener)

  SetupSection {
    SectionTitle(stringResource(org.runnerup.R.string.gym_exercises_title))
    OutlinedTextField(
      value = state.gymQuery,
      onValueChange = listener::onGymQueryChanged,
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(org.runnerup.R.string.gym_exercise_search)) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
    )
    if (state.gymCatalogLoading) {
      Text(stringResource(org.runnerup.R.string.gym_exercises_loading), color = RelvenColors.MutedText)
    } else if (state.gymCatalogError) {
      Text(stringResource(org.runnerup.R.string.gym_exercises_error), color = RelvenColors.MutedText)
    } else {
      val selectedIds = state.gymDraft.mapTo(HashSet(), GymExerciseDraft::exerciseId)
      val query = state.gymQuery.trim()
      val matches = state.gymExercises.filter {
        it.exerciseId !in selectedIds &&
          (query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
            it.searchText.contains(query, ignoreCase = true))
      }.take(12)
      if (matches.isEmpty()) {
        Text(
          if (state.gymDraft.isEmpty()) stringResource(org.runnerup.R.string.gym_exercises_empty)
          else stringResource(org.runnerup.R.string.gym_exercise_no_matches),
          color = RelvenColors.MutedText,
        )
      }
      matches.forEach { exercise ->
        Row(
          Modifier.fillMaxWidth().padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(exercise.name, color = RelvenColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(gymCategoryLabel(exercise.category), color = RelvenColors.MutedText, fontSize = 11.sp)
          }
          TextButton(onClick = { listener.onAddGymExercise(exercise.exerciseId) }) {
            Text(stringResource(org.runnerup.R.string.gym_exercise_add), color = RelvenColors.Orange)
          }
        }
      }
    }
  }

}

@Composable
private fun GymWorkoutDraftSection(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(org.runnerup.R.string.gym_workout_order))
    state.gymDraft.forEachIndexed { index, exercise ->
      Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            "${index + 1}. ${exercise.name}",
            color = RelvenColors.Text,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
          )
          TextButton(onClick = { listener.onRemoveGymExercise(exercise.exerciseId) }) {
            Text(stringResource(org.runnerup.R.string.gym_exercise_remove), color = RelvenColors.Orange)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GymNumberInput(
            label = stringResource(org.runnerup.R.string.gym_sets),
            value = exercise.sets,
            minimum = 1,
            maximum = 100,
            modifier = Modifier.weight(1f),
            onValueChange = { listener.onUpdateGymExercise(exercise.exerciseId, it, exercise.repetitions, exercise.restSeconds) },
          )
          GymNumberInput(
            label = stringResource(org.runnerup.R.string.gym_repetitions),
            value = exercise.repetitions,
            minimum = 1,
            maximum = 500,
            modifier = Modifier.weight(1f),
            onValueChange = { listener.onUpdateGymExercise(exercise.exerciseId, exercise.sets, it, exercise.restSeconds) },
          )
          GymNumberInput(
            label = stringResource(org.runnerup.R.string.gym_rest_seconds),
            value = exercise.restSeconds,
            minimum = 0,
            maximum = 3600,
            modifier = Modifier.weight(1f),
            onValueChange = { listener.onUpdateGymExercise(exercise.exerciseId, exercise.sets, exercise.repetitions, it) },
          )
        }
      }
    }
  }
}

@Composable
private fun gymCategoryLabel(category: String): String =
  stringResource(
    when (category) {
      "lower_body" -> CommonR.string.Exercise_category_lower_body
      "upper_body" -> CommonR.string.Exercise_category_upper_body
      "core" -> CommonR.string.Exercise_category_core
      else -> CommonR.string.Exercise_category_warmup_mobility
    },
  )

@Composable
private fun GymNumberInput(
  label: String,
  value: String,
  minimum: Int,
  maximum: Int,
  modifier: Modifier,
  onValueChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = { Text(label, maxLines = 1, fontSize = 11.sp) },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    isError = value.toIntOrNull()?.let { it !in minimum..maximum } ?: true,
    supportingText = {
      if (value.toIntOrNull()?.let { it in minimum..maximum } != true) {
        Text(stringResource(org.runnerup.R.string.gym_number_invalid), fontSize = 9.sp)
      }
    },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  )
}

@Composable
private fun BasicSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(CommonR.string.Target))
    SetupDropdown(
      label = stringResource(CommonR.string.Type),
      selected = state.goalType,
      options = state.goalTypes,
      onSelected = { listener.onPreferenceChanged("basic_goal_type", it) },
    )
    if (WorkoutSetupPresentation.showsPace(state.goalType)) {
      SetupInput(
        stringResource(CommonR.string.Target_pace_HHMMSS),
        state.pace,
        time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("basic_target_pace_max", it)
      }
    }
    if (WorkoutSetupPresentation.showsGoalTime(state.goalType)) {
      SetupInput(
        stringResource(CommonR.string.Duration),
        state.goalTime,
        time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("basic_goal_time", it)
      }
    }
    if (WorkoutSetupPresentation.showsGoalDistance(state.goalType)) {
      SetupInput(
        "${stringResource(CommonR.string.Distance)} (${state.goalDistanceUnit})",
        state.goalDistance,
        isValid = WorkoutSetupInput::isPositive,
      ) {
        if (WorkoutSetupInput.isPositive(it)) listener.onPreferenceChanged("basic_goal_distance", it)
      }
    }
    if (WorkoutSetupPresentation.showsHeartRate(
        state.goalType,
        state.heartRateZones.isNotEmpty(),
      )) {
      SetupDropdown(
        label = stringResource(CommonR.string.Target_heart_rate_zone),
        selected = state.heartRateZone,
        options = state.heartRateZones,
        onSelected = { listener.onPreferenceChanged("basic_target_hrz", it) },
      )
    }
  }
  AudioSection(
    selected = state.basicAudio,
    options = state.basicAudioSchemes,
    onSelected = { listener.onPreferenceChanged("basicAudio", it) },
    onManage = { listener.onManageAudio(0) },
  )
}

@Composable
private fun IntervalSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(CommonR.string.Interval))
    SetupInput(
      stringResource(CommonR.string.Repetitions),
      state.intervalRepetitions,
      isValid = { it.toIntOrNull()?.let { count -> count in 1..1000 } == true },
    ) {
      if (it.toIntOrNull()?.let { count -> count in 1..1000 } == true) {
        listener.onPreferenceChanged("intervalRepetitions", it)
      }
    }
    SetupDropdown(
      label = stringResource(CommonR.string.Interval_type),
      selected = state.intervalType.toString(),
      options = intervalKinds(),
      onSelected = { listener.onPreferenceChanged("intervalType", it) },
    )
    if (WorkoutSetupPresentation.showsActiveTime(state.intervalType)) {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_interval_duration),
        state.intervalTime,
        time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("intervalTime", it)
      }
    } else {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_interval_distance),
        state.intervalDistance,
        isValid = WorkoutSetupInput::isPositive,
      ) {
        if (WorkoutSetupInput.isPositive(it)) listener.onPreferenceChanged("intervalDistance", it)
      }
    }
    SetupDropdown(
      label = stringResource(CommonR.string.Rest_type),
      selected = state.restType.toString(),
      options = intervalKinds(),
      onSelected = { listener.onPreferenceChanged("intervalRestType", it) },
    )
    if (WorkoutSetupPresentation.showsRestTime(state.restType)) {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_rest_duration),
        state.restTime,
        time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("intervalRestTime", it)
      }
    } else {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_rest_distance),
        state.restDistance,
        isValid = WorkoutSetupInput::isPositive,
      ) {
        if (WorkoutSetupInput.isPositive(it)) listener.onPreferenceChanged("intervalRestDistance", it)
      }
    }
  }
  SetupSection {
    SectionTitle(stringResource(org.runnerup.R.string.setup_interval_structure))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("✓  ${stringResource(CommonR.string.warm_up)}", color = RelvenColors.MutedText, fontSize = 13.sp)
      Spacer(Modifier.width(20.dp))
      Text("✓  ${stringResource(CommonR.string.cool_down)}", color = RelvenColors.MutedText, fontSize = 13.sp)
    }
  }
  AudioSection(
    selected = state.intervalAudio,
    options = state.intervalAudioSchemes,
    onSelected = { listener.onPreferenceChanged("intervalAudio", it) },
    onManage = { listener.onManageAudio(1) },
  )
}

@Composable
private fun AdvancedSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(CommonR.string.Workout))
    if (state.advancedWorkouts.isNotEmpty()) {
      SetupDropdown(
        label = stringResource(CommonR.string.Workout),
        selected = state.advancedWorkout,
        options = state.advancedWorkouts,
        onSelected = listener::onSelectWorkout,
      )
    } else {
      Text(
        stringResource(org.runnerup.R.string.setup_workouts_empty),
        color = RelvenColors.MutedText,
        fontSize = 14.sp,
      )
    }
    TextButton(onClick = listener::onManageWorkouts) {
      Text(stringResource(CommonR.string.Manage_workouts), color = RelvenColors.Orange)
    }
  }
  if (state.advancedSteps.isNotEmpty()) {
    SetupSection {
      SectionTitle(stringResource(org.runnerup.R.string.setup_steps))
      state.advancedSteps.forEachIndexed { index, step ->
        Row(
          Modifier.fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(role = Role.Button) { listener.onEditStep(index) }
            .padding(start = (step.indent * 12).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              step.title,
              color = RelvenColors.Text,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
            )
            if (step.detail.isNotBlank()) {
              Text(step.detail, color = RelvenColors.MutedText, fontSize = 12.sp)
            }
          }
          Text(stringResource(CommonR.string.Edit_step), color = RelvenColors.Orange, fontSize = 12.sp)
        }
      }
    }
  }
  AudioSection(
    selected = state.advancedAudio,
    options = state.advancedAudioSchemes,
    onSelected = { listener.onPreferenceChanged("advancedAudio", it) },
    onManage = { listener.onManageAudio(2) },
  )
}

@Composable
private fun AudioSection(
  selected: String,
  options: List<WorkoutSetupChoice>,
  onSelected: (String) -> Unit,
  onManage: () -> Unit,
) {
  SetupSection {
    SetupDropdown(
      label = stringResource(CommonR.string.Audio_cue_settings),
      selected = selected,
      options = options,
      onSelected = onSelected,
    )
    TextButton(onClick = onManage) {
      Text(stringResource(CommonR.string.Manage_audio_cues), color = RelvenColors.Orange)
    }
  }
}

@Composable
private fun SetupSection(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    color = RelvenColors.Surface,
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(1.dp, RelvenColors.SurfaceRaised.copy(alpha = 0.72f)),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, color = RelvenColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SetupDropdown(
  label: String,
  selected: String,
  options: List<WorkoutSetupChoice>,
  onSelected: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val shown = options.firstOrNull { it.value == selected }?.label ?: label
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, color = RelvenColors.MutedText, fontSize = 13.sp)
    Surface(
      color = RelvenColors.Background,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, RelvenColors.SurfaceRaised),
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { expanded = true },
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(shown, color = RelvenColors.Text, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Icon(
          Icons.Default.ExpandMore,
          contentDescription = null,
          tint = RelvenColors.Orange,
        )
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.label) },
          onClick = {
            expanded = false
            onSelected(option.value)
          },
        )
      }
    }
  }
}

@Composable
private fun SetupInput(
  label: String,
  value: String,
  time: Boolean = false,
  isValid: (String) -> Boolean = { true },
  onCommit: (String) -> Unit,
) {
  var draft by remember(value) { mutableStateOf(value) }
  OutlinedTextField(
    value = draft,
    onValueChange = {
      draft = it
      onCommit(it)
    },
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    isError = draft.isNotEmpty() && !isValid(draft),
    supportingText = {
      if (draft.isNotEmpty() && !isValid(draft)) {
        Text(
          stringResource(
            if (time) org.runnerup.R.string.setup_invalid_time
            else org.runnerup.R.string.setup_invalid_number,
          ),
        )
      }
    },
    keyboardOptions =
      KeyboardOptions(keyboardType = if (time) KeyboardType.Number else KeyboardType.Decimal),
  )
}

@Composable
private fun intervalKinds(): List<WorkoutSetupChoice> =
  listOf(
    WorkoutSetupChoice("0", stringResource(CommonR.string.Time)),
    WorkoutSetupChoice("1", stringResource(CommonR.string.Distance)),
  )
