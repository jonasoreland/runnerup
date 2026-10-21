/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.util;

import static org.runnerup.util.Formatter.Format.TXT_SHORT;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final SQLiteDatabase mDB;
  private final long mID;
  private final Formatter formatter;
  private final MapView mapView;
  private final Context context;
  private List<Sample> samples = new ArrayList<>();
  private final ColoredRouteOverlay routeOverlay = new ColoredRouteOverlay();
  private TextView legend;
  private Metric selectedMetric = Metric.PACE;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private static final java.lang.String OSMDROID_USER_AGENT = "org.runnerup.free";
  private static final int[] ROUTE_COLORS = {
    0xff2e7d32, 0xff9eaa24, 0xffffc107, 0xfff57c00, 0xffc62828
  };

  private enum Metric {
    PACE,
    HEART_RATE,
    SPEED,
    ELEVATION
  }

  record Sample(
      GeoPoint point, int lap, Double pace, Double heartRate, Double speed, Double elevation) {}

  public MapWrapper(
      Context context,
      SQLiteDatabase mDB,
      long mID,
      Formatter formatter,
      java.lang.Object mapView) {
    this.mDB = mDB;
    this.mID = mID;
    this.formatter = formatter;
    this.mapView = (MapView) mapView;
    this.context = context;
  }

  public static void start(Context context) {}

  public void onCreate(Bundle savedInstanceState) {
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView
        .getZoomController()
        .setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
    mapView.setMultiTouchControls(true);
    addMetricControls();

    Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);

    loadRouteAsync();
  }

  record Route(List<Marker> markers, List<Sample> samples, GeoPoint firstPoint) {}

  private void addMetricControls() {
    View tab = (View) mapView.getParent();
    Spinner selector = new Spinner(context);
    selector.setPrompt(context.getString(R.string.route_color_by));
    selector.setAdapter(
        new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {
              context.getString(R.string.route_metric_pace),
              context.getString(R.string.route_metric_heart_rate),
              context.getString(R.string.route_metric_speed),
              context.getString(R.string.route_metric_elevation)
            }));
    legend = new TextView(context);
    legend.setPadding(16, 8, 16, 8);
    if (tab instanceof android.widget.LinearLayout parent) {
      parent.setOrientation(android.widget.LinearLayout.VERTICAL);
      parent.addView(selector, 0, new android.widget.LinearLayout.LayoutParams(-1, -2));
      parent.addView(legend, 1, new android.widget.LinearLayout.LayoutParams(-1, -2));
      mapView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
    }
    selector.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View view, int position, long id) {
            selectedMetric = Metric.values()[position];
            selector.setContentDescription(
                context.getString(
                    R.string.route_selector_description,
                    context.getString(R.string.route_color_by),
                    parent.getItemAtPosition(position)));
            renderRoute();
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
  }

  /**
   * Loads the route from the database on a background thread and then updates the UI on the main
   * thread.
   */
  private void loadRouteAsync() {
    executor.execute(
        () -> {
          final Route route = loadRouteData();

          // UI update
          mapView.post(
              () -> {
                samples = route.samples;
                IMapController mapController = mapView.getController();
                mapController.setZoom(15.);

                if (route.firstPoint != null) {
                  mapController.setCenter(route.firstPoint);
                }

                // Add markers to the map; metric-colored route segments are rendered separately.
                for (Marker marker : route.markers) {
                  mapView.getOverlays().add(marker);
                }
                renderRoute();
                mapView.invalidate();
              });
        });
  }

  /** The long-running database query and data processing logic. */
  private Route loadRouteData() {
    java.util.List<Marker> markers = new ArrayList<>();
    List<Sample> routeSamples = new ArrayList<>();

    LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
    int lastLap = -1;
    LocationEntity previous = null;
    for (LocationEntity loc : ll) {
      GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
      Double distanceDelta = previous == null ? null : loc.getDistance() - previous.getDistance();
      Double elapsedDelta =
          previous == null ? null : (loc.getElapsed() - previous.getElapsed()) / 1000.0;
      Double pace =
          distanceDelta != null && elapsedDelta != null && distanceDelta > 0 && elapsedDelta > 0
              ? elapsedDelta / distanceDelta
              : null;
      routeSamples.add(
          new Sample(
              point,
              loc.getLap(),
              pace,
              loc.getHr() == null ? null : loc.getHr().doubleValue(),
              loc.getSpeed(),
              loc.getAltitude()));

      int lap = loc.getLap();
      if (lastLap != lap) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        java.lang.String info =
            "#"
                + loc.getLap()
                + " "
                + formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue())
                + " "
                + formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
        lastLap = lap;
        marker.setTextIcon(info);
        marker.setInfoWindow(null);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        markers.add(marker);
      }
      previous = loc;
    }
    ll.close();
    return new Route(
        markers, routeSamples, routeSamples.isEmpty() ? null : routeSamples.get(0).point());
  }

  private void renderRoute() {
    if (!mapView.getOverlays().contains(routeOverlay)) mapView.getOverlays().add(routeOverlay);
    List<Double> values = metricValues();
    if (!RouteMetricColoring.isAvailable(values)) {
      if (legend != null) legend.setText(context.getString(R.string.route_metric_unavailable));
      if (legend != null) legend.setCompoundDrawables(null, null, null, null);
      routeOverlay.setMetric(selectedMetric, 0, 0, false);
      mapView.invalidate();
      return;
    }
    double min =
        values.stream()
            .filter(v -> v != null && Double.isFinite(v))
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0);
    double max =
        values.stream()
            .filter(v -> v != null && Double.isFinite(v))
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0);
    if (legend != null) {
      legend.setText(context.getString(R.string.route_legend_range, metricLabel(), min, max));
      int width = Math.round(72 * context.getResources().getDisplayMetrics().density);
      int height = Math.round(8 * context.getResources().getDisplayMetrics().density);
      GradientDrawable gradient =
          new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, ROUTE_COLORS);
      gradient.setSize(width, height);
      gradient.setBounds(0, 0, width, height);
      legend.setCompoundDrawablePadding(
          Math.round(10 * context.getResources().getDisplayMetrics().density));
      legend.setCompoundDrawables(gradient, null, null, null);
      legend.setContentDescription(
          context.getString(
              R.string.route_legend_content_description,
              metricLabel(),
              min,
              max,
              context.getString(R.string.route_legend_low_to_high)));
    }
    routeOverlay.setMetric(selectedMetric, min, max, true);
    mapView.invalidate();
  }

  private List<Double> metricValues() {
    List<Double> values = new ArrayList<>();
    for (Sample sample : samples) values.add(metricValue(sample));
    return values;
  }

  private Double metricValue(Sample sample) {
    return switch (selectedMetric) {
      case PACE -> sample.pace() == null ? null : sample.pace() * (1000.0 / 60.0);
      case HEART_RATE -> sample.heartRate();
      case SPEED -> sample.speed() == null ? null : sample.speed() * 3.6;
      case ELEVATION -> sample.elevation();
    };
  }

  private String metricLabel() {
    return switch (selectedMetric) {
      case PACE -> context.getString(R.string.route_metric_pace);
      case HEART_RATE -> context.getString(R.string.route_metric_heart_rate);
      case SPEED -> context.getString(R.string.route_metric_speed);
      case ELEVATION -> context.getString(R.string.route_metric_elevation);
    };
  }

  private final class ColoredRouteOverlay extends Overlay {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Point start = new Point();
    private final Point end = new Point();
    private Metric metric = Metric.PACE;
    private double min;
    private double max;
    private boolean colored;

    ColoredRouteOverlay() {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(10.f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setMetric(Metric metric, double min, double max, boolean colored) {
      this.metric = metric;
      this.min = min;
      this.max = max;
      this.colored = colored;
    }

    @Override
    public void draw(Canvas canvas, Projection projection) {
      for (int i = 1; i < samples.size(); i++) {
        Sample before = samples.get(i - 1), after = samples.get(i);
        if (before.lap() != after.lap()) continue;
        projection.toPixels(before.point(), start);
        projection.toPixels(after.point(), end);
        Double value = metricValue(after);
        paint.setColor(
            !colored || value == null || !Double.isFinite(value)
                ? Color.GRAY
                : ROUTE_COLORS[RouteMetricColoring.colorIndex(value, min, max)]);
        canvas.drawLine(start.x, start.y, end.x, end.y, paint);
      }
    }
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onStart() {}

  public void onStop() {}

  public void onPause() {
    mapView.onPause();
  }

  public void onSaveInstanceState(Bundle outState) {}

  public void onLowMemory() {}

  public void onDestroy() {
    onDestroy(() -> {});
  }

  public void onDestroy(Runnable afterBackgroundWork) {
    executor.shutdown();
    Thread waiter =
        new Thread(
            () -> {
              boolean interrupted = false;
              try {
                while (true) {
                  try {
                    if (executor.awaitTermination(1, TimeUnit.DAYS)) break;
                  } catch (InterruptedException e) {
                    interrupted = true;
                  }
                }
              } finally {
                if (interrupted) Thread.currentThread().interrupt();
                afterBackgroundWork.run();
              }
            },
            "Relven-map-shutdown");
    waiter.setDaemon(true);
    waiter.start();
  }
}
