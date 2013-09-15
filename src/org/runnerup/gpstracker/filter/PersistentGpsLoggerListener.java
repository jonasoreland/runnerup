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
package org.runnerup.gpstracker.filter;

import org.runnerup.gpstracker.LocationListenerBase;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;

import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class PersistentGpsLoggerListener extends LocationListenerBase implements
		org.runnerup.util.Constants {
	private java.lang.Object mLock;
	private SQLiteDatabase mDB;
	private java.lang.String mTable;
	private ContentValues mKey;

	public PersistentGpsLoggerListener(SQLiteDatabase _db, String _table,
			ContentValues _key) {
		this.mLock = new java.lang.Object();
		this.mDB = _db;
		this.mTable = _table;
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

	@Override
	public void onLocationChanged(Location arg0) {
		super.onLocationChanged(arg0);
		onLocationChanged(arg0, null);
	}

	public void onLocationChanged(Location arg0, Integer hrValue) {
		ContentValues values;
		synchronized (mLock) {
			if (mKey == null)
				values = new ContentValues();
			else
				values = new ContentValues(mKey);
		}

		values.put(DB.LOCATION.TIME, arg0.getTime());
		values.put(DB.LOCATION.LATITUDE, (float) arg0.getLatitude());
		values.put(DB.LOCATION.LONGITUDE, (float) arg0.getLongitude());
		if (arg0.hasAccuracy()) {
			values.put(DB.LOCATION.ACCURANCY, arg0.getAccuracy());
		}
		if (arg0.hasSpeed()) {
			values.put(DB.LOCATION.SPEED, arg0.getSpeed());
		}
		if (arg0.hasAltitude()) {
			values.put(DB.LOCATION.ALTITUDE, (float) arg0.getAltitude());
		}
		if (arg0.hasBearing()) {
			values.put(DB.LOCATION.BEARING, arg0.getBearing());
		}
		if (hrValue != null) {
			values.put(DB.LOCATION.HR, hrValue.intValue());
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
