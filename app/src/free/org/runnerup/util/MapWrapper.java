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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;

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

import java.util.LinkedList;
import java.util.List;

public class MapWrapper implements Constants {

    private final Context context;
    private final SQLiteDatabase mDB;
    private final long mID;
    private final Formatter formatter;
    private final MapView mapView;

    private static final String OSMDROID_USER_AGENT = "org.runnerup.free";

    public MapWrapper(Context context, SQLiteDatabase mDB, long mID, Formatter formatter, Object mapView) {
        this.context = context;
        this.mDB = mDB;
        this.mID = mID;
        this.formatter = formatter;
        this.mapView = (MapView)mapView;
    }

    public static void start(Context context) {
    }

    public void onCreate(Bundle savedInstanceState) {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        mapView.setMultiTouchControls(true);

        Configuration.getInstance().setUserAgentValue(OSMDROID_USER_AGENT);

        IMapController iMapController = mapView.getController();
        iMapController.setZoom(15.);

        new LoadRoute().execute(new LoadParam(context, mDB, mID, mapView, iMapController));
    }

    private class LoadParam {
        LoadParam(Context context, SQLiteDatabase mDB, long mID, MapView mapView, IMapController iMapController) {
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
    private class LoadRoute extends AsyncTask<LoadParam, Void, Polyline> {
        @Override
        protected Polyline doInBackground(LoadParam... params) {
            MapView mapView = params[0].mapView;
            SQLiteDatabase mDB = params[0].mDB;
            long mID = params[0].mID;
            IMapController iMapController = params[0].iMapController;
            Polyline route = new Polyline(mapView, true);
            route.setInfoWindow(null);
            route.getOutlinePaint().setStrokeWidth(10.f);
            LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(mDB, mID);
            List<GeoPoint> points = new LinkedList<>();
            for (LocationEntity loc : ll) {
                GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                points.add(point);
            }
            ll.close();
            route.setPoints(points);
            Marker markerStart = new Marker(mapView);
            markerStart.setInfoWindow(null);
            Marker markerEnd = new Marker(mapView);
            markerEnd.setInfoWindow(null);
            if (points.size() > 0) {
                iMapController.setCenter(points.get(0));

                markerStart.setPosition(points.get(0));
                markerEnd.setPosition(points.get(points.size() - 1));

                markerStart.setTextIcon(context.getString(R.string.Start));
                markerEnd.setTextIcon(context.getString(R.string.Finished));

                markerStart.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                markerEnd.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                mapView.getOverlays().add(markerStart);
                mapView.getOverlays().add(markerEnd);

                mapView.getOverlays().add(route);
            }

            return route;
        }
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onStart() {
    }

    public void onStop() {
    }

    public void onPause() {
        mapView.onPause();
    }

    public void onSaveInstanceState(Bundle outState) {
    }

    public void onLowMemory() {
    }

    public void onDestroy() {
    }
}
