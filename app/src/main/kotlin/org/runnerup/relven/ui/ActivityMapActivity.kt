package org.runnerup.relven.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.BuildConfig
import org.runnerup.db.DBHelper

internal class ActivityMapActivity : ComponentActivity() {
  private var activityId: Long = -1L
  private var screenState by mutableStateOf<ActivityMapScreenState>(ActivityMapScreenState.Loading)
  private var mapHost: ActivityMapHost? = null
  private var requestVersion = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1L)
    setContent {
      val context = LocalContext.current
      RelvenTheme {
        ActivityMapScreen(
            state = screenState,
            onBack = ::finish,
            onRetry = ::loadActivity,
            onOpenFullDetails = { finish() },
            activityId = activityId,
            mapContent = { modifier, route, metric ->
              AndroidView(
                  factory = {
                    createActivityMapHost(context, route, metric).also { host ->
                      mapHost = host
                      if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.onStart()
                      if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host.onResume()
                    }.view
                  },
                  update = { mapHost?.updateMetric(metric) },
                  modifier = modifier)
            })
      }
    }
    loadActivity()
  }

  private fun loadActivity() {
    val request = ++requestVersion
    mapHost?.onDestroy()
    mapHost = null
    screenState = ActivityMapScreenState.Loading
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val database = DBHelper.getReadableDatabase(applicationContext)
              try {
                readActivityMapState(
                    database,
                    activityId,
                    mapEnabled = BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED)
              } finally {
                DBHelper.closeDB(database)
              }
            }
          }
      if (request != requestVersion || isFinishing || isDestroyed) return@launch
      result.fold(
          onSuccess = { state ->
            screenState =
                when (state) {
                  ActivityMapLoadState.NotFound -> ActivityMapScreenState.NotFound
                  ActivityMapLoadState.RouteUnavailable -> ActivityMapScreenState.RouteUnavailable
                  ActivityMapLoadState.Unavailable -> ActivityMapScreenState.Unavailable
                  is ActivityMapLoadState.Loaded ->
                      ActivityMapScreenState.Loaded(state.activity, state.route)
                }
          },
          onFailure = { screenState = ActivityMapScreenState.Unavailable })
    }
  }

  override fun onStart() {
    super.onStart()
    mapHost?.onStart()
  }

  override fun onResume() {
    super.onResume()
    mapHost?.onResume()
  }

  override fun onPause() {
    mapHost?.onPause()
    super.onPause()
  }

  override fun onStop() {
    mapHost?.onStop()
    super.onStop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    mapHost?.onSaveInstanceState(outState)
  }

  override fun onLowMemory() {
    super.onLowMemory()
    mapHost?.onLowMemory()
  }

  override fun onDestroy() {
    mapHost?.onDestroy()
    mapHost = null
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_ACTIVITY_ID = "org.runnerup.relven.ui.activity_map_id"

    fun intent(context: android.content.Context, activityId: Long): Intent =
        Intent(context, ActivityMapActivity::class.java).putExtra(EXTRA_ACTIVITY_ID, activityId)
  }
}
