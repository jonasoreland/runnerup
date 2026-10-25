package org.runnerup.relven.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import android.widget.FrameLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.runnerup.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioCueSettingsFragmentControllerTest {
  @Test
  fun attachedContainerAddsOneFragmentAndReusesItOnRepeatedUpdates() {
    val activityController = Robolectric.buildActivity(FragmentActivity::class.java).setup()
    val activity = activityController.get()
    val container = FragmentContainerView(activity).apply {
      id = R.id.relven_audio_cue_settings_container
    }
    activity.setContentView(container)

    AudioCueSettingsFragmentController(
        container, activity.supportFragmentManager, ::TestFragment, activity)
        .ensureFragmentWhenAttached()
    val first = activity.supportFragmentManager.findFragmentById(container.id)
    AudioCueSettingsFragmentController(container, activity.supportFragmentManager, ::TestFragment, activity)
        .ensureFragmentWhenAttached()

    assertTrue(container.isAttachedToWindow)
    assertTrue(first is TestFragment)
    assertEquals(1, activity.supportFragmentManager.fragments.count { it is TestFragment })
    activityController.destroy()
  }

  @Test
  fun savedFragmentManagerStateDefersFragmentCommit() {
    val activityController = Robolectric.buildActivity(FragmentActivity::class.java).setup()
    val activity = activityController.get()
    val container = FragmentContainerView(activity).apply {
      id = R.id.relven_audio_cue_settings_container
    }
    activity.setContentView(container)
    activityController.saveInstanceState(Bundle())

    AudioCueSettingsFragmentController(container, activity.supportFragmentManager, ::TestFragment, activity)

    assertTrue(activity.supportFragmentManager.isStateSaved)
    assertNull(activity.supportFragmentManager.findFragmentById(container.id))
    activityController.pause().stop().restart().start().resume().visible()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertTrue(activity.supportFragmentManager.findFragmentById(container.id) is TestFragment)
    activityController.destroy()
  }

  @Test
  fun productionFragmentFactoryProvidesRequiredDefaultArguments() {
    val fragment = newAudioCueSettingsFragment()

    assertTrue(fragment.arguments != null)
    assertEquals(null, fragment.arguments?.getString("name"))
  }

  @Test
  fun reenteringTheDestinationReplacesFragmentWithOneAttachedToTheNewContainer() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val firstContainer = FragmentContainerView(activity).apply {
      id = R.id.relven_audio_cue_settings_container
    }
    activity.setContentView(firstContainer)
    AudioCueSettingsFragmentController(
        firstContainer, activity.supportFragmentManager, ::TestFragment, activity)
        .ensureFragmentWhenAttached()
    val first = activity.supportFragmentManager.findFragmentById(firstContainer.id)

    activity.setContentView(FrameLayout(activity))
    val reopenedContainer = FragmentContainerView(activity).apply {
      id = R.id.relven_audio_cue_settings_container
    }
    activity.setContentView(reopenedContainer)
    AudioCueSettingsFragmentController(
        reopenedContainer, activity.supportFragmentManager, ::TestFragment, activity)
        .ensureFragmentWhenAttached()

    val reopened = activity.supportFragmentManager.findFragmentById(reopenedContainer.id)
    assertTrue(reopened is TestFragment)
    assertTrue(reopened !== first)
    assertTrue(reopened?.view?.parent === reopenedContainer)
    assertEquals(1, activity.supportFragmentManager.fragments.count { it is TestFragment })
    activity.finish()
  }

  class TestFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View = FrameLayout(requireContext())
  }
}
