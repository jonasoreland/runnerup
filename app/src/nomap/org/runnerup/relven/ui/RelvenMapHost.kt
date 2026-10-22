package org.runnerup.relven.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout

internal fun createActivityMapHost(
    context: Context,
    route: List<RoutePoint>,
    metric: RouteMetric): ActivityMapHost = NoMapHost(context)

private class NoMapHost(context: Context) : ActivityMapHost {
  override val view: View = FrameLayout(context)
  override fun updateMetric(metric: RouteMetric) = Unit
  override fun onStart() = Unit
  override fun onResume() = Unit
  override fun onPause() = Unit
  override fun onStop() = Unit
  override fun onSaveInstanceState(state: Bundle) = Unit
  override fun onLowMemory() = Unit
  override fun onDestroy() = Unit
}
