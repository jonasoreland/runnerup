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

import static org.runnerup.util.Formatter.Format.TXT_LONG;
import static org.runnerup.util.Formatter.Format.TXT_SHORT;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import com.mapbox.common.MapboxOptions;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions;
import com.mapbox.maps.plugin.locationcomponent.utils.BitmapUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final MapView mapView;
  private final long mID;
  private final SQLiteDatabase mDB;
  private final Context context;
  private final Formatter formatter;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public MapWrapper(
      Context context,
      SQLiteDatabase mDB,
      long mID,
      Formatter formatter,
      java.lang.Object mapView) {
    this.context = context;
    this.mDB = mDB;
    this.mID = mID;
    this.formatter = formatter;
    this.mapView = (MapView) mapView;
  }

  public static void start(Context context) {
    MapboxOptions.setAccessToken(BuildConfig.MAPBOX_ACCESS_TOKEN);
  }

  public void onCreate(Bundle savedInstanceState) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Resources res = context.getResources();
    String val =
        prefs.getString(
            res.getString(R.string.pref_mapbox_default_style),
            res.getString(R.string.mapboxDefaultStyle));
    mapView
        .getMapboxMap()
        .loadStyle(
            val,
            mapStyle -> {
              loadRouteAsync(mapView);
            });
  }

  public void onResume() {}

  public void onStart() {}

  public void onStop() {}

  public void onPause() {}

  public void onSaveInstanceState(Bundle outState) {}

  public void onLowMemory() {}

  public void onDestroy() {
    executor.shutdown();
  }

  /**
   * Kicks off the process of loading route data on a background thread and then updating the UI.
   *
   * @param mapView The map instance to update once data is loaded.
   */
  private void loadRouteAsync(final MapView mapView) {
    executor.execute(
        () -> {
          // DB queries, data processing on the background thread
          final Route route = loadRouteDataInBackground();

          // update the UI on the main thread
          mapView.post(() -> onRouteLoaded(route, mapView));
        });
  }

  /**
   * This method runs on a background thread. It queries the database and processes the location
   * data into a format suitable for the map, without touching any UI components.
   *
   * @return A {@link Route} object containing the processed path and markers.
   */
  private Route loadRouteDataInBackground() {
    Route route = new Route();
    LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
    int lastLap = 0;
    for (LocationEntity loc : ll) {
      Point point = Point.fromLngLat(loc.getLongitude(), loc.getLatitude());
      route.path.add(point);

      java.lang.Integer type;
      // Start/end markers are not set in db, special handling
      if (route.markers.isEmpty()) {
        type = DB.LOCATION.TYPE_START;
      } else {
        type = loc.getType();
      }

      java.lang.String iconImage;
      if (type != DB.LOCATION.TYPE_START && lastLap != loc.getLap()) {
        lastLap = loc.getLap();
        iconImage = "lap";
      } else if (type == DB.LOCATION.TYPE_START
          || type == DB.LOCATION.TYPE_END
          || type == DB.LOCATION.TYPE_PAUSE
          || type == DB.LOCATION.TYPE_RESUME) {
        iconImage = type.toString();
      } else {
        iconImage = null;
      }

      if (iconImage != null) {
        String pointInfo;
        String popupInfo;
        pointInfo = (iconImage.equals("lap") ? "#" + loc.getLap() + "\n" : "");
        popupInfo =
            pointInfo
                + (formatter.formatDistance(TXT_LONG, loc.getDistance().longValue()) + "\n")
                + (formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0))
                    + "\n")
                + (formatter.formatDateTime(loc.getTime() / 1000) + "\n")
                + (loc.getSpeed() != null
                    ? formatter.formatPaceSpeed(TXT_LONG, loc.getSpeed()) + "\n"
                    : "")
                + (loc.getAltitude() != null
                    ? formatter.formatElevation(TXT_LONG, loc.getAltitude()) + "\n"
                    : "")
                + (loc.getHr() != null
                    ? formatter.formatHeartRate(TXT_LONG, loc.getHr()) + "\n"
                    : "");

        route.markers.add(new RouteMarker(point, iconImage, pointInfo, popupInfo));
      }
    }
    ll.close();

    // Track is normally ended with a pause, not always followed by an end
    // Ignore the pause
    if (route.markers.size() >= 2) {
      RouteMarker m = route.markers.get(route.markers.size() - 2);
      RouteMarker me = route.markers.get(route.markers.size() - 1);
      if (m.image.equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())
          && me.image.equals(((Integer) DB.LOCATION.TYPE_END).toString())) {
        route.markers.remove(route.markers.size() - 2);
      }
    }

    if (!route.markers.isEmpty()) {
      RouteMarker me = route.markers.get(route.markers.size() - 1);
      if (me.image.equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())) {
        me.image = (((Integer) DB.LOCATION.TYPE_END).toString());
      }
    }

    return route;
  }

  /**
   * This method runs on the main UI thread. It takes the processed route data and applies it to the
   * map, drawing the polyline and markers.
   *
   * @param route The processed route data.
   * @param mapView The map instance to update.
   */
  private void onRouteLoaded(Route route, MapView mapView) {
    AnnotationPlugin annotationApi = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
    if (route == null
        || annotationApi == null
        || route.path.size() <= 1
        || route.markers.isEmpty()) {
      return;
    }

    // Add layers bottom to top
    AnnotationConfig configLine = new AnnotationConfig(null, null, "bottom");
    PolylineAnnotationManager polylineAnnotationManager =
        PolylineAnnotationManagerKt.createPolylineAnnotationManager(annotationApi, configLine);

    AnnotationConfig configMarker = new AnnotationConfig(null, null, "top");
    PointAnnotationManager pointAnnotationManager =
        PointAnnotationManagerKt.createPointAnnotationManager(annotationApi, configMarker);

    PolylineAnnotationOptions options =
        new PolylineAnnotationOptions()
            .withPoints(route.path)
            .withLineColor(Color.RED)
            .withLineWidth(3.0f);
    polylineAnnotationManager.create(options);
    Log.v(getClass().getName(), "Added line");

    // The view must adjust to the route path bounds after the view is first loaded
    // subscribeMapLoaded could be used too, but the async version of cameraForCoordinates() is
    // still needed
    CameraOptions camera = new CameraOptions.Builder().build();
    com.mapbox.maps.EdgeInsets padding = new com.mapbox.maps.EdgeInsets(100.0, 50.0, 50.0, 50.0);
    mapView
        .getMapboxMap()
        .cameraForCoordinates(
            route.path,
            camera,
            padding,
            null,
            null,
            cameraOptions -> {
              mapView.getMapboxMap().setCamera(cameraOptions);
              Log.d(getClass().getName(), "Camera zoomed to route bounds after map idle event.");
              return null;
            });

    // Images id from text
    Objects.requireNonNull(mapView.getMapboxMap().getStyle())
        .addImage(
            ((Integer) DB.LOCATION.TYPE_START).toString(),
            Objects.requireNonNull(
                BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                    context, R.drawable.ic_map_marker_start)),
            false);
    mapView
        .getMapboxMap()
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_END).toString(),
            Objects.requireNonNull(
                BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                    context, R.drawable.ic_map_marker_end)),
            false);
    mapView
        .getMapboxMap()
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_PAUSE).toString(),
            Objects.requireNonNull(
                BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                    context, R.drawable.ic_map_marker_pause)),
            false);
    mapView
        .getMapboxMap()
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_RESUME).toString(),
            Objects.requireNonNull(
                BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                    context, R.drawable.ic_map_marker_resume)),
            false);
    mapView
        .getMapboxMap()
        .getStyle()
        .addImage(
            "lap",
            Objects.requireNonNull(
                BitmapUtils.INSTANCE.getBitmapFromDrawableRes(
                    context, R.drawable.ic_map_marker_lap)),
            false);

    List<PointAnnotationOptions> points = new ArrayList<>(route.markers.size());
    for (RouteMarker m : route.markers) {
      PointAnnotationOptions o =
          new PointAnnotationOptions()
              .withPoint(m.point)
              .withIconImage(m.image)
              .withIconAnchor(IconAnchor.BOTTOM)
              .withTextField(m.info)
              .withTextOffset(
                  new ArrayList<>() {
                    {
                      add(0.0);
                      add(1.0);
                    }
                  })
              .withTextAnchor(TextAnchor.BOTTOM);
      points.add(o);
    }
    pointAnnotationManager.create(points);

    pointAnnotationManager.addClickListener(
        pointAnnotation -> {
          // Find the original marker data, ignore overlapping
          for (RouteMarker m : route.markers) {
            if (m.point.equals(pointAnnotation.getPoint())) {
              // Show a dialog with its info.
              new AlertDialog.Builder(context)
                  .setMessage(m.popupInfo)
                  .setPositiveButton(android.R.string.ok, null)
                  .show();
              break;
            }
          }
          return true;
        });
  }

  /** RouteMarker (start, pause, end, lap) */
  static class RouteMarker {
    Point point;
    String image;
    String info;
    String popupInfo;

    public RouteMarker(Point point, String iconImage, String pointInfo, String popupInfo) {
      this.point = point;
      this.image = iconImage;
      this.info = pointInfo;
      this.popupInfo = popupInfo;
    }
  }

  /** Class to hold the processed route data from the background task. */
  static class Route {
    final java.util.List<Point> path = new ArrayList<>(10);
    final java.util.ArrayList<RouteMarker> markers = new ArrayList<>(10);
  }
}
