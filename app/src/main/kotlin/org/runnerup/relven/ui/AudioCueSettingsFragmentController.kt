package org.runnerup.relven.ui

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Adds the voice settings fragment only after its Compose-owned container is attached. */
internal class AudioCueSettingsFragmentController(
    private val container: FragmentContainerView,
    private val fragmentManager: FragmentManager,
    private val fragmentFactory: () -> Fragment,
    private val lifecycleOwner: LifecycleOwner
) : View.OnAttachStateChangeListener, DefaultLifecycleObserver {
  init {
    container.addOnAttachStateChangeListener(this)
    if (container.isAttachedToWindow) lifecycleOwner.lifecycle.addObserver(this)
    ensureFragmentWhenAttached()
  }

  override fun onViewAttachedToWindow(view: View) {
    lifecycleOwner.lifecycle.addObserver(this)
    ensureFragmentWhenAttached()
  }

  override fun onViewDetachedFromWindow(view: View) {
    lifecycleOwner.lifecycle.removeObserver(this)
  }

  override fun onResume(owner: LifecycleOwner) {
    container.post { ensureFragmentWhenAttached() }
  }

  fun ensureFragmentWhenAttached() {
    if (!container.isAttachedToWindow || fragmentManager.isStateSaved) return
    val existing = fragmentManager.findFragmentById(container.id)
    if (existing?.view?.parent !== container) {
      fragmentManager.commitNow {
        setReorderingAllowed(true)
        replace(container.id, fragmentFactory())
      }
    }
  }
}
