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
package org.runnerup.db;

import java.util.ArrayList;

import org.runnerup.R;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper implements
		org.runnerup.util.Constants {

	private static final int DBVERSION = 5;
	private static final String DBNAME = "runnerup.db";

	private static final String CREATE_TABLE_ACTIVITY = "create table "
			+ DB.ACTIVITY.TABLE + " ( "
			+ "_id integer primary key autoincrement, "
			+ DB.ACTIVITY.START_TIME
			+ " integer not null default (strftime('%s','now')),"
			+ DB.ACTIVITY.DISTANCE + " real, " + DB.ACTIVITY.TIME
			+ " integer, " + DB.ACTIVITY.NAME + " text," + DB.ACTIVITY.COMMENT
			+ " text," + DB.ACTIVITY.TYPE + " integer,"
			+ "deleted integer not null default 0, "
			+ "nullColumnHack text null" + ");";

	private static final String CREATE_TABLE_LOCATION = "create table "
			+ DB.LOCATION.TABLE + " ( "
			+ "_id integer primary key autoincrement, " + DB.LOCATION.ACTIVITY
			+ " integer not null, " + DB.LOCATION.LAP + " integer not null, "
			+ DB.LOCATION.TYPE + " integer not null, " + DB.LOCATION.TIME
			+ " integer not null, " + DB.LOCATION.LONGITUDE
			+ " real not null, " + DB.LOCATION.LATITUDE + " real not null, "
			+ DB.LOCATION.ACCURANCY + " real, " + DB.LOCATION.ALTITUDE
			+ " real, " + DB.LOCATION.SPEED + " real, " + DB.LOCATION.BEARING
			+ " real " + ");";

	private static final String CREATE_TABLE_LAP = "create table "
			+ DB.LAP.TABLE + " ( " + "_id integer primary key autoincrement, "
			+ DB.LAP.ACTIVITY + " integer not null, " + DB.LAP.LAP
			+ " integer not null, " + DB.LAP.TYPE
			+ " integer not null default 0, " + DB.LAP.TIME + " integer, "
			+ DB.LAP.DISTANCE + " real, " + DB.LAP.PLANNED_TIME + " integer, "
			+ DB.LAP.PLANNED_DISTANCE + " real, " + DB.LAP.PLANNED_PACE
			+ " real " + ");";

	private static final String CREATE_TABLE_ACCOUNT = "create table "
			+ DB.ACCOUNT.TABLE + " ( "
			+ "_id integer primary key autoincrement, " + DB.ACCOUNT.NAME
			+ " text not null, " + DB.ACCOUNT.DESCRIPTION + " text, "
			+ DB.ACCOUNT.URL + " text, " + DB.ACCOUNT.FORMAT
			+ " text not null, " + DB.ACCOUNT.DEFAULT
			+ " int not null default 1, " + DB.ACCOUNT.ENABLED
			+ " int not null default 1," + DB.ACCOUNT.AUTH_METHOD
			+ " text not null, " + DB.ACCOUNT.AUTH_CONFIG + " text, "
			+ DB.ACCOUNT.ICON + " int null, "
			+ "UNIQUE (" + DB.ACCOUNT.NAME + ")" + ");";

	private static final String CREATE_TABLE_REPORT = "create table "
			+ DB.EXPORT.TABLE + " ( "
			+ "_id integer primary key autoincrement, " + DB.EXPORT.ACTIVITY
			+ " integer not null, " + DB.EXPORT.ACCOUNT + " integer not null, "
			+ DB.EXPORT.STATUS + " text, " + DB.EXPORT.EXTERNAL_ID + " text, "
			+ DB.EXPORT.EXTRA + " int not null default 1" + ");";

	public DBHelper(Context context) {
		super(context, DBNAME, null, DBVERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase arg0) {
		arg0.execSQL(CREATE_TABLE_ACTIVITY);
		arg0.execSQL(CREATE_TABLE_LAP);
		arg0.execSQL(CREATE_TABLE_LOCATION);
		arg0.execSQL(CREATE_TABLE_ACCOUNT);
		arg0.execSQL(CREATE_TABLE_REPORT);

		onUpgrade(arg0, 0, DBVERSION);
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int oldVersion, int newVersion) {
		System.err.println("onUpgrade: oldVersion: " + oldVersion + ", newVersion: " + newVersion);
		
		if (newVersion < oldVersion) {
			throw new java.lang.UnsupportedOperationException(
					"Downgrade not supported");
		}

		if (oldVersion > 0 && oldVersion < 5 && newVersion >= 5) {
			arg0.execSQL("alter table account add column icon int");
		}
		
		insertAccounts(arg0);
	}

	public void insertAccounts(SQLiteDatabase arg0) {
		boolean yet = true;
		boolean notyet = false;
		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, "Garmin");
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a0_garminlogo);
			insertAccount(arg0, values);
		}

		if (yet)
		{
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, "RunKeeper");
			values.put(DB.ACCOUNT.FORMAT, "runkeeper");
			values.put(DB.ACCOUNT.AUTH_METHOD, "oauth2");
			values.put(DB.ACCOUNT.ICON, R.drawable.a1_rklogo);
			insertAccount(arg0, values);
		}

		if (notyet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, "jogg.se");
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.DEFAULT, 1);
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, "FunBeat");
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a2_funbeatlogo);
			insertAccount(arg0, values);
		}

	}

	void insertAccount(SQLiteDatabase arg0, ContentValues arg1) {
		String cols[] = { "_id" };
		String arr[] = { arg1.getAsString(DB.ACCOUNT.NAME) };
		Cursor c = arg0.query(DB.ACCOUNT.TABLE, cols, DB.ACCOUNT.NAME + " = ?",
				arr, null, null, null);
		if (!c.moveToFirst())
			arg0.insert(DB.ACCOUNT.TABLE, null, arg1);
		else {
			arg0.update(DB.ACCOUNT.TABLE, arg1, DB.ACCOUNT.NAME + " = ?", arr);
			System.err.println("update: " + arg1);
		}
		c.close();
		c = null;
	}

	public static ContentValues get(Cursor c) {
		if (c.isClosed() || c.isAfterLast() || c.isBeforeFirst())
			return null;
		ContentValues ret = new ContentValues();
		final int cnt = c.getColumnCount();
		for (int i = 0; i < cnt; i++) {
			if (!c.isNull(i)) {
				ret.put(c.getColumnName(i), c.getString(i));
			}
		}
		return ret;
	}

	public static ContentValues[] toArray(Cursor c) {
		ArrayList<ContentValues> list = new ArrayList<ContentValues>();
		if (c.moveToFirst()) {
			do {
				list.add(get(c));
			} while (c.moveToNext());
		}
		return list.toArray(new ContentValues[list.size()]);
	}
}
