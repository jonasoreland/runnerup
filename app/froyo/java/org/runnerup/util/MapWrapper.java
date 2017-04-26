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
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.mapbox.mapboxsdk.maps.MapView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.util.Formatter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MapWrapper implements Constants {

    public MapWrapper(Context context, SQLiteDatabase mDB, long mID, Formatter formatter, MapView mapView) {
    }

    public static void start(Context context) {
    }

    public void onCreate(Bundle savedInstanceState) {
    }

    public void onStart() {
    }

    public void onStop() {
    }

    public void onResume() {
    }

    public void onPause() {
    }

    public void onSaveInstanceState(Bundle outState) {
    }

    public void onLowMemory() {
    }

    public void onDestroy() {
    }
}
