package org.runnerup.relven.ui

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.runnerup.workout.RelvenWorkoutImporter
import org.runnerup.workout.Workout

@RunWith(RobolectricTestRunner::class)
class StartFragmentContainerControllerTest {
  @Test
  fun createsStartFragmentOnlyAfterContainerIsAttached() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val containerId = View.generateViewId()
    val container = FragmentContainerView(activity).apply { id = containerId }
    var deliveredWorkout: Workout? = null
    val workout =
        RelvenWorkoutImporter.parse(
            """{"schemaVersion":3,"id":"planned-run","name":"Easy run","sport":"running","steps":[{"id":"run","kind":"segment","phase":"work","end":{"type":"time","seconds":600}}]}""")
    val controller =
        StartFragmentContainerController(
            container,
            activity.supportFragmentManager,
            fragmentFactory = { Fragment() },
            onFragmentReady = { _, selectedWorkout -> deliveredWorkout = selectedWorkout })

    controller.updatePlannedWorkout(workout)
    assertNull(activity.supportFragmentManager.findFragmentById(containerId))
    assertNull(deliveredWorkout)

    activity.setContentView(container)
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    assertNotNull(activity.supportFragmentManager.findFragmentById(containerId))
    assertSame(workout, deliveredWorkout)

    controller.updatePlannedWorkout(null)
    assertNull(deliveredWorkout)
  }
}
