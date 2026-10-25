package org.runnerup.relven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import org.runnerup.R
import org.runnerup.widget.ClassicSpinner

object StartScreenChromeCompose {
  @JvmStatic
  fun installHeader(view: ComposeView, sportSpinner: ClassicSpinner) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
      RelvenTheme {
        Box(
          modifier = Modifier.fillMaxWidth().background(RelvenColors.Background).heightIn(min = 56.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          AndroidView(
            factory = { sportSpinner },
            modifier = Modifier.width(240.dp).heightIn(min = 56.dp).padding(start = 16.dp),
          )
        }
      }
    }
  }

  @JvmStatic
  fun installReadiness(view: ComposeView) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @JvmStatic
  fun setReadiness(
    view: ComposeView,
    presentation: StartReadinessPresentation,
    detailsExpanded: Boolean,
    canStartWorkout: Boolean,
    startWorkoutLabel: String,
    onToggleDetails: Runnable,
    onStartWorkout: Runnable,
  ) {
    view.setContent {
      RelvenTheme {
        StartReadinessBar(
          presentation,
          detailsExpanded,
          canStartWorkout,
          startWorkoutLabel,
          onToggleDetails,
          onStartWorkout,
        )
      }
    }
  }
}

@Composable
private fun StartReadinessBar(
  presentation: StartReadinessPresentation,
  detailsExpanded: Boolean,
  canStartWorkout: Boolean,
  startWorkoutLabel: String,
  onToggleDetails: Runnable,
  onStartWorkout: Runnable,
) {
  val readinessState =
    stringResource(
      if (detailsExpanded) R.string.start_readiness_expanded
      else R.string.start_readiness_collapsed,
    )
  Column(
    modifier = Modifier.fillMaxWidth().background(RelvenColors.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    if (presentation.summaryItems.isNotEmpty()) Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier =
          Modifier.weight(1f)
            .clickable(role = Role.Button) { onToggleDetails.run() }
            .semantics {
              stateDescription = readinessState
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        presentation.summaryItems.forEach { item ->
          item.iconResource?.let { icon ->
            androidx.compose.foundation.Image(
              painter = androidx.compose.ui.res.painterResource(icon),
              contentDescription = item.contentDescription,
              modifier = Modifier.size(16.dp),
            )
          }
          if (item.message.isNotEmpty()) {
            Text(
              item.message,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Icon(
          imageVector = if (detailsExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
          contentDescription = null,
          tint = RelvenColors.MutedText,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    if (canStartWorkout) {
      Button(
        onClick = onStartWorkout::run,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
      ) {
        Text(
          startWorkoutLabel,
          fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
      }
    }
    presentation.gpsDetails?.let { detail ->
      Text(
        detail.message,
        color = RelvenColors.MutedText,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
      )
    }
  }
}
