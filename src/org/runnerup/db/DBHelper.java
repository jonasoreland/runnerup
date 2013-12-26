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
package org.runnerup.db;

import java.util.ArrayList;

import org.runnerup.R;
import org.runnerup.export.DigifitUploader;
import org.runnerup.export.Endomondo;
import org.runnerup.export.FunBeatUploader;
import org.runnerup.export.GarminUploader;
import org.runnerup.export.JoggSE;
import org.runnerup.export.MapMyRunUploader;
import org.runnerup.export.NikePlus;
import org.runnerup.export.RunKeeperUploader;
import org.runnerup.export.RunnerUpLive;
import org.runnerup.export.RunningAHEAD;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DBHelper extends SQLiteOpenHelper implements
		org.runnerup.util.Constants {

	private static final int DBVERSION = 21;
	private static final String DBNAME = "runnerup.db";

	private static final String CREATE_TABLE_ACTIVITY = "create table "
			+ DB.ACTIVITY.TABLE + " ( "
			+ ( "_id integer primary key autoincrement, " ) 
			+ ( DB.ACTIVITY.START_TIME + " integer not null default (strftime('%s','now'))," )
			+ ( DB.ACTIVITY.DISTANCE + " real, " ) 
			+ ( DB.ACTIVITY.TIME + " integer, " )
			+ ( DB.ACTIVITY.NAME + " text," )
			+ ( DB.ACTIVITY.COMMENT + " text," )
			+ ( DB.ACTIVITY.SPORT + " integer," )
			+ ( "deleted integer not null default 0, " )
			+ "nullColumnHack text null" + ");";

	private static final String CREATE_TABLE_LOCATION = "create table "
			+ DB.LOCATION.TABLE + " ( "
			+ ( "_id integer primary key autoincrement, " )
			+ ( DB.LOCATION.ACTIVITY + " integer not null, " )
			+ ( DB.LOCATION.LAP + " integer not null, " )
			+ ( DB.LOCATION.TYPE + " integer not null, " )
			+ ( DB.LOCATION.TIME + " integer not null, " )
			+ ( DB.LOCATION.LONGITUDE + " real not null, " )
			+ ( DB.LOCATION.LATITUDE + " real not null, " )
			+ ( DB.LOCATION.ACCURANCY + " real, " )
			+ ( DB.LOCATION.ALTITUDE + " real, " )
			+ ( DB.LOCATION.SPEED + " real, " )
			+ ( DB.LOCATION.BEARING	+ " real, " )
			+ ( DB.LOCATION.HR	+ " int " )
			+ ");";

	private static final String CREATE_TABLE_LAP = "create table "
			+ DB.LAP.TABLE + " ( " + "_id integer primary key autoincrement, "
			+ DB.LAP.ACTIVITY + " integer not null, " + DB.LAP.LAP
			+ " integer not null, " + DB.LAP.INTENSITY
			+ " integer not null default 0, " + DB.LAP.TIME + " integer, "
			+ DB.LAP.DISTANCE + " real, " + DB.LAP.PLANNED_TIME + " integer, "
			+ DB.LAP.PLANNED_DISTANCE + " real, " + DB.LAP.PLANNED_PACE
			+ " real " + ");";

	private static final String CREATE_TABLE_ACCOUNT = "create table "
			+ DB.ACCOUNT.TABLE + " ( "
			+ ( "_id integer primary key autoincrement, " ) 
			+ ( DB.ACCOUNT.NAME + " text not null, " )
			+ ( DB.ACCOUNT.DESCRIPTION + " text, " )
			+ ( DB.ACCOUNT.URL + " text, " ) 
			+ ( DB.ACCOUNT.FORMAT + " text not null, " )
			+ ( DB.ACCOUNT.FLAGS + " int not null default " + DB.ACCOUNT.DEFAULT_FLAGS + ", " )
			+ ( DB.ACCOUNT.ENABLED + " int not null default 1," )
			+ ( DB.ACCOUNT.AUTH_METHOD + " text not null, " )
			+ ( DB.ACCOUNT.AUTH_CONFIG + " text, " )
			+ ( DB.ACCOUNT.ICON + " int null, " )
			+ "UNIQUE (" + DB.ACCOUNT.NAME + ")" + ");";

	private static final String CREATE_TABLE_REPORT = "create table "
			+ DB.EXPORT.TABLE + " ( "
			+ "_id integer primary key autoincrement, " + DB.EXPORT.ACTIVITY
			+ " integer not null, " + DB.EXPORT.ACCOUNT + " integer not null, "
			+ DB.EXPORT.STATUS + " text, " + DB.EXPORT.EXTERNAL_ID + " text, "
			+ DB.EXPORT.EXTRA + " int not null default 1" + ");";

	private static final String CREATE_TABLE_AUDIO_SCHEMES = "create table "
			+ DB.AUDIO_SCHEMES.TABLE + " ( "
			+ ( "_id integer primary key autoincrement, " )
			+ ( DB.AUDIO_SCHEMES.NAME + " text not null, " )
			+ ( DB.AUDIO_SCHEMES.SORT_ORDER + " int not null, " )
			+ ( "unique (" + DB.AUDIO_SCHEMES.NAME + ")" )
			+ ");";

	private static final String CREATE_TABLE_FEED = "create table "
			+ DB.FEED.TABLE + " ( "
			+ ( "_id integer primary key autoincrement, " )
			+ ( DB.FEED.ACCOUNT_ID + " int not null, " )
			+ ( DB.FEED.EXTERNAL_ID + " text, " )
			+ ( DB.FEED.FEED_TYPE + " int not null, " )
			+ ( DB.FEED.FEED_SUBTYPE + " int, " )
			+ ( DB.FEED.FEED_TYPE_STRING + " text, " )
			+ ( DB.FEED.START_TIME + " int not null, " )
			+ ( DB.FEED.DURATION + " int, ")
			+ ( DB.FEED.DISTANCE + " double, ")
			+ ( DB.FEED.USER_ID + " text, " )
			+ ( DB.FEED.USER_FIRST_NAME + " text, " )
			+ ( DB.FEED.USER_LAST_NAME + " text, " )
			+ ( DB.FEED.USER_IMAGE_URL + " text, " )
			+ ( DB.FEED.NOTES + " text, " )
			+ ( DB.FEED.COMMENTS + " text, " )
			+ ( DB.FEED.URL + " text, " )
			+ ( DB.FEED.FLAGS + " text ")
			+ ");";

	private static final String CREATE_INDEX_FEED = "create index if not exists FEED_START_TIME " + 
			( " on " + DB.FEED.TABLE + " (" +  DB.FEED.START_TIME + ")" );

	
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
		arg0.execSQL(CREATE_TABLE_AUDIO_SCHEMES);
		arg0.execSQL(CREATE_TABLE_FEED);
		arg0.execSQL(CREATE_INDEX_FEED);
		
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

		if (oldVersion > 0 && oldVersion < 7 && newVersion >= 7) {
			arg0.execSQL(CREATE_TABLE_AUDIO_SCHEMES);
		}

		if (oldVersion > 0 && oldVersion < 16 && newVersion >= 16) {
			echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.HR + " int");
		}
		
		if (oldVersion > 0 && oldVersion < 10 && newVersion >= 10) {
			recreateAccount(arg0);
		}

		if (oldVersion > 0 && oldVersion < 17 && newVersion >= 17) {
			arg0.execSQL(CREATE_TABLE_FEED);
			arg0.execSQL(CREATE_INDEX_FEED);
			echoDo(arg0, "update account set " + DB.ACCOUNT.FLAGS + " = " + DB.ACCOUNT.FLAGS + " + " + (1 << DB.ACCOUNT.FLAG_FEED));
		}

		if (oldVersion > 0 && oldVersion < 18 && newVersion >= 18) {
			echoDo(arg0, "update account set auth_config = '{ \"access_token\":\"' || auth_config || '\" }' where auth_config is not null and auth_method='oauth2';");
		}

		if (oldVersion > 0 && oldVersion < 19 && newVersion >= 19) {
			echoDo(arg0, "update account set " + DB.ACCOUNT.FLAGS + " = " + DB.ACCOUNT.FLAGS + " + " + (1 << DB.ACCOUNT.FLAG_LIVE));
		}
		
		insertAccounts(arg0);
	}

	private static void echoDo(SQLiteDatabase arg0, String str) {
		System.err.println("execSQL(" + str + ")");
		arg0.execSQL(str);
	}
	
	private void recreateAccount(SQLiteDatabase arg0) {
		Cursor c = null;
		try {
			String cols[] = { "method" };
			c = arg0.query(DB.ACCOUNT.TABLE, cols, null, null, null, null, null);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		if (c != null)
			c.close();
		
		StringBuffer newtab = new StringBuffer();
		newtab.append(CREATE_TABLE_ACCOUNT);
		newtab.replace(0,  
				("create table " + DB.ACCOUNT.TABLE).length(),
				"create table " + DB.ACCOUNT.TABLE + "_new");
		String copy = 
				"insert into " + DB.ACCOUNT.TABLE + "_new" +
						"(_id, " +
						DB.ACCOUNT.NAME + ", " +
						DB.ACCOUNT.URL + ", " +
						DB.ACCOUNT.DESCRIPTION + ", " +
						DB.ACCOUNT.FORMAT + ", " +
						DB.ACCOUNT.FLAGS + ", " +
						DB.ACCOUNT.ENABLED + ", " +
						DB.ACCOUNT.AUTH_METHOD + ", " +
						DB.ACCOUNT.AUTH_CONFIG + ") " +
						"select " +
						"_id, " +
						DB.ACCOUNT.NAME + ", " +
						DB.ACCOUNT.URL + ", " +
						DB.ACCOUNT.DESCRIPTION + ", " +
						DB.ACCOUNT.FORMAT + ", " +
						DB.ACCOUNT.FLAGS + ", " +
						DB.ACCOUNT.ENABLED + ", " +
						DB.ACCOUNT.AUTH_METHOD + ", " +
						DB.ACCOUNT.AUTH_CONFIG + " " +
						"FROM " + DB.ACCOUNT.TABLE;
		try {
			echoDo(arg0, newtab.toString());
			echoDo(arg0, copy);
			echoDo(arg0, "alter table " + DB.ACCOUNT.TABLE + " rename to " + DB.ACCOUNT.TABLE + "_old");
			echoDo(arg0, "alter table " + DB.ACCOUNT.TABLE + "_new" + " rename to " + DB.ACCOUNT.TABLE);
			echoDo(arg0, "drop table " + DB.ACCOUNT.TABLE + "_old");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void insertAccounts(SQLiteDatabase arg0) {
		boolean yet = true;
		boolean notyet = false;

		if (notyet) {
			/**
			 * just removing warning from notyet
			 */
		}
		
		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, GarminUploader.NAME);
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a0_garminlogo);
			values.put(DB.ACCOUNT.URL, "http://connect.garmin.com/");
			insertAccount(arg0, values);
		}

		if (yet)
		{
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, RunKeeperUploader.NAME);
			values.put(DB.ACCOUNT.FORMAT, "runkeeper");
			values.put(DB.ACCOUNT.AUTH_METHOD, "oauth2");
			values.put(DB.ACCOUNT.ICON, R.drawable.a1_rklogo);
			values.put(DB.ACCOUNT.URL, "http://runkeeper.com/");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, JoggSE.NAME);
			values.put(DB.ACCOUNT.FORMAT, "gpx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a5_jogg);
			values.put(DB.ACCOUNT.URL, "http://jogg.se/");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, FunBeatUploader.NAME);
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a2_funbeatlogo);
			values.put(DB.ACCOUNT.URL, "http://www.funbeat.se/");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, MapMyRunUploader.NAME);
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a3_mapmyrun_logo);
			values.put(DB.ACCOUNT.URL, "http://www.mapmyrun.com/");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, NikePlus.NAME);
			values.put(DB.ACCOUNT.FORMAT, "nikeplus,gpx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a4_nikeplus);
			values.put(DB.ACCOUNT.URL, "http://nikeplus.nike.com");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, Endomondo.NAME);
			values.put(DB.ACCOUNT.FORMAT, "endomondotrack");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a6_endomondo);
			values.put(DB.ACCOUNT.URL, "http://www.endomondo.com");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, RunningAHEAD.NAME);
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "oauth2");
			values.put(DB.ACCOUNT.ICON, R.drawable.a7_runningahead);
			values.put(DB.ACCOUNT.URL, "http://www.runningahead.com");
			insertAccount(arg0, values);
		}
		
		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, DigifitUploader.NAME);
			values.put(DB.ACCOUNT.FORMAT, "tcx");
			values.put(DB.ACCOUNT.AUTH_METHOD, "post");
			values.put(DB.ACCOUNT.ICON, R.drawable.a9_digifit);
			values.put(DB.ACCOUNT.URL, "http://www.digifit.com");
			insertAccount(arg0, values);
		}

		if (yet) {
			ContentValues values = new ContentValues();
			values.put(DB.ACCOUNT.NAME, RunnerUpLive.NAME);
			values.put(DB.ACCOUNT.FORMAT, "");
			values.put(DB.ACCOUNT.AUTH_METHOD, "none");
			values.put(DB.ACCOUNT.ICON, R.drawable.a8_runneruplive);
			values.put(DB.ACCOUNT.URL, "http://weide.devsparkles.se/Demo/Map");
			values.put(DB.ACCOUNT.FLAGS,  (int)(1 << DB.ACCOUNT.FLAG_LIVE));
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
