package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Measures the floating home navigation as part of the same viewport as each screen. */
@Composable
internal fun HomeNavigationScaffold(
    modifier: Modifier = Modifier,
    navigationBar: @Composable () -> Unit,
    content: @Composable () -> Unit) {
  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = RelvenColors.Background,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = navigationBar) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)) {
          content()
        }
      }
}
