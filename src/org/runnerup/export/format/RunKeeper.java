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
package org.runnerup.export.format;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.runnerup.util.Constants.DB;
import org.runnerup.util.JsonWriter;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author jonas.oreland@gmail.com
 * 
 */
public class RunKeeper {

	long mID = 0;
	SQLiteDatabase mDB = null;

	public RunKeeper(SQLiteDatabase db) {
		mDB = db;
	}

	static String formatTime(long time) {
		return new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss", Locale.US)
				.format(new Date(time));
	}

	public void export(long activityId, Writer writer) throws IOException {

		String[] aColumns = { DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
				DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME };
		Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
				+ activityId, null, null, null, null);
		cursor.moveToFirst();

		long startTime = cursor.getLong(2); // epoch
		double distance = cursor.getDouble(3);
		long duration = cursor.getLong(4);
		String comment = null;
		if (!cursor.isNull(1))
			comment = cursor.getString(1);
		try {
			JsonWriter w = new JsonWriter(writer);
			w.beginObject();
			w.name("type").value("Running");
			w.name("equipment").value("None");
			w.name("start_time").value(formatTime(startTime * 1000));
			w.name("total_distance").value(distance);
			w.name("duration").value(duration);
			if (comment != null)
				w.name("notes").value(comment);
			w.name("path");
			w.beginArray();
			exportPath(activityId, startTime, w);
			w.endArray();
			w.name("post_to_facebook").value(false);
			w.name("post_to_twitter").value(false);
			w.endObject();
		} catch (IOException e) {
			throw e;
		}
		cursor.close();
	}

	private void exportPath(long activityId, long startTime, JsonWriter w)
			throws IOException {
		String[] pColumns = { DB.LOCATION.TIME, DB.LOCATION.LATITUDE,
				DB.LOCATION.LONGITUDE, DB.LOCATION.ALTITUDE, DB.LOCATION.TYPE };
		Cursor cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
				DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null,
				null);
		if (cursor.moveToFirst()) {
			startTime = cursor.getLong(0);
			do {
				w.beginObject();
				w.name("timestamp").value(
						(cursor.getLong(0) - startTime) / 1000);
				w.name("latitude").value(cursor.getDouble(1));
				w.name("longitude").value(cursor.getDouble(2));
				if (!cursor.isNull(3)) {
					w.name("altitude").value(cursor.getDouble(3));
				}
				if (cursor.getLong(4) == DB.LOCATION.TYPE_START) {
					w.name("type").value("start");
				} else if (cursor.getLong(4) == DB.LOCATION.TYPE_END) {
					w.name("type").value("end");
				} else if (cursor.getLong(4) == DB.LOCATION.TYPE_PAUSE) {
					w.name("type").value("pause");
				} else if (cursor.getLong(4) == DB.LOCATION.TYPE_RESUME) {
					w.name("type").value("resume");
				} else if (cursor.getLong(4) == DB.LOCATION.TYPE_GPS) {
					w.name("type").value("gps");
				} else {
					w.name("type").value("manual");
				}
				w.endObject();
			} while (cursor.moveToNext());
		}
		cursor.close();
	}
}
