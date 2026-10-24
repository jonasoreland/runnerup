package org.runnerup.relven.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import org.runnerup.R
import org.runnerup.view.StartFragment
import org.runnerup.workout.Workout

@Composable
internal fun StartDestinationScreen(
    fragmentManager: FragmentManager,
    plannedWorkout: Workout? = null) {
  AndroidView(
      factory = { context ->
        FragmentContainerView(context).apply {
          id = R.id.relven_start_fragment_container
          setBackgroundColor(android.graphics.Color.rgb(10, 15, 24))
          setTag(
              R.id.relven_start_fragment_controller,
              StartFragmentContainerController(
                  this,
                  fragmentManager,
                  fragmentFactory = ::StartFragment,
                  onFragmentReady = { fragment: Fragment, workout ->
                    (fragment as? StartFragment)?.usePlannedWorkout(workout)
                  }))
        }
      },
      update = { container ->
        (container.getTag(R.id.relven_start_fragment_controller) as? StartFragmentContainerController)
            ?.updatePlannedWorkout(plannedWorkout)
      },
      modifier = Modifier.fillMaxSize().statusBarsPadding())
}
