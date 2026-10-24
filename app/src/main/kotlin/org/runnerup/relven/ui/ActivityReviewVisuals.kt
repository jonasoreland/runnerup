package org.runnerup.relven.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.runnerup.BuildConfig
import org.runnerup.R as AppR
import org.runnerup.common.R as CommonR
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

@Composable
internal fun ActivityReviewOverview(activity: HistoryActivityDetails) {
  val context = LocalContext.current
  val formatter = remember(context) { Formatter(context) }
  val sport = Sport.textOf(androidx.compose.ui.platform.LocalResources.current, activity.sport)
  val date =
      remember(activity.startedAtSeconds) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(activity.startedAtSeconds * 1000L))
      }
  val measurements = activityDetailMeasurements(activity)
  val metrics = buildList {
    measurements.distanceMeters?.let {
      add(
          stringResource(CommonR.string.Home_activity_detail_distance) to
              formatter.formatDistance(Formatter.Format.TXT_LONG, it.toLong()))
    }
    measurements.durationSeconds?.let {
      add(
          stringResource(CommonR.string.Home_activity_detail_duration) to
              formatter.formatElapsedTime(Formatter.Format.TXT_LONG, it))
    }
    measurements.averageSpeedMetersPerSecond?.let {
      add(
          formatter.formatVelocityLabel() to
              formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, it))
    }
    measurements.averageHeartRate?.let {
      add(stringResource(CommonR.string.Home_activity_detail_average_hr) to heartRateLabel(it))
    }
    measurements.maximumHeartRate?.let {
      add(stringResource(CommonR.string.Home_activity_detail_maximum_hr) to heartRateLabel(it))
    }
    activity.averageCadence?.let {
      add(
          stringResource(CommonR.string.cadence) to
              formatter.format(
                  Formatter.Format.TXT_LONG, org.runnerup.workout.Dimension.CAD, it))
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(sport, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
      Text(date, color = RelvenColors.MutedText, fontSize = 14.sp)
    }
    metrics.chunked(2).forEach { rowMetrics ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rowMetrics.forEach { (label, value) -> ActivityMetric(label, value, Modifier.weight(1f)) }
        if (rowMetrics.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
      }
    }
  }
}

@Composable
internal fun ActivityReviewMapCard(activity: HistoryActivityDetails, onOpenFullMap: () -> Unit) {
  val mapEnabled = BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED
  if (!mapEnabled || Sport.isWithoutGps(activity.sport)) return
  if (!activityHasMapRoute(activity, mapEnabled) || activity.route.size < 2) {
    Surface(
        color = RelvenColors.Surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(AppR.string.activity_review_route),
            color = RelvenColors.Text,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(
            stringResource(AppR.string.activity_review_no_route),
            color = RelvenColors.MutedText)
      }
    }
    return
  }
  var selectedMetric by remember(activity.id) {
    mutableStateOf(availableRouteMetrics(activity.route).firstOrNull() ?: RouteMetric.STANDARD)
  }
  val context = LocalContext.current
  val route = activity.route
  val mapHost = remember(activity.id) { createActivityMapHost(context, route, selectedMetric) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(selectedMetric) { mapHost.updateMetric(selectedMetric) }
  DisposableEffect(lifecycle, mapHost) {
    val observer =
        LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_START -> mapHost.onStart()
            Lifecycle.Event.ON_RESUME -> mapHost.onResume()
            Lifecycle.Event.ON_PAUSE -> mapHost.onPause()
            Lifecycle.Event.ON_STOP -> mapHost.onStop()
            else -> Unit
          }
        }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      mapHost.onStop()
      mapHost.onDestroy()
    }
  }

  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(22.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(AppR.string.activity_review_route),
            color = RelvenColors.Text,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onOpenFullMap) {
          Text(stringResource(AppR.string.activity_review_open_map), color = RelvenColors.Orange)
        }
      }
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        availableRouteMetrics(route).forEach { metric ->
          FilterChip(
              selected = metric == selectedMetric,
              onClick = { selectedMetric = metric },
              label = { Text(stringResource(metric.reviewLabelResource())) })
        }
      }
      val range = routeMetricRange(route, selectedMetric)
      val legend =
          if (selectedMetric == RouteMetric.STANDARD) {
            stringResource(AppR.string.route_metric_standard_description)
          } else if (range == null) {
            stringResource(AppR.string.route_metric_unavailable)
          } else {
            stringResource(
                AppR.string.route_legend_range,
                stringResource(selectedMetric.reviewLabelResource()),
                range.minimum,
                range.maximum)
          }
      Text(legend, color = RelvenColors.MutedText, fontSize = 12.sp)
      Box(
          modifier = Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(16.dp))) {
        AndroidView(factory = { mapHost.view }, modifier = Modifier.fillMaxWidth().height(224.dp))
      }
    }
  }
}

