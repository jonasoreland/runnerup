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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

import org.runnerup.common.util.Constants;
import org.runnerup.tracker.LocationListenerBase;


public class PersistentGpsLoggerListener extends LocationListenerBase implements
        Constants {
    private final java.lang.Object mLock;
    private SQLiteDatabase mDB;
    private java.lang.String mTable;
    private ContentValues mKey;
    private final boolean mLogGpxAccuracy;

    public PersistentGpsLoggerListener(SQLiteDatabase _db, String _table,
            ContentValues _key, boolean logGpxAccuracy) {
        this.mLock = new java.lang.Object();
        this.mDB = _db;
        this.mTable = _table;
        this.mLogGpxAccuracy = logGpxAccuracy;
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

    public void onLocationChanged(Location arg0, Double eleValue, Long elapsed, Double distance,
        Integer hrValue, Float cadValue, Float temperatureValue, Float pressureValue) {
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
        if (eleValue != null) {
            values.put(DB.LOCATION.ALTITUDE, eleValue);
        }
        //Used by Google Fit, so logged by default
        if (arg0.hasAccuracy()) {
            values.put(DB.LOCATION.ACCURANCY, arg0.getAccuracy());
        }

        if (this.mLogGpxAccuracy) {
            //Accuracy related, normally not used in exports
            //null data still uses one byte storage
            if (arg0.hasAltitude()) {
                values.put(DB.LOCATION.GPS_ALTITUDE, arg0.getAltitude());
            }
            if (arg0.hasSpeed()) {
                values.put(DB.LOCATION.SPEED, arg0.getSpeed());
            }
            if (arg0.hasBearing()) {
                values.put(DB.LOCATION.BEARING, arg0.getBearing());
            }
            //Most GPS chips also includes no of sats
            if (arg0.getExtras() != null) {
                int sats = arg0.getExtras().getInt("satellites", -1);
                if (sats >= 0) {
                    values.put(DB.LOCATION.SATELLITES, sats);
                }
            }
            //Not accuracy related but unused by exporters
            if (pressureValue != null) {
                values.put(DB.LOCATION.PRESSURE, pressureValue);
            }
        }
        if (elapsed != null) {
            values.put(DB.LOCATION.ELAPSED, elapsed);
        }
        if (distance != null) {
            values.put(DB.LOCATION.DISTANCE, distance);
        }
        if (hrValue != null) {
            values.put(DB.LOCATION.HR, hrValue);
        }
        if (cadValue != null) {
            values.put(DB.LOCATION.CADENCE, cadValue);
        }
        if (temperatureValue != null) {
            values.put(DB.LOCATION.TEMPERATURE, temperatureValue);
        }
        if (mDB != null) {
            mDB.insert(mTable, null, values);
        }
    }
}
