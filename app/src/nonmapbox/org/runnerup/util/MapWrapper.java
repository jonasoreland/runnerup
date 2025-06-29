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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final Context context;
  private final SQLiteDatabase mDB;
  private final long mID;
  private final Formatter formatter;
  private final MapView mapView;

  private static final String OSMDROID_USER_AGENT = "org.runnerup.free";

  public MapWrapper(
      Context context, SQLiteDatabase mDB, long mID, Formatter formatter, Object mapView) {
    this.context = context;
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

    IMapController iMapController = mapView.getController();
    iMapController.setZoom(15.);

    new LoadRoute().execute(new LoadParam(context, mDB, mID, mapView, iMapController));
  }

  class Route {
    Route(Context context, MapView mapView) {
      this.context = context;
      this.mapView = mapView;
      this.map = new Polyline(mapView, true);
    }

    final Context context;
    final MapView mapView;
    final Polyline map;
    List<Marker> markers = new ArrayList<>(2);
  }

  private class LoadParam {
    LoadParam(
        Context context,
        SQLiteDatabase mDB,
        long mID,
        MapView mapView,
        IMapController iMapController) {
      this.context = context;
      this.mDB = mDB;
      this.mID = mID;
      this.mapView = mapView;
      this.iMapController = iMapController;
    }

    final Context context;
    final SQLiteDatabase mDB;
    final long mID;
    final MapView mapView;
    final IMapController iMapController;
  }

  @SuppressLint("StaticFieldLeak")
  private class LoadRoute extends AsyncTask<LoadParam, Void, Route> {
    @Override
    protected Route doInBackground(LoadParam... params) {
      Route route = new Route(params[0].context, params[0].mapView);
      SQLiteDatabase mDB = params[0].mDB;
      long mID = params[0].mID;
      IMapController iMapController = params[0].iMapController;

      route.map.setInfoWindow(null);
      route.map.getOutlinePaint().setStrokeWidth(10.f);
      LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
      List<GeoPoint> points = new LinkedList<>();
      int lastLap = -1;
      for (LocationEntity loc : ll) {
        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        points.add(point);

        int lap = loc.getLap();
        if (lastLap != lap) {
          Marker marker = new Marker(route.mapView);
          marker.setPosition(point);
          String info =
                  "#" + loc.getLap() + " "
                          + formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue())
                          + " "
                          + formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
          lastLap = lap;
          marker.setTextIcon(info);
          marker.setInfoWindow(null);
          marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
          route.markers.add(marker);
        }
      }
      ll.close();
      route.map.setPoints(points);

      if (!points.isEmpty()) {
        iMapController.setCenter(points.get(0));
      }

      return route;
    }

    @Override
    protected void onPostExecute(Route route) {

      if (route != null && route.map != null && route.markers != null) {
        for (Marker marker : route.markers) {
          route.mapView.getOverlays().add(marker);
        }

        route.mapView.getOverlays().add(route.map);
        //Log.v(getClass().getName(), "Added " + route.markers.size() + " markers");
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

  public void onDestroy() {}
}