@Composable
internal fun ActivityReviewChartCard(route: List<RoutePoint>) {
  val metrics = remember(route) { activityChartMetrics(route) }
  if (metrics.isEmpty()) return
  val locale = LocalLocale.current.platformLocale
  var selectedMetric by remember(route) { mutableStateOf(metrics.first()) }
  LaunchedEffect(metrics) {
    if (selectedMetric !in metrics) selectedMetric = metrics.first()
  }
  val points = remember(route, selectedMetric) { activityChartPoints(route, selectedMetric) }
  val range = remember(route, selectedMetric) { routeMetricRange(route, selectedMetric) }
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(22.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
          stringResource(AppR.string.activity_review_chart),
          color = RelvenColors.Text,
          fontSize = 18.sp,
          fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { metric ->
          FilterChip(
              selected = selectedMetric == metric,
              onClick = { selectedMetric = metric },
              label = { Text(stringResource(metric.reviewLabelResource())) })
        }
      }
      val low = points.minOfOrNull(ActivityChartPoint::value) ?: 0.0
      val high = points.maxOfOrNull(ActivityChartPoint::value) ?: 0.0
      val lowDistance = points.minOfOrNull(ActivityChartPoint::distanceKm) ?: 0.0
      val highDistance = points.maxOfOrNull(ActivityChartPoint::distanceKm) ?: 0.0
      val description =
          stringResource(
              AppR.string.activity_review_chart_description,
              stringResource(selectedMetric.reviewLabelResource()),
              low,
              high)
      Canvas(
          modifier = Modifier.fillMaxWidth().height(164.dp).semantics {
            contentDescription = description
          }) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val ySpan = (high - low).takeIf { it > 0.0 } ?: 1.0
        val xSpan = (highDistance - lowDistance).takeIf { it > 0.0 } ?: 1.0
        val grid = Color(0x33424656)
        repeat(4) { index ->
          val y = top + (bottom - top) * index / 3f
          drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), 1.dp.toPx())
        }
        var path: Path? = null
        var activeSegment: Int? = null
        points.forEach { point ->
          val nextX = left + ((point.distanceKm - lowDistance) / xSpan).toFloat() * (right - left)
          val nextY = bottom - ((point.value - low) / ySpan).toFloat() * (bottom - top)
          if (activeSegment != point.segment || path == null) {
            path?.let { drawPath(it, Color(0xFFFF6A00), style = Stroke(width = 3.dp.toPx())) }
            path = Path().apply { moveTo(nextX, nextY) }
            activeSegment = point.segment
          } else {
            path.lineTo(nextX, nextY)
          }
        }
        path?.let { drawPath(it, Color(0xFFFF6A00), style = Stroke(width = 3.dp.toPx())) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(AppR.string.activity_review_chart_range, low, high),
            color = RelvenColors.MutedText,
            fontSize = 12.sp)
        Text(
            stringResource(
                AppR.string.activity_review_distance_axis,
                activityChartDistanceRangeLabel(lowDistance, highDistance, locale)),
            color = RelvenColors.MutedText,
            fontSize = 12.sp)
      }
    }
  }
}

@Composable
internal fun ActivityReviewSplitsCard(route: List<RoutePoint>) {
  val splits = remember(route) { kilometerSplits(route) }
  val locale = LocalLocale.current.platformLocale
  if (splits.isEmpty()) return
  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(22.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
          stringResource(AppR.string.activity_review_kilometer_splits),
          color = RelvenColors.Text,
          fontSize = 18.sp,
          fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
      splits.forEach { split ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
              stringResource(AppR.string.activity_review_kilometer_number, split.number),
              color = RelvenColors.Text)
          val minutes = (split.paceSecondsPerKm / 60.0).toInt()
          val seconds = (split.paceSecondsPerKm % 60.0).toInt()
          Text(
              String.format(locale, "%d:%02d min/km", minutes, seconds),
              color = RelvenColors.MutedText)
          Text(
              String.format(locale, "%d:%02d", split.durationSeconds.toInt() / 60, split.durationSeconds.toInt() % 60),
              color = RelvenColors.Text)
        }
      }
    }
  }
}

private fun availableRouteMetrics(route: List<RoutePoint>): List<RouteMetric> =
    listOf(
            RouteMetric.STANDARD,
            RouteMetric.PACE,
            RouteMetric.HEART_RATE,
            RouteMetric.CADENCE,
            RouteMetric.SPEED,
            RouteMetric.ELEVATION)
        .filter { it == RouteMetric.STANDARD || it.isAvailable(route) }

private fun RouteMetric.reviewLabelResource(): Int =
    when (this) {
      RouteMetric.STANDARD -> AppR.string.route_metric_standard
      RouteMetric.PACE -> AppR.string.route_metric_pace
      RouteMetric.HEART_RATE -> AppR.string.route_metric_heart_rate
      RouteMetric.CADENCE -> AppR.string.route_metric_cadence
      RouteMetric.SPEED -> AppR.string.route_metric_speed
      RouteMetric.ELEVATION -> AppR.string.route_metric_elevation
    }
