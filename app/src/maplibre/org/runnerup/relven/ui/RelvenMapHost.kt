package org.runnerup.relven.ui

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.toColorInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.get
import org.runnerup.common.R
import org.runnerup.R as AppR

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
  private val markerSourceId = "relven-activity-route-markers"
  private var style: Style? = null
  private var mapInstance: org.maplibre.android.maps.MapLibreMap? = null
  private var currentMetric = metric
  private val failedLoadingListener = MapView.OnDidFailLoadingMapListener {
    mapView.visibility = View.GONE
    fallback.visibility = View.VISIBLE
    recenterButton.visibility = View.GONE
  }
  private val recenterButton = ImageButton(context).apply {
    setImageResource(android.R.drawable.ic_menu_mylocation)
    setColorFilter("#FF5A1F".toColorInt())
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor("#172131".toColorInt())
      setStroke(dp(context, 1), "#FF5A1F".toColorInt())
    }
    setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
    contentDescription = context.getString(AppR.string.route_map_recenter)
    setOnClickListener { mapInstance?.let(::fitRoute) }
  }

  override val view: View = root

  init {
    root.setBackgroundColor("#0A0F18".toColorInt())
    mapView.visibility = View.VISIBLE
    fallback.visibility = View.GONE
    root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
    root.addView(fallback, FrameLayout.LayoutParams(-1, -1))
    root.addView(
        recenterButton,
        FrameLayout.LayoutParams(dp(context, 48), dp(context, 48), Gravity.END or Gravity.TOP).apply {
          setMargins(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        })
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
    root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
      attribution.maxWidth =
          routeMapAttributionMaxWidthPx(
              mapWidthPx = view.width,
              leftClearancePx = dp(context, 120),
              marginPx = dp(context, 8))
    }
    mapView.addOnDidFailLoadingMapListener(failedLoadingListener)
    mapView.onCreate(null)
    mapView.getMapAsync { map ->
      mapInstance = map
      map.uiSettings.isCompassEnabled = true
      map.setStyle(STYLE_URL) { loadedStyle ->
        style = loadedStyle
        loadedStyle.addSource(GeoJsonSource(sourceId, buildRouteGeoJson(route, currentMetric)))
        loadedStyle.addSource(GeoJsonSource(markerSourceId, buildRouteMarkersGeoJson(route)))
        loadedStyle.addLayer(
            LineLayer("relven-activity-route-line", sourceId).withProperties(
                lineColor(get("color")), lineWidth(5.0f), lineCap("round"), lineJoin("round")))
        loadedStyle.addLayer(
            CircleLayer("relven-activity-route-markers", markerSourceId).withProperties(
                circleColor(get("color")),
                circleRadius(
                    switchCase(
                        eq(get("coincident"), true),
                        switchCase(eq(get("kind"), "start"), literal(9.0f), literal(4.0f)),
                        literal(7.0f))),
                circleStrokeColor("#0A0F18"), circleStrokeWidth(2.0f)))
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
    mapInstance = null
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
    val validRoute = route.filter(RoutePoint::hasValidCoordinates)
    if (validRoute.size < 2) return
    val minLat = validRoute.minOf { it.latitude }
    val maxLat = validRoute.maxOf { it.latitude }
    val minLon = validRoute.minOf { it.longitude }
    val maxLon = validRoute.maxOf { it.longitude }
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
      if (!before.hasValidCoordinates() || !after.hasValidCoordinates()) continue
      if (before.lap != after.lap || before.segment != after.segment) continue
      paint.color = routeMetricColor(selectedMetric, selectedMetric.value(after, before), range).toColorInt()
      val startX = pad + ((before.longitude - minLon) * longitudeScale).toFloat() * scale
      val startY = height + pad - (before.latitude - minLat).toFloat() * scale
      val endX = pad + ((after.longitude - minLon) * longitudeScale).toFloat() * scale
      val endY = height + pad - (after.latitude - minLat).toFloat() * scale
      canvas.drawLine(startX, startY, endX, endY, paint)
    }
    val coincidentEndpoints =
        validRoute.first().latitude == validRoute.last().latitude &&
            validRoute.first().longitude == validRoute.last().longitude
    listOf(validRoute.first(), validRoute.last()).forEachIndexed { index, point ->
      val x = pad + ((point.longitude - minLon) * longitudeScale).toFloat() * scale
      val y = height + pad - (point.latitude - minLat).toFloat() * scale
      val markerRadius =
          dp(context, if (coincidentEndpoints) (if (index == 0) 9 else 4) else 6).toFloat()
      paint.style = Paint.Style.FILL
      paint.color = (if (index == 0) "#28A875" else "#FF5A1F").toColorInt()
      canvas.drawCircle(x, y, markerRadius, paint)
      paint.style = Paint.Style.STROKE
      paint.color = "#0A0F18".toColorInt()
      paint.strokeWidth = dp(context, 2).toFloat()
      canvas.drawCircle(x, y, markerRadius, paint)
      paint.strokeWidth = dp(context, 6).toFloat()
    }
  }
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
