package org.runnerup.relven.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import org.runnerup.R
import org.runnerup.view.StartFragment

@Composable
internal fun StartDestinationScreen(fragmentManager: FragmentManager) {
  AndroidView(
      factory = { context ->
        FragmentContainerView(context).apply {
          id = R.id.relven_start_fragment_container
          setBackgroundColor(android.graphics.Color.rgb(10, 15, 24))
          if (fragmentManager.findFragmentById(id) == null) {
            fragmentManager.commit {
              setReorderingAllowed(true)
              replace(id, StartFragment())
            }
          }
        }
      },
      modifier = Modifier.fillMaxSize().padding(bottom = 104.dp))
}
