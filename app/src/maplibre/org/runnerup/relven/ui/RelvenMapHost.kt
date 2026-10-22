package org.runnerup.relven.ui

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.get
import org.runnerup.common.R

internal fun createActivityMapHost(
    context: Context,
    route: List<RoutePoint>,
    metric: RouteMetric): ActivityMapHost = RelvenMapHost(context, route, metric)

private class RelvenMapHost(
    context: Context,
    private val route: List<RoutePoint>,
    metric: RouteMetric) : ActivityMapHost {
  private val mapLibre = MapLibre.getInstance(context)
  private val root = FrameLayout(context)
  private val mapView = MapView(context)
  private val fallback = RouteOnlyView(context, route, metric)
  private val sourceId = "relven-activity-route-source"
  private var style: Style? = null
  private var currentMetric = metric
  private val failedLoadingListener = MapView.OnDidFailLoadingMapListener {
    mapView.visibility = View.GONE
    fallback.visibility = View.VISIBLE
  }

  override val view: View = root

  init {
    root.setBackgroundColor("#0A0F18".toColorInt())
    mapView.visibility = View.VISIBLE
    fallback.visibility = View.GONE
    root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
    root.addView(fallback, FrameLayout.LayoutParams(-1, -1))
    val attribution =
        TextView(context).apply {
          text = context.getString(R.string.route_map_attribution)
          textSize = 10f
          setTextColor(0xffffffff.toInt())
          setBackgroundColor(0xcc10131b.toInt())
          setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
          contentDescription = context.getString(R.string.route_map_attribution_description)
        }
    root.addView(
        attribution,
        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
          setMargins(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        })
    mapView.addOnDidFailLoadingMapListener(failedLoadingListener)
    mapView.onCreate(null)
    mapView.getMapAsync { map ->
      map.uiSettings.isCompassEnabled = true
      map.setStyle(STYLE_URL) { loadedStyle ->
        style = loadedStyle
        loadedStyle.addSource(GeoJsonSource(sourceId, buildRouteGeoJson(route, currentMetric)))
        loadedStyle.addLayer(
            LineLayer("relven-activity-route-line", sourceId).withProperties(
                lineColor(get("color")), lineWidth(5.0f), lineCap("round"), lineJoin("round")))
        fitRoute(map)
      }
    }
  }

  private fun fitRoute(map: org.maplibre.android.maps.MapLibreMap) {
    if (route.isEmpty()) return
    val bounds =
        LatLngBounds.Builder().apply {
          route.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
    runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(root.context, 42))) }
  }

  override fun updateMetric(metric: RouteMetric) {
    currentMetric = metric
    fallback.setMetric(metric)
    style?.let { (it.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(buildRouteGeoJson(route, metric)) }
  }

  override fun onStart() = mapView.onStart()
  override fun onResume() = mapView.onResume()
  override fun onPause() = mapView.onPause()
  override fun onStop() = mapView.onStop()
  override fun onSaveInstanceState(state: Bundle) = mapView.onSaveInstanceState(state)
  override fun onLowMemory() = mapView.onLowMemory()

  override fun onDestroy() {
    mapView.removeOnDidFailLoadingMapListener(failedLoadingListener)
    mapView.onDestroy()
    style = null
  }

  companion object {
    private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
  }
}

@SuppressLint("ViewConstructor")
private class RouteOnlyView(
    context: Context,
    private val route: List<RoutePoint>,
    metric: RouteMetric) : View(context) {
  private var selectedMetric = metric
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = dp(context, 6).toFloat()
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  fun setMetric(metric: RouteMetric) {
    selectedMetric = metric
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (route.size < 2) return
    val minLat = route.minOf { it.latitude }
    val maxLat = route.maxOf { it.latitude }
    val minLon = route.minOf { it.longitude }
    val maxLon = route.maxOf { it.longitude }
    val pad = dp(context, 32).toFloat()
    val width = (this.width - pad * 2).coerceAtLeast(1f)
    val height = (this.height - pad * 2).coerceAtLeast(1f)
    val averageLatitudeRadians = Math.toRadians((minLat + maxLat) / 2.0)
    val longitudeScale = kotlin.math.cos(averageLatitudeRadians).coerceAtLeast(0.01)
    val lonRange = ((maxLon - minLon) * longitudeScale).takeIf { it > 0.0 } ?: 1e-6
    val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1e-6
    val scale = minOf(width / lonRange.toFloat(), height / latRange.toFloat())
    val range = routeMetricRange(route, selectedMetric)
    for (index in 1 until route.size) {
      val before = route[index - 1]
      val after = route[index]
      if (before.lap != after.lap || before.segment != after.segment) continue
      paint.color = routeMetricColor(selectedMetric.value(after, before), range).toColorInt()
      val startX = pad + ((before.longitude - minLon) * longitudeScale).toFloat() * scale
      val startY = height + pad - (before.latitude - minLat).toFloat() * scale
      val endX = pad + ((after.longitude - minLon) * longitudeScale).toFloat() * scale
      val endY = height + pad - (after.latitude - minLat).toFloat() * scale
      canvas.drawLine(startX, startY, endX, endY, paint)
    }
  }
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
