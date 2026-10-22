package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
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
    Modifier.fillMaxWidth().background(RelvenColors.Background).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    when (state.selectedTab) {
      0 -> BasicSetup(state, listener)
      1 -> IntervalSetup(state, listener)
      else -> AdvancedSetup(state, listener)
    }
  }
}

@Composable
private fun BasicSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(CommonR.string.Target))
    SetupDropdown(
      label = stringResource(CommonR.string.Type),
      selected = state.targetType.toString(),
      options = state.targetTypes,
      onSelected = { listener.onPreferenceChanged("basicTargetType", it) },
    )
    if (WorkoutSetupPresentation.showsPace(state.targetType)) {
      SetupInput(
        stringResource(CommonR.string.Target_pace_HHMMSS), state.pace, time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("basic_target_pace_max", it)
      }
    }
    if (WorkoutSetupPresentation.showsHeartRate(state.targetType, state.heartRateZones.isNotEmpty())) {
      SetupDropdown(
        label = stringResource(CommonR.string.Target_heart_rate_zone),
        selected = state.heartRateZone,
        options = state.heartRateZones,
        onSelected = { listener.onPreferenceChanged("basic_target_hrz", it) },
      )
    }
  }
  AudioSection(state.basicAudio, state.basicAudioSchemes) {
    listener.onPreferenceChanged("basicAudio", it)
  }
  ManageAudioButton { listener.onManageAudio(0) }
}

@Composable
private fun IntervalSetup(state: WorkoutSetupState, listener: WorkoutSetupListener) {
  SetupSection {
    SectionTitle(stringResource(CommonR.string.Interval))
    SetupInput(
      stringResource(CommonR.string.Repetitions), state.intervalRepetitions,
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
        stringResource(org.runnerup.R.string.setup_interval_duration), state.intervalTime, time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("intervalTime", it)
      }
    } else {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_interval_distance), state.intervalDistance,
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
        stringResource(org.runnerup.R.string.setup_rest_duration), state.restTime, time = true,
        isValid = WorkoutSetupInput::isTime,
      ) {
        if (WorkoutSetupInput.isTime(it)) listener.onPreferenceChanged("intervalRestTime", it)
      }
    } else {
      SetupInput(
        stringResource(org.runnerup.R.string.setup_rest_distance), state.restDistance,
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
  AudioSection(state.intervalAudio, state.intervalAudioSchemes) {
    listener.onPreferenceChanged("intervalAudio", it)
  }
  ManageAudioButton { listener.onManageAudio(1) }
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
      Text(stringResource(org.runnerup.R.string.setup_workouts_empty), color = RelvenColors.MutedText, fontSize = 14.sp)
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
            Text(step.title, color = RelvenColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (step.detail.isNotBlank()) Text(step.detail, color = RelvenColors.MutedText, fontSize = 12.sp)
          }
          Text(stringResource(CommonR.string.Edit_step), color = RelvenColors.Orange, fontSize = 12.sp)
        }
      }
    }
  }
  AudioSection(state.advancedAudio, state.advancedAudioSchemes) {
    listener.onPreferenceChanged("advancedAudio", it)
  }
  ManageAudioButton { listener.onManageAudio(2) }
}

@Composable
private fun AudioSection(
  selected: String,
  options: List<WorkoutSetupChoice>,
  onSelected: (String) -> Unit,
) {
  SetupSection {
    SetupDropdown(
      label = stringResource(CommonR.string.Audio_cue_settings),
      selected = selected,
      options = options,
      onSelected = onSelected,
    )
  }
}

@Composable
private fun ManageAudioButton(onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(stringResource(CommonR.string.Manage_audio_cues), color = RelvenColors.MutedText)
  }
}

@Composable
private fun SetupSection(content: @Composable () -> Unit) {
  Surface(color = RelvenColors.Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      content()
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, color = RelvenColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
  Column {
    Text(label, color = RelvenColors.MutedText, fontSize = 12.sp)
    TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(shown, color = RelvenColors.Text, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Text("⌄", color = RelvenColors.Orange)
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
    onValueChange = { draft = it; onCommit(it) },
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    singleLine = true,
    isError = draft.isNotEmpty() && !isValid(draft),
    supportingText = {
      if (draft.isNotEmpty() && !isValid(draft)) {
        Text(
          stringResource(if (time) org.runnerup.R.string.setup_invalid_time else org.runnerup.R.string.setup_invalid_number),
        )
      }
    },
    keyboardOptions = KeyboardOptions(keyboardType = if (time) KeyboardType.Number else KeyboardType.Decimal),
  )
}

@Composable
private fun intervalKinds(): List<WorkoutSetupChoice> =
  listOf(
    WorkoutSetupChoice("0", stringResource(CommonR.string.Time)),
    WorkoutSetupChoice("1", stringResource(CommonR.string.Distance)),
  )

