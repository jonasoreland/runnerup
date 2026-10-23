package org.runnerup.relven.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.runnerup.common.R as CommonR
import org.runnerup.util.Formatter
import org.runnerup.workout.Intensity
import org.runnerup.workout.Step

class RunWorkoutStepsStateHolder {
  var presentation by mutableStateOf(RunWorkoutStepsPresentation<RunWorkoutStepItem>(emptyList(), null))
    private set

  fun update(value: RunWorkoutStepsPresentation<RunWorkoutStepItem>) {
    presentation = value
  }

  @Composable
  fun Content() {
    RunWorkoutStepsList(presentation)
  }
}

object RunWorkoutStepsCompose {
  @JvmStatic
  fun install(view: ComposeView, state: RunWorkoutStepsStateHolder, formatter: Formatter) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
      CompositionLocalProvider(LocalRunWorkoutFormatter provides formatter) {
        RelvenTheme { state.Content() }
      }
    }
  }
}

@Composable
private fun RunWorkoutStepsList(
  presentation: RunWorkoutStepsPresentation<RunWorkoutStepItem>,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(presentation.currentIndex, presentation.items.size) {
    presentation.currentIndex?.let { listState.scrollToItem(it) }
  }
  LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
    itemsIndexed(presentation.items, key = { index, _ -> index }) { index, item ->
      RunWorkoutStepRow(item, isCurrent = index == presentation.currentIndex)
    }
  }
}

@Composable
private fun RunWorkoutStepRow(item: RunWorkoutStepItem, isCurrent: Boolean) {
  val resources = LocalResources.current
  val locale = LocalLocale.current.platformLocale
  val finished = stringResource(CommonR.string.Finished)
  val step = item.step
  val formatter = LocalRunWorkoutFormatter.current
  val durationType = step.durationType?.let { resources.getText(it.textId).toString() }.orEmpty()
  var durationValue =
    step.durationType?.let { formatter.format(Formatter.Format.TXT_LONG, it, step.durationValue) }
      .orEmpty()
  val target =
    step.targetType?.let {
      val minValue = step.targetValue.minValue
      val maxValue = step.targetValue.maxValue
      if (minValue == maxValue) {
        formatter.format(Formatter.Format.TXT_SHORT, it, minValue)
      } else {
        String.format(
          locale,
          "%s-%s",
          formatter.format(Formatter.Format.TXT_SHORT, it, minValue),
          formatter.format(Formatter.Format.TXT_SHORT, it, maxValue),
        )
      }
    }.orEmpty()
  if (step.intensity == Intensity.REPEAT) {
    durationValue =
      if (step.currentRepeat >= step.repeatCount) {
        finished
      } else {
        String.format(locale, "%d/%d", step.currentRepeat + 1, step.repeatCount)
      }
  }
  val intensity = resources.getText(step.intensity.textId).toString()
  val shape = RoundedCornerShape(8.dp)
  Surface(
    color = if (isCurrent) RelvenColors.SurfaceRaised else RelvenColors.Background,
    shape = shape,
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 2.dp)
        .clip(shape)
        .then(
          if (isCurrent) Modifier.border(1.dp, RelvenColors.Orange.copy(alpha = 0.45f), shape)
          else Modifier
        ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (item.indentLevel > 0) Spacer(Modifier.width((item.indentLevel * 10).dp))
      Text(
        text = intensity,
        modifier = Modifier.weight(1.1f),
        color = RelvenColors.Text,
        fontSize = 13.sp,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(durationType, color = RelvenColors.MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(durationValue, color = RelvenColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Text(
        text = target,
        modifier = Modifier.weight(1f),
        color = RelvenColors.MutedText,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

internal val LocalRunWorkoutFormatter =
  staticCompositionLocalOf<Formatter> { error("Formatter must be provided by RunActivity") }
