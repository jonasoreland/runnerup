/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import java.util.TimeZone;

import org.runnerup.util.Constants.DB;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

/**
 * @author jonas.oreland@gmail.com
 * 
 */
public class EndomondoTrack {

	long mID = 0;
	SQLiteDatabase mDB = null;
	SimpleDateFormat simpleDateFormat = null;

	public EndomondoTrack(final SQLiteDatabase db) {
		mDB = db;
		simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss 'UTC'",
				Locale.US);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public static class Summary {
		public int sport;
		public long duration;
		public double distance;
	};
	
	public void export(final long activityId, final Writer writer, Summary summary) throws IOException {

		final String[] aColumns = { DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
				DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME,
				DB.ACTIVITY.SPORT };
		final Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
				+ activityId, null, null, null, null);
		cursor.moveToFirst();

		final double distance = cursor.getDouble(3) / 1000; // in km
		final long duration = cursor.getLong(4);

		if (summary != null) {
			summary.distance = distance;
			summary.duration = duration;
			switch(cursor.getInt(5)) {
			case DB.ACTIVITY.SPORT_RUNNING:
				summary.sport = 0;
				break;
			case DB.ACTIVITY.SPORT_BIKING:
				summary.sport = 2;
				break;
			default:
				summary.sport = 22; // other
				break;
			}
		}
		cursor.close();

		emitWaypoints(activityId, writer);
	}

	private void emitWaypoints(final long activityId, final Writer writer) throws IOException {
		final String[] pColumns = { DB.LOCATION.LAP, // 0
				DB.LOCATION.TIME, // 1
				DB.LOCATION.LATITUDE, // 2
				DB.LOCATION.LONGITUDE, // 3
				DB.LOCATION.ALTITUDE, // 4
				DB.LOCATION.TYPE,	// 5
				DB.LOCATION.HR }; // 6

		final Cursor c = mDB.query(DB.LOCATION.TABLE, pColumns, DB.LOCATION.ACTIVITY
				+ " = " + activityId, null, null, null, null);

		double distance = 0;
		Location lastLoc = null;
		try {
			if (c.moveToFirst()) {
				do {
					Location l = new Location("Dill");
					l.setLatitude(c.getDouble(2));
					l.setLongitude(c.getDouble(3));
					if (lastLoc != null) {
						distance += l.distanceTo(lastLoc);
					}
					lastLoc = l;
					
//			        # timestamp;
//			        # type (2=start, 3=end, 0=pause, 1=resume);
//			        # latitude;
//			        # longitude;
//			        #;
//			        #;
//			        # alt;
//			        # hr;
					
					writer.write(simpleDateFormat.format(new Date(c.getLong(1))));
					final int type = c.getInt(5);
					switch(type) {
					case DB.LOCATION.TYPE_START:
						writer.write(";2;");
						break;
					case DB.LOCATION.TYPE_END:
						lastLoc = null;
						writer.write(";3;");
						break;
					case DB.LOCATION.TYPE_PAUSE:
						lastLoc = null;
						writer.write(";0;");
						break;
					case DB.LOCATION.TYPE_RESUME:
						writer.write(";1;");
						break;
					case DB.LOCATION.TYPE_GPS:
					default:
						writer.write(";;");
					}
					writer.write(Double.toString(c.getDouble(2)));
					writer.write(';');
					writer.write(Double.toString(c.getDouble(3)));
					writer.write(';');
					writer.write(Double.toString(distance / 1000)); // in km
					writer.write(';');
					// unknown
					writer.write(';');
					// alt
					if (!c.isNull(4)) {
						writer.write(Double.toString(c.getDouble(4)));
					}
					writer.write(';');
					//hr
					if(!c.isNull(6)){
						writer.write(Integer.toString(c.getInt(6)));
					}
					writer.write(';');
					writer.append('\n');
				} while (c.moveToNext());
			}
		} finally {
			if (c != null)
				c.close();
		}
	}
}
