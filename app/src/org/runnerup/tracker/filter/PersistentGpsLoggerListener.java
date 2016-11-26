/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

package org.runnerup.tracker.filter;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import org.matthiaszimmermann.location.egm96.Geoid;
import org.runnerup.common.util.Constants;
import org.runnerup.tracker.LocationListenerBase;

@TargetApi(Build.VERSION_CODES.FROYO)
public class PersistentGpsLoggerListener extends LocationListenerBase implements
        Constants {
    private final java.lang.Object mLock;
    private SQLiteDatabase mDB;
    private java.lang.String mTable;
    private ContentValues mKey;
    private boolean altitudeCorrection;

    public PersistentGpsLoggerListener(SQLiteDatabase _db, String _table,
            ContentValues _key, boolean altitudeCorrection) {
        this.mLock = new java.lang.Object();
        this.mDB = _db;
        this.mTable = _table;
        this.altitudeCorrection = altitudeCorrection;
        setKey(_key);
    }

    public SQLiteDatabase getDB() {
        return mDB;
    }

    public void setDB(SQLiteDatabase _db) {
        mDB = _db;
    }

    public String getTable() {
        return mTable;
    }

    public void setTable(String _tab) {
        mTable = _tab;
    }

    public ContentValues getKey() {
        synchronized (mLock) {
            if (mKey == null)
                return null;
            return new ContentValues(mKey);
        }
    }

    public void setKey(ContentValues key) {
        synchronized (mLock) {
            if (key == null)
                mKey = null;
            else
                mKey = new ContentValues(key);
        }
    }

    public void onLocationChanged(Location arg0, @SuppressWarnings("UnusedParameters") Long elapsed, @SuppressWarnings("UnusedParameters") Double distance, Integer hrValue) {
        ContentValues values;
        synchronized (mLock) {
            if (mKey == null)
                values = new ContentValues();
            else
                values = new ContentValues(mKey);
        }

        values.put(DB.LOCATION.TIME, arg0.getTime());
        values.put(DB.LOCATION.LATITUDE, arg0.getLatitude());
        values.put(DB.LOCATION.LONGITUDE, arg0.getLongitude());
        if (arg0.hasAltitude()) {
            double altitude = arg0.getAltitude();
            if (this.altitudeCorrection) {
                altitude -= Geoid.getOffset(arg0.getLatitude(),
                        arg0.getLongitude());
            }
            values.put(DB.LOCATION.ALTITUDE, altitude);
        }

        //Accuracy related, normally not used in exports
        //Most GPS chips also includes no of sats: arg0.getExtras().getInt("satellites", -1)
        if (arg0.hasAccuracy()) {
            values.put(DB.LOCATION.ACCURANCY, arg0.getAccuracy());
        }
        if (arg0.hasSpeed()) {
            values.put(DB.LOCATION.SPEED, arg0.getSpeed());
        }
        if (arg0.hasBearing()) {
            values.put(DB.LOCATION.BEARING, arg0.getBearing());
        }

        //if (elapsed != null) {
        //    values.put(DB.LOCATION2.ELAPSED, elapsed);
        //}
        //if (distance != null) {
        //    values.put(DB.LOCATION2.DISTANCE, distance);
        //}
        if (hrValue != null) {
            values.put(DB.LOCATION.HR, hrValue);
        }
        if (mDB != null) {
            mDB.insert(mTable, null, values);
        }
    }

    @Override
    public void onProviderDisabled(String arg0) {
        super.onProviderDisabled(arg0);
    }

    @Override
    public void onProviderEnabled(String arg0) {
        super.onProviderEnabled(arg0);
    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        super.onStatusChanged(arg0, arg1, arg2);
    }
}
