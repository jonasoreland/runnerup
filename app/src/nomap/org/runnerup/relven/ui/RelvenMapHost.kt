package org.runnerup.relven.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout

internal fun createActivityMapHost(
    context: Context,
    route: List<RoutePoint>,
    metric: RouteMetric): ActivityMapHost = NoMapHost(context)

internal fun createLiveWorkoutMapHost(context: Context): LiveWorkoutMapHost =
    NoMapWorkoutHost(context)

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

private class NoMapWorkoutHost(context: Context) : LiveWorkoutMapHost {
  private val routeView = LiveRouteFallbackView(context)
  override val view: View = FrameLayout(context).apply { addView(routeView, FrameLayout.LayoutParams(-1, -1)) }
  override fun updateMetric(metric: RouteMetric) = Unit
  override fun updateLiveRoute(route: List<RoutePoint>, currentLocation: RoutePoint?) =
      routeView.update(route, currentLocation)
  override fun recenter() = Unit
  override fun onStart() = Unit
  override fun onResume() = Unit
  override fun onPause() = Unit
  override fun onStop() = Unit
  override fun onSaveInstanceState(state: Bundle) = Unit
  override fun onLowMemory() = Unit
  override fun onDestroy() = Unit
}

private class LiveRouteFallbackView(context: Context) : View(context) {
  private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  private var route: List<RoutePoint> = emptyList()
  private var currentLocation: RoutePoint? = null

  fun update(route: List<RoutePoint>, currentLocation: RoutePoint?) {
    this.route = route.toList()
    this.currentLocation = currentLocation
    invalidate()
  }

  override fun onDraw(canvas: android.graphics.Canvas) {
    super.onDraw(canvas)
    val points = route.filter(RoutePoint::hasValidCoordinates)
    if (points.isEmpty()) return
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val padding = 32f * resources.displayMetrics.density
    val scaleX = (width - 2 * padding) / (maxLon - minLon).coerceAtLeast(1e-6).toFloat()
    val scaleY = (height - 2 * padding) / (maxLat - minLat).coerceAtLeast(1e-6).toFloat()
    val scale = minOf(scaleX, scaleY)
    paint.color = android.graphics.Color.rgb(41, 121, 255)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 5f * resources.displayMetrics.density
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    for (index in 1 until points.size) {
      if (points[index - 1].segment != points[index].segment) continue
      canvas.drawLine(x(points[index - 1], minLon, padding, scale), y(points[index - 1], maxLat, padding, scale),
          x(points[index], minLon, padding, scale), y(points[index], maxLat, padding, scale), paint)
    }
    currentLocation?.takeIf(RoutePoint::hasValidCoordinates)?.let { point ->
      paint.color = android.graphics.Color.rgb(255, 106, 0)
      paint.style = android.graphics.Paint.Style.FILL
      canvas.drawCircle(x(point, minLon, padding, scale), y(point, maxLat, padding, scale),
          8f * resources.displayMetrics.density, paint)
    }
  }

  private fun x(point: RoutePoint, minLon: Double, padding: Float, scale: Float) =
      padding + (point.longitude - minLon).toFloat() * scale

  private fun y(point: RoutePoint, maxLat: Double, padding: Float, scale: Float) =
      padding + (maxLat - point.latitude).toFloat() * scale
}
