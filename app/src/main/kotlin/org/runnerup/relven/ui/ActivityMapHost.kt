package org.runnerup.relven.ui

import android.os.Bundle
import android.view.View

internal interface ActivityMapHost {
  val view: View
  fun updateMetric(metric: RouteMetric)
  fun onStart()
  fun onResume()
  fun onPause()
  fun onStop()
  fun onSaveInstanceState(state: Bundle)
  fun onLowMemory()
  fun onDestroy()
}

internal interface LiveWorkoutMapHost : ActivityMapHost {
  fun updateLiveRoute(route: List<RoutePoint>, currentLocation: RoutePoint?)
  fun recenter()
}
