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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.LineManager;
import com.mapbox.mapboxsdk.plugins.annotation.LineOptions;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.utils.BitmapUtils;
import com.mapbox.mapboxsdk.utils.ColorUtils;
import com.mapbox.pluginscalebar.ScaleBarOptions;
import com.mapbox.pluginscalebar.ScaleBarPlugin;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final MapView mapView;
  private LineManager lineManager;
  private SymbolManager symbolManager;

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
    Mapbox.getInstance(context, BuildConfig.MAPBOX_ACCESS_TOKEN);
  }

  public void onCreate(Bundle savedInstanceState) {
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(
        mapboxMap -> {
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
          Resources res = context.getResources();
          String val =
              prefs.getString(
                  res.getString(R.string.pref_mapbox_default_style),
                  res.getString(R.string.mapboxDefaultStyle));

          Style.Builder style = new Style.Builder().fromUri(val);
          mapboxMap.setStyle(
              style,
              mapStyle -> {
                lineManager = new LineManager(mapView, mapboxMap, mapStyle);
                symbolManager = new SymbolManager(mapView, mapboxMap, mapStyle);
                // Map is set up and the style has loaded, now load the route data
                loadRouteAsync(mapboxMap);
              });
        });
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onStart() {
    mapView.onStart();
  }

  public void onStop() {
    mapView.onStop();
  }

  public void onPause() {
    mapView.onPause();
  }

  public void onSaveInstanceState(Bundle outState) {
    mapView.onSaveInstanceState(outState);
  }

  public void onLowMemory() {
    mapView.onLowMemory();
  }

  public void onDestroy() {
    executor.shutdown();

    if (lineManager != null) {
      lineManager.onDestroy();
    }
    if (symbolManager != null) {
      symbolManager.onDestroy();
    }
    mapView.onDestroy();
  }

  /** Class to hold the processed route data from the background task. */
  static class Route {
    final java.util.List<LatLng> path = new ArrayList<>(10);
    final java.util.ArrayList<SymbolOptions> markers = new ArrayList<>(10);
  }

  /**
   * Kicks off the process of loading route data on a background thread and then updating the UI.
   *
   * @param mapboxMap The map instance to update once data is loaded.
   */
  private void loadRouteAsync(final MapboxMap mapboxMap) {
    executor.execute(
        () -> {
          // DB queries, data processing on the background thread
          final Route route = loadRouteDataInBackground();

          // update the UI on the main thread
          mapView.post(() -> onRouteLoaded(route, mapboxMap));
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
      LatLng point = new LatLng(loc.getLatitude(), loc.getLongitude());
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
        // TBD Implement Info popup with the info instead, using the annotaion plugin (currently
        // no examples)
        java.lang.String info;
        if (type == DB.LOCATION.TYPE_START) {
          info = null;
        } else {
          info =
              (iconImage.equals("lap") ? "#" + loc.getLap() + "\n" : "")
                  + formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue())
                  + "\n"
                  + formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
        }

        SymbolOptions m =
            new SymbolOptions()
                .withLatLng(point)
                .withIconImage(iconImage)
                .withIconAnchor(Property.ICON_ANCHOR_BOTTOM)
                .withTextField(info)
                .withTextAnchor(Property.TEXT_ANCHOR_TOP);
        route.markers.add(m);
      }
    }
    ll.close();

    // Track is normally ended with a pause, not always followed by an end
    // Ignore the pause
    if (route.markers.size() >= 2) {
      SymbolOptions m = route.markers.get(route.markers.size() - 2);
      SymbolOptions me = route.markers.get(route.markers.size() - 1);
      if (m.getIconImage().equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())
          && me.getIconImage().equals(((Integer) DB.LOCATION.TYPE_END).toString())) {
        route.markers.remove(route.markers.size() - 2);
      }
    }

    if (!route.markers.isEmpty()) {
      SymbolOptions me = route.markers.get(route.markers.size() - 1);
      if (me.getIconImage().equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())) {
        me.withIconImage(((Integer) DB.LOCATION.TYPE_END).toString());
      }
    }

    return route;
  }

  /**
   * This method runs on the main UI thread. It takes the processed route data and applies it to the
   * map, drawing the polyline and markers.
   *
   * @param route The processed route data.
   * @param mapboxMap The map instance to update.
   */
  private void onRouteLoaded(Route route, MapboxMap mapboxMap) {
    if (route == null) {
      return;
    }

    ScaleBarPlugin scaleBarPlugin = new ScaleBarPlugin(mapView, mapboxMap);
    scaleBarPlugin.create(new ScaleBarOptions(context));

    if (route.path.size() > 1) {
      LineOptions lineOptions =
          new LineOptions()
              .withLatLngs(route.path)
              .withLineColor(ColorUtils.colorToRgbaString(Color.RED))
              .withLineWidth(3.0f);
      lineManager.create(lineOptions);
      Log.v(getClass().getName(), "Added line");

      final LatLngBounds box = new LatLngBounds.Builder().includes(route.path).build();
      final CameraUpdate initialCameraPosition = CameraUpdateFactory.newLatLngBounds(box, 50);
      mapboxMap.moveCamera(initialCameraPosition);

      // Since MapBox 4.2.0-beta.3 moving the camera in onMapReady is not working if map is not
      // visible
      // The proper solution is a redesign using fragments, see
      // https://github.com/mapbox/mapbox-gl-native/issues/6855#event-841575956
      // A workaround is to try move the camera at view updates as long as position is 0,0
      mapView
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(
              new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                  if (mapboxMap.getCameraPosition().target.getLatitude() != 0
                      || mapboxMap.getCameraPosition().target.getLongitude() != 0) {
                    mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                  } else {
                    mapboxMap.moveCamera(initialCameraPosition);
                  }
                }
              });
    }

    // Images id from text
    Objects.requireNonNull(mapboxMap
                    .getStyle())
        .addImage(
            ((Integer) DB.LOCATION.TYPE_START).toString(),
            Objects.requireNonNull(
                BitmapUtils.getBitmapFromDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_start))),
            false);
    mapboxMap
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_END).toString(),
            Objects.requireNonNull(
                BitmapUtils.getBitmapFromDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_end))),
            false);
    mapboxMap
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_PAUSE).toString(),
            Objects.requireNonNull(
                BitmapUtils.getBitmapFromDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_pause))),
            false);
    mapboxMap
        .getStyle()
        .addImage(
            ((Integer) DB.LOCATION.TYPE_RESUME).toString(),
            Objects.requireNonNull(
                BitmapUtils.getBitmapFromDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_resume))),
            false);
    mapboxMap
        .getStyle()
        .addImage(
            "lap",
            Objects.requireNonNull(
                BitmapUtils.getBitmapFromDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_lap))),
            false);

    symbolManager.setIconAllowOverlap(true);
    if (!route.markers.isEmpty()) {
      for (SymbolOptions m : route.markers) {
        symbolManager.create(m);
      }
    }
    Log.v(getClass().getName(), "Added " + route.markers.size() + " markers");
  }
}
