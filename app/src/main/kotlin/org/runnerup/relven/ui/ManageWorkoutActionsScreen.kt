package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R as CommonR

data class ManageWorkoutActionsPresentation(val canModifySelected: Boolean)

class ManageWorkoutActionsStateHolder(private val callbacks: WorkoutManagerActionCallbacks) {
  var presentation by mutableStateOf(ManageWorkoutActionsPresentation(canModifySelected = false))
    private set

  fun update(canModifySelected: Boolean) {
    presentation = ManageWorkoutActionsPresentation(canModifySelected)
  }

  fun importWorkout() = callbacks.importWorkout()

  fun importPlan() = callbacks.importPlan()

  fun showPlanKit() = callbacks.showPlanKit()

  fun viewPlans() = callbacks.viewPlans()

  fun share() = callbacks.share()

  fun edit() = callbacks.edit()

  fun create() = callbacks.create()

  fun delete() = callbacks.delete()

  @Composable
  fun Content() {
    ManageWorkoutActions(presentation, this)
  }
}

object ManageWorkoutActionsCompose {
  @JvmStatic
  fun install(view: ComposeView, state: ManageWorkoutActionsStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun ManageWorkoutActions(
  presentation: ManageWorkoutActionsPresentation,
  actions: ManageWorkoutActionsStateHolder,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ManagerActionButton(
        stringResource(CommonR.string.Create_new_workout),
        RelvenColors.Orange,
        Modifier.weight(1f),
        onClick = actions::create,
      )
      ManagerActionButton(
        stringResource(CommonR.string.Import_workout),
        ManagerActionSecondary,
        Modifier.weight(1f),
        onClick = actions::importWorkout,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ManagerActionButton(
        stringResource(CommonR.string.Import_plan),
        ManagerActionSecondary,
        Modifier.weight(1f),
        onClick = actions::importPlan,
      )
      ManagerActionButton(
        stringResource(CommonR.string.View_saved_plans),
        ManagerActionSecondary,
        Modifier.weight(1f),
        onClick = actions::viewPlans,
      )
    }
    OutlinedButton(
      onClick = actions::showPlanKit,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      shape = RoundedCornerShape(16.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.5f)),
    ) {
      Text(stringResource(CommonR.string.AI_plan_kit_title), color = RelvenColors.Orange, fontWeight = FontWeight.SemiBold)
    }
    if (presentation.canModifySelected) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ManagerActionButton(
        stringResource(CommonR.string.Share),
        ManagerActionSecondary,
        Modifier.weight(1f),
        onClick = actions::share,
      )
      ManagerActionButton(
        stringResource(CommonR.string.Edit),
        ManagerActionSecondary,
        Modifier.weight(1f),
        onClick = actions::edit,
      )
      ManagerActionButton(
        stringResource(CommonR.string.Delete),
        ManagerActionDanger,
        Modifier.weight(1f),
        onClick = actions::delete,
      )
      }
    }
  }
}

@Composable
private fun ManagerActionButton(
  label: String,
  color: Color,
  modifier: Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    colors = ButtonDefaults.buttonColors(containerColor = color),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
    )
  }
}

private val ManagerActionSecondary = RelvenColors.SurfaceRaised
private val ManagerActionDanger = Color(0xFF9F3C3B)
