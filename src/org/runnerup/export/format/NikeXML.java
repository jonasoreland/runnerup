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

import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import org.runnerup.util.Constants.DB;
import org.runnerup.util.Formatter;
import org.xmlpull.v1.XmlSerializer;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Xml;

/**
 * @author jonas.oreland@gmail.com
 * 
 */
import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class NikeXML {

	private static final String DEVICE = "iPod";

	long mID = 0;
	SQLiteDatabase mDB = null;
	XmlSerializer mXML = null;
	SimpleDateFormat simpleDateFormat = null;

	public NikeXML(final SQLiteDatabase db) {
		mDB = db;
		simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ssZ",
				Locale.US);
	}

	private String formatTime(final long time) {
		final String s = simpleDateFormat.format(new Date(time));
		return String.format("%s:%s", s.substring(0, 22), s.substring(22)); // plain weird!
	}

	enum Dim {
		DISTANCE,
		LAP,
		TIME,
		SPEED
	};
	
	public void export(final long activityId, final Writer writer) throws Exception {

		final String[] aColumns = { DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
				DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME,
				DB.ACTIVITY.SPORT };
		final Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
				+ activityId, null, null, null, null);
		cursor.moveToFirst();

		final long startTime = cursor.getLong(2) * 1000; // epoch
		final double distance = cursor.getDouble(3);
		final long duration = cursor.getLong(4);
		try {
			mXML = Xml.newSerializer();
			mXML.setFeature(
					"http://xmlpull.org/v1/doc/features.html#indent-output",
					true);
			mXML.setOutput(writer);
			mXML.startDocument("UTF-8", true);
			mXML.startTag("", "sportsData");
			mXML.startTag("", "runSummary");
			mXML.startTag("", "time");
			mXML.text(formatTime(startTime));
			mXML.endTag("", "time");
			mXML.startTag("", "duration");
			mXML.text(Long.toString(duration * 1000)); // in ms
			mXML.endTag("", "duration");
			mXML.startTag("", "distance");
			mXML.attribute("", "unit", "km");
			mXML.text(Double.toString(distance / 1000)); // in km
			mXML.endTag("", "distance");
			mXML.startTag("", "calories");
			mXML.text("0");
			mXML.endTag("", "calories");
			mXML.startTag("", "battery");
			mXML.endTag("", "battery");
			mXML.endTag("", "runSummary");

			mXML.startTag("", "template");
			mXML.startTag("", "templateName");
			mXML.cdsect("Basic");
			mXML.endTag("", "templateName");
			mXML.endTag("", "template");

			mXML.startTag("", "goal");
			mXML.attribute("", "type", "");
			mXML.attribute("", "unit", "");
			mXML.attribute("", "value", "");
			mXML.endTag("", "goal");

			mXML.startTag("", "userInfo");
			mXML.startTag("", "empedID");
			mXML.text("XXXXXXXXXXX");
			mXML.endTag("", "empedID");
			mXML.startTag("", "weight");
			mXML.endTag("", "weight");
			mXML.startTag("", "device");
			mXML.text(DEVICE);
			mXML.endTag("", "device");
			mXML.startTag("", "calibration");
			mXML.endTag("", "calibration");
			mXML.endTag("", "userInfo");

			mXML.startTag("", "startTime");
			mXML.text(formatTime(startTime));
			mXML.endTag("", "startTime");

			mXML.startTag("", "snapShotList");
			mXML.attribute("", "snapShotType", "kmSplit");
			emitList(activityId, Dim.DISTANCE, 1000d, new SnapshotList());
			mXML.endTag("", "snapShotList");

			mXML.startTag("", "snapShotList");
			mXML.attribute("", "snapShotType", "mileSplit");
			emitList(activityId, Dim.DISTANCE, Formatter.mi_meters, new SnapshotList());
			mXML.endTag("", "snapShotList");

			mXML.startTag("", "snapShotList");
			mXML.attribute("", "snapShotType", "userClick");
			emitList(activityId, Dim.LAP, 1, new SnapshotList("onDemandVP"));
			mXML.endTag("", "snapShotList");

			mXML.startTag("", "extendedDataList");

			{
				mXML.startTag("", "extendedData");
				mXML.attribute("", "dataType", "distance");
				mXML.attribute("", "intervalType", "time");
				mXML.attribute("", "intervalUnit", "s");
				mXML.attribute("", "intervalValue", "10");
				final ExtendedData e = new ExtendedData(Dim.DISTANCE);
				e.buf.append("0.0");
				emitList(activityId, Dim.TIME, 10 * 1000d, e);
				mXML.text(e.buf.toString());
				mXML.endTag("", "extendedData");
			}

			{
				mXML.startTag("", "extendedData");
				mXML.attribute("", "dataType", "speed");
				mXML.attribute("", "intervalType", "time");
				mXML.attribute("", "intervalUnit", "s");
				mXML.attribute("", "intervalValue", "10");
				final ExtendedData e = new ExtendedData(Dim.SPEED);
				e.buf.append("0.0");
				emitList(activityId, Dim.TIME, 10 * 1000d, e);
				mXML.text(e.buf.toString());
				mXML.endTag("", "extendedData");
			}

			mXML.endTag("", "extendedDataList");

			mXML.endTag("", "sportsData");
			mXML.endDocument();
			mXML.flush();
		} catch (final Exception e) {
			throw e;
		}
		cursor.close();
	}

	abstract class Emitter {

		public abstract void emit(Pos p, Vector<Pos> posHist, Vector<Location> hist) throws Exception;
	};
	
	class SnapshotList extends Emitter 
	{
		String event = null;

		public SnapshotList() {
		}

		public SnapshotList(final String evnt) {
			event = evnt;
		}

		@Override
		public void emit(final Pos p, final Vector<Pos> posHist, final Vector<Location> hist) throws Exception {
			mXML.startTag("", "snapShot");
			if (event != null)
				mXML.attribute("", "event", event);
			mXML.startTag("", "duration");
			mXML.text(Long.toString(p.sumTime));
			mXML.endTag("", "duration");

			mXML.startTag("", "distance");
			mXML.text(Double.toString(Math.round(1000.0 * p.sumDistance / 1000.0) / 1000.0d));
			mXML.endTag("", "distance");

			mXML.startTag("", "pace");
			double deltaTime = p.sumTime;
			double deltaDist = p.sumDistance;
			if (!posHist.isEmpty()) {
				deltaTime -= posHist.lastElement().sumTime;
				deltaDist -= posHist.lastElement().sumDistance;
			}
			double pace = 0;
			if (deltaDist != 0) {
				pace = 1000d * deltaTime / deltaDist;
			}
			mXML.text(Long.toString(Math.round(pace)));
			mXML.endTag("", "pace");
			mXML.endTag("", "snapShot");
		}
	};
	
	class ExtendedData extends Emitter
	{
		Dim d = null;
		StringBuffer buf = new StringBuffer();
		
		public ExtendedData(final Dim dim) {
			d = dim;
		}

		@Override
		public void emit(final Pos p, final Vector<Pos> posHist, final Vector<Location> hist) throws Exception {
			if (d == Dim.DISTANCE) {
				buf.append(' ');
				buf.append(Double.toString(Math.round(1000.0 * p.sumDistance / 1000.0) / 1000.0d));
			} else if (d == Dim.SPEED) {
				double deltaTime = p.sumTime;
				double deltaDist = p.sumDistance;
				if (!posHist.isEmpty()) {
					deltaTime -= posHist.lastElement().sumTime;
					deltaDist -= posHist.lastElement().sumDistance;
				}
				double speed = 0; // km/h
				if (deltaTime != 0) {
					speed = deltaDist / deltaTime;
				}
				buf.append(' ');
				buf.append(Double.toString(speed));
			}
		}
	};

	class Pos {
		public Pos() {
		}
		public Pos(final Pos p) {
			sumTime = p.sumTime;
			sumDistance = p.sumDistance;
		}
		long sumTime = 0;
		double sumDistance = 0;
	};
	
	private void emitList(final long activityId, final Dim d, final double add, final Emitter out)
			throws Exception {
		double first = add;

		final String[] pColumns = { DB.LOCATION.LAP, // 0
				DB.LOCATION.TIME, // 1
				DB.LOCATION.LATITUDE, // 2
				DB.LOCATION.LONGITUDE, // 3
				DB.LOCATION.ALTITUDE, // 4
				DB.LOCATION.TYPE }; // 5

		final Cursor c = mDB.query(DB.LOCATION.TABLE, pColumns, DB.LOCATION.ACTIVITY
				+ " = " + activityId, null, null, null, null);

		try {
			final Pos p = new Pos();
			int lastLap = 0;
			final Vector<Location> locHist = new Vector<Location>();
			final Vector<Pos> posHist = new Vector<Pos>();
			if (c.moveToFirst()) {
				do {
					final int type = c.getInt(5);
					if (type == DB.LOCATION.TYPE_RESUME) {
						locHist.clear();
						continue;
					}

					final Location l = new Location("Sill E Dill");
					final int lap = c.getInt(0);
					l.setTime(c.getLong(1));
					l.setLatitude(c.getDouble(2));
					l.setLongitude(c.getDouble(3));
					l.setProvider("" + c.getLong(3));

					long deltaTime = 0;
					double deltaDist = 0;
					double bearing = 0;
					if (!locHist.isEmpty()) {
						deltaTime = l.getTime()
								- locHist.lastElement().getTime();
						deltaDist = l.distanceTo(locHist.lastElement());
						bearing = locHist.lastElement().bearingTo(l);
					}

					while ((d == Dim.DISTANCE && p.sumDistance + deltaDist >= first)
							|| (d == Dim.TIME && p.sumTime + deltaTime >= first)) {

						double diffTime = 0;
						double diffDist = 0;
						double pct = 0;
						if (d == Dim.DISTANCE) {
							diffDist = first - p.sumDistance;
							pct = diffDist / deltaDist;
							diffTime = deltaTime * pct;
						} else {
							diffTime = first - p.sumTime;
							pct = diffTime / deltaTime;
							diffDist = deltaDist * pct;
						}

						final Location tmp = new Location(locHist.lastElement());
						move(tmp, bearing, diffDist); // move location
						tmp.setTime((long) (tmp.getTime() + diffTime)); // move
																		// time

						locHist.add(tmp);

						p.sumDistance += diffDist;
						p.sumTime += diffTime;
						out.emit(p, posHist, locHist);
						posHist.add(new Pos(p));

						locHist.remove(locHist.size() - 1); // remove synthetic
															// location

						deltaTime -= diffTime;
						deltaDist -= diffDist;
						first += add;
					}
					if (d == Dim.LAP && lastLap != lap) {
						lastLap = lap;
						if ((posHist.isEmpty() && p.sumTime > 0 && p.sumDistance > 0)
								|| (p.sumTime > posHist.lastElement().sumTime && p.sumDistance > posHist
										.lastElement().sumDistance)) {
							out.emit(p, posHist, locHist);
							posHist.add(new Pos(p));
						} else {
							continue;
						}
					}

					locHist.add(l);
					if (locHist.size() == 6) {
						locHist.remove(0);
					}
					p.sumTime += deltaTime;
					p.sumDistance += deltaDist;
				} while (c.moveToNext());
			}
		} finally {
			if (c != null)
				c.close();
		}
	}

	static final double R = 6371.0; // radius of earth in km

	/**
	 * Move a location <em>dist</em> meters in direction of <em>bearing</em>
	 *
	 * @param l
	 * @param bearing
	 * @param dist
	 *
	 * @see http://www.movable-type.co.uk/scripts/latlong.html
	 */
	public static void move(final Location l, final double bearing, final double dist) {
		final double d = dist / 1000.0;     // in km
		final double lat1 = Math.toRadians(l.getLatitude());
		final double lon1 = Math.toRadians(l.getLongitude());
		final double brng = Math.toRadians(bearing);
		final double lat2 = Math.asin(Math.sin(lat1)*Math.cos(d/R) + 
	              Math.cos(lat1)*Math.sin(dist/R)*Math.cos(brng));
		final double lon2 = lon1 + Math.atan2(Math.sin(brng)*Math.sin(d/R)*Math.cos(lat1), 
	                     Math.cos(d/R)-Math.sin(lat1)*Math.sin(lat2));

		l.setLatitude(Math.toDegrees(lat2));
		l.setLongitude(Math.toDegrees(lon2));
	}
}
