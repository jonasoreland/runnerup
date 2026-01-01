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
import android.os.Bundle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final SQLiteDatabase mDB;
  private final long mID;
  private final Formatter formatter;
  private final MapView mapView;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private static final java.lang.String OSMDROID_USER_AGENT = "org.runnerup.free";

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
  }

  public static void start(Context context) {}

  public void onCreate(Bundle savedInstanceState) {
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView
        .getZoomController()
        .setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
    mapView.setMultiTouchControls(true);

    Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);

    loadRouteAsync();
  }

  // The results from the database query
  record Route(Polyline map, List<Marker> markers, GeoPoint firstPoint) {}

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
                IMapController mapController = mapView.getController();
                mapController.setZoom(15.);

                if (route.firstPoint != null) {
                  mapController.setCenter(route.firstPoint);
                }

                // Add markers and the polyline to the map
                for (Marker marker : route.markers) {
                  mapView.getOverlays().add(marker);
                }
                mapView.getOverlays().add(route.map);
                mapView.invalidate();
              });
        });
  }

  /** The long-running database query and data processing logic. */
  private Route loadRouteData() {
    Polyline polyline = new Polyline(mapView, true);
    polyline.setInfoWindow(null);
    polyline.getOutlinePaint().setStrokeWidth(10.f);

    java.util.List<Marker> markers = new ArrayList<>();
    java.util.List<GeoPoint> points = new LinkedList<>();

    LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
    int lastLap = -1;
    for (LocationEntity loc : ll) {
      GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
      points.add(point);

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
    }
    ll.close();
    polyline.setPoints(points);

    GeoPoint firstPoint = points.isEmpty() ? null : points.get(0);
    return new Route(polyline, markers, firstPoint);
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
    executor.shutdown();
  }
}
