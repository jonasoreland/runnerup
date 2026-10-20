package org.runnerup.relven.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StartReadinessCompose {
  @JvmStatic
  fun install(view: ComposeView) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @JvmStatic
  fun setContent(view: ComposeView, presentation: StartReadinessPresentation) {
    view.setContent {
      RelvenTheme {
        StartReadinessStatus(presentation)
      }
    }
  }
}

@Composable
private fun StartReadinessStatus(presentation: StartReadinessPresentation) {
  Column(
    modifier = Modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      presentation.summaryItems.forEach { item ->
        StartReadinessLabel(item)
      }
    }
    presentation.gpsDetails?.let { detail ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        StartReadinessLabel(detail)
      }
    }
  }
}

@Composable
private fun StartReadinessLabel(item: StartReadinessItem) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    item.iconResource?.let { icon ->
      Image(
        painter = painterResource(icon),
        contentDescription = item.contentDescription,
        modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(4.dp))
    }
    if (item.message.isNotEmpty()) {
      Text(
        text = item.message,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
      )
    }
  }
}
