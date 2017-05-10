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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewTreeObserver;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;

import java.util.ArrayList;
import java.util.List;

import static org.runnerup.util.Formatter.TXT_SHORT;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class MapWrapper implements Constants {

    private MapView mapView = null;
    private MapboxMap map;

    private long mID = 0;
    private SQLiteDatabase mDB = null;
    private final Context context;
    private Formatter formatter = null;

    public MapWrapper(Context context, SQLiteDatabase mDB, long mID, Formatter formatter, MapView mapView) {
        this.context = context;
        this.mDB = mDB;
        this.mID = mID;
        this.formatter = formatter;
        this.mapView = mapView;
    }

    public static void start(Context context) {
        Mapbox.getInstance(context, context.getString(R.string.mapboxAccessToken));
    }

    private void setStyle() {
        if (map != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Resources res = context.getResources();
            String val = prefs.getString(res.getString(R.string.pref_mapbox_default_style), "");

            if (TextUtils.isEmpty(val)) {
                //The preferences should prevent from setting an empty value for display reasons
                //However, that handling is not so easy
                //(MapBox seem to handle no style set OK though).
                val = res.getString(R.string.mapboxDefaultStyle);
            }
            map.setStyleUrl(val);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
                setStyle();
                new LoadRoute().execute(new LoadParam(context, mDB, mID));
            }
        });
    }

    public void onResume() {
        setStyle();
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
        mapView.onDestroy();
    }

    class Route {
        final List<LatLng> path = new ArrayList<>(10);
        final ArrayList<MarkerViewOptions> markers = new ArrayList<>(10);
    }

    private class LoadParam {
        LoadParam(Context context, SQLiteDatabase mDB, long mID) {
            this.context = context;
            this.mDB = mDB;
            this.mID = mID;
        }

        final Context context;
        final SQLiteDatabase mDB;
        final long mID;
    }

    private class LoadRoute extends AsyncTask<LoadParam, Void, Route> {
        @Override
        protected Route doInBackground(LoadParam... params) {

            Route route = new Route();
            LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(params[0].mDB, params[0].mID);
            IconFactory iconFactory = IconFactory.getInstance(params[0].context);

            int lastLap = 0;
            for (LocationEntity loc : ll) {
                LatLng point = new LatLng(loc.getLatitude(), loc.getLongitude());
                route.path.add(point);
                int type = loc.getType();
                MarkerViewOptions m;
                String title = "";
                Integer iconId = null;
                //Start/end markers are not set in db, special handling
                if (route.markers.isEmpty()) {
                    type = DB.LOCATION.TYPE_START;
                }
                switch (type) {
                    case DB.LOCATION.TYPE_START:
                        iconId = R.drawable.ic_map_marker_start;
                        title = context.getResources().getString(R.string.Start);
                        break;
                    case DB.LOCATION.TYPE_END:
                        iconId = R.drawable.ic_map_marker_end;
                        title = context.getResources().getString(R.string.Stop);
                        break;
                    case DB.LOCATION.TYPE_PAUSE:
                        iconId = R.drawable.ic_map_marker_pause;
                        title = context.getResources().getString(R.string.Pause);
                        break;
                    case DB.LOCATION.TYPE_RESUME:
                        iconId = R.drawable.ic_map_marker_resume;
                        title = context.getResources().getString(R.string.Resume);
                        break;
                    case DB.LOCATION.TYPE_GPS:
                        break;
                }

                if (lastLap != loc.getLap()) {
                    if (lastLap >= 0) {
                        title = context.getString(R.string.cue_lap) + " " + loc.getLap();
                    }
                    iconId = R.drawable.ic_map_marker_lap;
                    lastLap = loc.getLap();
                }
                if (iconId != null) {
                    String snippet = formatter.formatDistance(TXT_SHORT, loc.getDistance().longValue()) + " " +
                            formatter.formatElapsedTime(TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
                    Icon icon = iconFactory.fromBitmap(BitmapFactory.decodeResource(context.getResources(),iconId));
                    m = new MarkerViewOptions().title(title).position(point).snippet(snippet).icon(icon).anchor(0.5f, 86f / 96f);

                    if (type == DB.LOCATION.TYPE_START) {
                        m.snippet(null);
                    }
                    route.markers.add(m);
                }
            }
            ll.close();
            //Track is ended with a pause, replace with end
            if (!route.markers.isEmpty()) {
                MarkerViewOptions m = route.markers.get(route.markers.size() - 1);
                Icon icon = iconFactory.fromBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_map_marker_end));
                m.title(context.getResources().getString(R.string.Stop)).icon(icon);
            }
            return route;
        }

        @Override
        protected void onPostExecute(Route route) {

            if (route != null && map != null &&
                    android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

                if (route.path.size() > 1) {

                    LatLng[] pointsArray = new LatLng[route.path.size()];
                    route.path.toArray(pointsArray);
                    map.addPolyline(new PolylineOptions()
                            .add(pointsArray)
                            .color(Color.RED)
                            .width(3));
                    Log.v(getClass().getName(), "Added polyline");

                    final LatLngBounds box =
                            new LatLngBounds.Builder().includes(route.path).build();
                    final CameraUpdate initialCameraPosition = CameraUpdateFactory.newLatLngBounds(box, 50);
                    map.moveCamera(initialCameraPosition);

                    //Since MapBox 4.2.0-beta.3 moving the camera in onMapReady is not working if map is not visible
                    //The proper solution is a redesign using fragments, see https://github.com/mapbox/mapbox-gl-native/issues/6855#event-841575956
                    //A workaround is to try move the camera at view updates as long as position is 0,0
                    mapView.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @SuppressWarnings("deprecation")
                                // We check which build version we are using.
                                @Override
                                public void onGlobalLayout() {
                                    if (map.getCameraPosition().target.getLatitude() != 0 ||
                                            map.getCameraPosition().target.getLongitude() != 0) {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                                            mapView.getViewTreeObserver()
                                                    .removeGlobalOnLayoutListener(this);
                                        } else {
                                            mapView.getViewTreeObserver()
                                                    .removeOnGlobalLayoutListener(this);
                                        }
                                    } else {
                                        map.moveCamera(initialCameraPosition);
                                    }
                                }
                            }
                    );
                }

                if (route.markers.size() > 0) {
                    for (MarkerViewOptions m : route.markers) {
                        map.addMarker(m);
                    }
                }
                Log.v(getClass().getName(), "Added " + route.markers.size() + " markers");
            }
        }
    }
}