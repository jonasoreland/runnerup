package org.runnerup.relven.ui

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import org.runnerup.workout.Workout

/** Adds the start fragment only after AndroidView's container joins the activity hierarchy. */
internal class StartFragmentContainerController(
    private val container: FragmentContainerView,
    private val fragmentManager: FragmentManager,
    private val fragmentFactory: () -> Fragment,
    private val onFragmentReady: (Fragment, Workout?) -> Unit
) : View.OnAttachStateChangeListener {
  private var plannedWorkout: Workout? = null

  init {
    container.addOnAttachStateChangeListener(this)
  }

  fun updatePlannedWorkout(workout: Workout?) {
    plannedWorkout = workout
    ensureFragmentWhenAttached()
  }

  override fun onViewAttachedToWindow(view: View) {
    ensureFragmentWhenAttached()
  }

  override fun onViewDetachedFromWindow(view: View) = Unit

  private fun ensureFragmentWhenAttached() {
    if (!container.isAttachedToWindow || fragmentManager.isStateSaved) return

    if (fragmentManager.findFragmentById(container.id) == null) {
      fragmentManager.commitNow {
        setReorderingAllowed(true)
        replace(container.id, fragmentFactory())
      }
    }
    fragmentManager.findFragmentById(container.id)?.let { onFragmentReady(it, plannedWorkout) }
  }
}
