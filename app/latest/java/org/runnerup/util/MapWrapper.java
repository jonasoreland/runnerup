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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.mapbox.mapboxsdk.MapboxAccountManager;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
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
import java.util.Iterator;
import java.util.List;

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

    public static void start(Context context, String accessToken) {
        //Must be called before setContentView() in 4,1.1
        MapboxAccountManager.start(context, accessToken);
    }

    public void onCreate(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
                map.setStyleUrl("mapbox://styles/mapbox/outdoors-v9");

                new LoadRoute().execute();
            }
        });
    }

    public void onResume() {
        mapView.onResume();
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
        final ArrayList<MarkerViewOptions > markers = new ArrayList<>(10);
    }

    private class LoadRoute extends AsyncTask<Void, Void, Route> {
        final String[] from = new String[]{
                DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE,
                DB.LOCATION.TYPE,
                DB.LOCATION.TIME,
                DB.LOCATION.LAP,
                DB.LOCATION.HR
        };

        @Override
        protected Route doInBackground(Void... voids) {

            int cnt = 0;
            Route route = null;
            Cursor c = mDB.query(DB.LOCATION.TABLE, from, "activity_id == " + mID,
                    null, null, null, "_id", null);
            if (c.moveToFirst()) {
                route = new Route();
                double acc_distance = 0;
                double tot_distance = 0;
                int cnt_distance = 0;
                LatLng lastLocation = null;
                long lastTime = 0;
                int lastLap = -1;
                do {
                    cnt++;
                    LocationEntity loc = new LocationEntity(c);
                    LatLng point = new LatLng(loc.getLatitude(), loc.getLongitude());
                    route.path.add(point);
                    int type = loc.getType();
                    long time = loc.getTime();
                    int lap = loc.getLap();
                    MarkerViewOptions m;
                    switch (type) {
                        case DB.LOCATION.TYPE_START:
                        case DB.LOCATION.TYPE_END:
                        case DB.LOCATION.TYPE_PAUSE:
                        case DB.LOCATION.TYPE_RESUME:
                            if (type == DB.LOCATION.TYPE_PAUSE) {
                                if (lap == lastLap && lastTime != 0 && lastLocation != null) {
                                    float res[] = {
                                            0
                                    };
                                    Location.distanceBetween(lastLocation.getLatitude(),
                                            lastLocation.getLongitude(), point.getLatitude(),
                                            point.getLongitude(), res);
                                }
                                lastLap = lap;
                                lastTime = 0;
                            } else if (type == DB.LOCATION.TYPE_RESUME) {
                                lastLap = lap;
                                lastTime = time;
                            }
                            lastLocation = point;

                            String title = null;
                            int color = Color.WHITE;
                            switch (type) {
                                case DB.LOCATION.TYPE_START:
                                    color = Color.GREEN;
                                    title = context.getResources().getString(R.string.Start);
                                    break;
                                case DB.LOCATION.TYPE_END:
                                    color = Color.RED;
                                    title = context.getResources().getString(R.string.Stop);
                                    break;
                                case DB.LOCATION.TYPE_PAUSE:
                                    color = Color.CYAN;
                                    title = context.getResources().getString(R.string.Pause);
                                    break;
                                case DB.LOCATION.TYPE_RESUME:
                                    color = Color.BLUE;
                                    title = context.getResources().getString(R.string.Resume);
                                    break;
                            }
                            //TODO Set icon color, probably using static icons
                            m = new MarkerViewOptions().title(title).position(point);
                            route.markers.add(m);
                            break;
                        case DB.LOCATION.TYPE_GPS:
                            float res[] = {
                                    0
                            };
                            if (lastLocation == null) {
                                lastLocation = point;
                            }
                            Location.distanceBetween(lastLocation.getLatitude(),
                                    lastLocation.getLongitude(), point.getLatitude(), point.getLongitude(),
                                    res);
                            acc_distance += res[0];
                            tot_distance += res[0];

                            lastLap = lap;
                            lastTime = time;

                            if (acc_distance >= formatter.getUnitMeters()) {
                                cnt_distance++;
                                acc_distance = 0;
                                m = new MarkerViewOptions()
                                        .title("" + cnt_distance + " " + formatter.getUnitString())
                                        .position(point);
                                //TODO Set icon color, probably using static icons
                                route.markers.add(m);
                            }
                            lastLocation = point;
                            break;
                    }
                } while (c.moveToNext());

                // only keep 10 distance points to not overload the map with markers
                if (tot_distance > 1 + 10 * formatter.getUnitMeters()) {
                    double step = tot_distance / (10 * formatter.getUnitMeters());
                    double current = 0;
                    for (Iterator<MarkerViewOptions> iterator = route.markers.iterator(); iterator.hasNext(); ) {
                        MarkerViewOptions m = iterator.next();
                        if (context.getString(R.string.Distance_marker).equals(m.toString())) {
                            current++;
                            if (current >= step) {
                                current -= step;
                            } else {
                                iterator.remove();
                            }
                        }
                    }
                    Log.i(getClass().getName(), "Too big activity, keeping only 10 of " + (int) (tot_distance / formatter.getUnitMeters()) + " distance markers");
                }
                Log.e(getClass().getName(), "Finished loading " + cnt + " points");
            }
            c.close();
            return route;
        }

        @Override
        protected void onPostExecute(Route route) {

            if (route != null) {
                if (map != null) {
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.FROYO) {
                        LatLng[] pointsArray = new LatLng[route.path.size()];
                        route.path.toArray(pointsArray);
                        map.addPolyline(new PolylineOptions()
                                .add(pointsArray)
                                .color(Color.RED)
                                .width(3));
                        Log.e(getClass().getName(), "Added polyline");
                        int cnt = 0;
                        for (MarkerViewOptions m : route.markers) {
                            cnt++;
                            map.addMarker(m);
                        }
                        Log.e(getClass().getName(), "Added " + cnt + " markers");

                        //There seem to be no way to get bounds from polyline or LatLong[]...
                        LatLng n = null, s = null, w = null, e = null;
                        for (LatLng l : route.path) {
                            if(n==null || l.getLatitude() > n.getLatitude())
                                n=l;
                            if(s==null || l.getLatitude() < s.getLatitude())
                                s=l;
                            if(w==null || l.getLongitude() < w.getLongitude())
                                w=l;
                            if(e==null || l.getLongitude() > e.getLongitude())
                                e=l;
                        }
                        //zoom on map
                        @SuppressWarnings("ConstantConditions") final LatLngBounds box = new LatLngBounds.Builder().include(n).include(s).include(w).include(e).build();
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(box, 50));
                    }
                    route = null; // release mem for old...
                }
            }
        }
    }
}
