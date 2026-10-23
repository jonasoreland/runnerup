package org.runnerup.relven.ui

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.runnerup.common.util.Constants
import org.runnerup.db.DBHelper
import org.runnerup.tracker.Tracker
import org.runnerup.tracker.TrackerLocationObserver

/** UI-only projection of persisted Tracker fixes; it never subscribes to a location provider. */
internal class ActiveWorkoutMapController(context: Context) : TrackerLocationObserver {
  private val appContext = context.applicationContext
  val mapHost: LiveWorkoutMapHost = createLiveWorkoutMapHost(appContext)
  val gpsState = ActiveWorkoutGpsState()
  private val route = ActiveWorkoutRouteState()
  private val mapRenderGate = LiveRouteRenderGate()
  private val mainHandler = Handler(Looper.getMainLooper())
  private var tracker: Tracker? = null
  private var latestLocation: Location? = null
  private val gpsRefresh = object : Runnable {
    override fun run() {
      latestLocation?.let(::updateGps)
      if (tracker != null) mainHandler.postDelayed(this, 1_000L)
    }
  }

  fun attach(value: Tracker) {
    if (tracker === value) return
    detach()
    tracker = value
    value.addLocationObserver(this)
    mainHandler.removeCallbacks(gpsRefresh)
    mainHandler.post(gpsRefresh)
    value.lastKnownLocation?.let { location ->
      latestLocation = Location(location)
      updateGps(location)
      val point = route.points.lastOrNull()
      renderRouteNow(point ?: routePoint(location, value.distance, value.timeMs / 1000.0))
    }
    val activityId = value.activityId
    Thread(
            {
              val persistedRoute =
                  runCatching {
                    val database = DBHelper.getReadableDatabase(appContext)
                    try {
                      readActivityRoute(database, activityId)
                    } finally {
                      DBHelper.closeDB(database)
                    }
                  }.getOrDefault(emptyList())
              mainHandler.post {
                if (tracker !== value) return@post
                val livePoints = route.points
                val restored = mergePersistedAndLiveRoute(persistedRoute, livePoints)
                route.restore(restored, value.lastKnownLocation?.elapsedRealtimeNanos)
                val location = latestLocation ?: value.lastKnownLocation
                renderRouteNow(
                    location?.let { routePoint(it, value.distance, value.timeMs / 1000.0) })
              }
            },
            "relven-live-route-restore")
        .start()
  }

  fun detach() {
    tracker?.removeLocationObserver(this)
    tracker = null
    mainHandler.removeCallbacks(gpsRefresh)
  }

  fun onPaused(paused: Boolean) {
    if (paused) route.onPause() else route.onResume()
    latestLocation?.let { location ->
      renderRouteNow(
          routePoint(location, tracker?.distance ?: 0.0, (tracker?.timeMs ?: 0L) / 1000.0))
    }
  }

  override fun onAcceptedLocation(
      location: Location,
      persistedType: Int,
      totalDistanceMeters: Double,
      activeTimeNanos: Long,
      heartRate: Int?,
      elevationMeters: Double?,
  ) {
    val copy = Location(location)
    mainHandler.post {
      if (tracker == null) return@post
      latestLocation = copy
      route.onLocation(
          copy,
          persistedType,
          activeTimeNanos / 1_000_000_000.0,
          totalDistanceMeters,
          heartRate?.toDouble(),
          if (copy.hasSpeed()) copy.speed.toDouble() else null,
          elevationMeters)
      val currentPoint =
          routePoint(
              copy,
              totalDistanceMeters,
              activeTimeNanos / 1_000_000_000.0,
              heartRate,
              elevationMeters)
      if (persistedType != Constants.DB.LOCATION.TYPE_GPS) {
        renderRouteNow(currentPoint)
      } else if (mapRenderGate.shouldRender(SystemClock.elapsedRealtime())) {
        mapHost.updateLiveRoute(route.points, currentPoint)
      }
      updateGps(copy)
    }
  }

  private fun updateGps(location: Location) {
    val age = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L)
    gpsState.value.value = gpsSignalState(
        if (location.hasAccuracy()) location.accuracy else null,
        age,
        location.latitude.isFinite() && location.longitude.isFinite())
  }

  private fun renderRouteNow(currentLocation: RoutePoint?) {
    mapRenderGate.reset()
    mapHost.updateLiveRoute(route.points, currentLocation)
  }

  private fun routePoint(
      location: Location,
      distance: Double,
      elapsedSeconds: Double,
      heartRate: Int? = null,
      elevationMeters: Double? = null,
  ) = RoutePoint(
      latitude = location.latitude,
      longitude = location.longitude,
      lap = 0,
      elapsedSeconds = elapsedSeconds,
      distanceMeters = distance,
      heartRate = heartRate?.toDouble(),
      speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else null,
      elevationMeters = elevationMeters,
      segment = 0)

}
