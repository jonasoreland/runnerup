package org.runnerup.relven.ui

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.BuildConfig
import org.runnerup.db.DBHelper
import org.runnerup.util.Formatter
import org.runnerup.util.MapViewWrapper
import org.runnerup.util.MapWrapper
import org.runnerup.view.DetailActivity

internal class ActivityMapActivity : ComponentActivity() {
  private var activityId: Long = -1L
  private var screenState by mutableStateOf<ActivityMapScreenState>(ActivityMapScreenState.Loading)
  private var mapDatabase: SQLiteDatabase? = null
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
            onOpenFullDetails = ::openFullDetails,
            activityId = activityId,
            mapContent = { modifier ->
              mapDatabase?.let { database ->
                AndroidView(
                    factory = {
                      (mapHost ?: createMapHost(context, database, activityId)).view
                    },
                    modifier = modifier)
              }
            })
      }
    }
    loadActivity()
  }

  private fun loadActivity() {
    val request = ++requestVersion
    screenState = ActivityMapScreenState.Loading
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val database = DBHelper.getReadableDatabase(applicationContext)
              try {
              val state =
                  readActivityMapState(
                      database,
                      activityId,
                      mapEnabled = BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED)
              database to state
              } catch (error: Throwable) {
                DBHelper.closeDB(database)
                throw error
              }
            }
          }
      if (request != requestVersion || isFinishing || isDestroyed) {
        result.getOrNull()?.first?.let(DBHelper::closeDB)
        return@launch
      }
      result.fold(
          onSuccess = { (database, state) ->
            mapDatabase?.let(DBHelper::closeDB)
            mapDatabase = database
            screenState =
                when (state) {
                  ActivityMapLoadState.NotFound -> ActivityMapScreenState.NotFound
                  ActivityMapLoadState.RouteUnavailable -> ActivityMapScreenState.RouteUnavailable
                  ActivityMapLoadState.Unavailable -> ActivityMapScreenState.Unavailable
                  is ActivityMapLoadState.Loaded -> ActivityMapScreenState.Loaded(state.activity)
                }
          },
          onFailure = { screenState = ActivityMapScreenState.Unavailable })
    }
  }

  private fun openFullDetails(id: Long) {
    startActivity(
        Intent(this, DetailActivity::class.java)
            .putExtra("ID", id)
            .putExtra("mode", "details"))
  }

  private fun createMapHost(
      context: Context,
      database: SQLiteDatabase,
      activityId: Long
  ): ActivityMapHost =
      ActivityMapHost(context, database, activityId, Formatter(context))
          .also { host ->
            mapHost = host
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.onStart()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host.onResume()
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
    val host = mapHost
    mapHost = null
    val database = mapDatabase
    mapDatabase = null
    if (host == null) database?.let(DBHelper::closeDB) else host.onDestroy {
      database?.let(DBHelper::closeDB)
    }
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_ACTIVITY_ID = "org.runnerup.relven.ui.activity_map_id"

    fun intent(context: Context, activityId: Long): Intent =
        Intent(context, ActivityMapActivity::class.java).putExtra(EXTRA_ACTIVITY_ID, activityId)
  }
}

private class ActivityMapHost(
    context: Context,
    database: SQLiteDatabase,
    activityId: Long,
    formatter: Formatter) {
  val view = LinearLayout(context)
  private val mapWrapper: MapWrapper

  init {
    view.orientation = LinearLayout.VERTICAL
    MapWrapper.start(context)
    val mapView = MapViewWrapper(context)
    view.addView(
        mapView,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
    mapWrapper = MapWrapper(context, database, activityId, formatter, mapView)
    mapWrapper.onCreate(null)
  }

  fun onStart() = mapWrapper.onStart()

  fun onResume() = mapWrapper.onResume()

  fun onPause() = mapWrapper.onPause()

  fun onStop() = mapWrapper.onStop()

  fun onSaveInstanceState(state: Bundle) = mapWrapper.onSaveInstanceState(state)

  fun onLowMemory() = mapWrapper.onLowMemory()

  fun onDestroy(afterBackgroundWork: () -> Unit) = mapWrapper.onDestroy(afterBackgroundWork)
}
