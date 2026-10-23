package org.runnerup.relven.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.common.R as CommonR
import org.runnerup.relven.ui.StartWorkoutTabSelectionListener

class StartWorkoutTabsStateHolder(
  initialIndex: Int,
  private val onSelected: StartWorkoutTabSelectionListener,
) {
  var selectedIndex by mutableIntStateOf(initialIndex)
    private set

  init {
    require(initialIndex in 0..2)
  }

  fun select(index: Int) {
    require(index in 0..2)
    if (selectedIndex == index) return
    selectedIndex = index
    onSelected.onSelected(index)
  }

  @Composable
  fun Content() {
    StartWorkoutTabs(selectedIndex, ::select)
  }
}

object StartWorkoutTabsCompose {
  @JvmStatic
  fun install(view: ComposeView, state: StartWorkoutTabsStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun StartWorkoutTabs(selectedIndex: Int, onSelected: (Int) -> Unit) {
  val labels =
    listOf(
      stringResource(CommonR.string.Basic),
      stringResource(CommonR.string.Interval),
      stringResource(CommonR.string.Advanced),
    )
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    color = RelvenColors.Surface,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, RelvenColors.SurfaceRaised.copy(alpha = 0.65f)),
  ) {
    Row(
      Modifier.fillMaxWidth().selectableGroup().padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      labels.forEachIndexed { index, label ->
        val selected = selectedIndex == index
        Surface(
          modifier =
            Modifier.weight(1f)
              .heightIn(min = 48.dp)
              .selectable(selected = selected, role = Role.Tab) { onSelected(index) },
          color = if (selected) RelvenColors.Orange else RelvenColors.Surface,
          shape = RoundedCornerShape(16.dp),
        ) {
          androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              label,
              color = if (selected) RelvenColors.Text else RelvenColors.MutedText,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}
