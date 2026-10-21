package org.runnerup.relven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.workout.feedback.CountdownDisplay

data class RunCountdownPresentation(val occupiesSpace: Boolean, val text: String?)

class RunCountdownStateHolder : CountdownDisplay {
  var presentation by mutableStateOf(RunCountdownPresentation(occupiesSpace = false, text = null))
    private set

  override fun show(text: String) {
    presentation = RunCountdownPresentation(occupiesSpace = true, text = text.takeIf { it.isNotEmpty() })
  }

  override fun hide() {
    presentation = RunCountdownPresentation(occupiesSpace = true, text = null)
  }

  override fun remove() {
    presentation = RunCountdownPresentation(occupiesSpace = false, text = null)
  }

  @Composable
  fun Content() {
    RunCountdownScreen(presentation)
  }
}

object RunCountdownCompose {
  @JvmStatic
  fun install(view: ComposeView, state: RunCountdownStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun RunCountdownScreen(presentation: RunCountdownPresentation) {
  if (presentation.occupiesSpace) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .background(
              Brush.linearGradient(
                colors = listOf(Color(0xDD000000), Color(0xDD2ECCFA)),
              ),
              RoundedCornerShape(7.dp),
            )
            .border(2.dp, Color.White, RoundedCornerShape(7.dp))
            .padding(20.dp)
            .heightIn(min = 36.dp),
        contentAlignment = Alignment.Center,
      ) {
        presentation.text?.let { text ->
          Text(text = text, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
