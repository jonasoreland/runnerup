package org.runnerup.relven.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentContainerView
import org.runnerup.R
import org.runnerup.view.AudioCueSettingsFragment

internal fun newAudioCueSettingsFragment(): AudioCueSettingsFragment =
    AudioCueSettingsFragment().apply { arguments = Bundle() }

@Composable
internal fun AudioCueSettingsDestination(
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier) {
  val lifecycleOwner = LocalLifecycleOwner.current
  AndroidView(
      factory = { context ->
        FragmentContainerView(context).apply {
          id = R.id.relven_audio_cue_settings_container
          setBackgroundColor(android.graphics.Color.rgb(10, 15, 24))
          AudioCueSettingsFragmentController(
              this,
              fragmentManager,
              fragmentFactory = ::newAudioCueSettingsFragment,
              lifecycleOwner = lifecycleOwner)
        }
      },
      modifier = modifier)
}
