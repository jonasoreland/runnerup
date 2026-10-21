package org.runnerup.relven.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.runnerup.common.R as CommonR
import androidx.compose.ui.res.stringResource
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
  PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = Modifier.fillMaxWidth()) {
    labels.forEachIndexed { index, label ->
      Tab(
        selected = selectedIndex == index,
        onClick = { onSelected(index) },
        selectedContentColor = RelvenColors.Orange,
        unselectedContentColor = RelvenColors.MutedText,
        text = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
      )
    }
  }
}
