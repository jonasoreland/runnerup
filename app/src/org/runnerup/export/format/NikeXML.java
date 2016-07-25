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

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.util.Formatter;
import org.runnerup.util.KXmlSerializer;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

/**
 * @author jonas.oreland@gmail.com
 * 
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class NikeXML {

    private static final String DEVICE = "iPod";

    SQLiteDatabase mDB = null;
    KXmlSerializer mXML = null;
    SimpleDateFormat simpleDateFormat = null;

    public NikeXML(final SQLiteDatabase db) {
        mDB = db;
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",
                Locale.US);
    }

    private String formatTime(final long time) {
        final String s = simpleDateFormat.format(new Date(time));
        return String.format("%s:%s", s.substring(0, 22), s.substring(22)); // plain
                                                                            // weird!
    }

    enum Dim {
        DISTANCE,
        LAP,
        TIME,
        SPEED,
        HR
    }

    public void export(final long activityId, final Writer writer) throws Exception {

        final String[] aColumns = {
                DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME,
                DB.ACTIVITY.SPORT
        };
        final Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + activityId, null, null, null, null);
        cursor.moveToFirst();

        final long startTime = cursor.getLong(2) * 1000; // epoch
        final double distance = cursor.getDouble(3);
        final long duration = cursor.getLong(4);
        try {
            mXML = new KXmlSerializer();
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
            final boolean hasHR = emitHeartrateStats(activityId);
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

            if (hasHR)
            {
                mXML.startTag("", "extendedData");
                mXML.attribute("", "dataType", "heartRate");
                mXML.attribute("", "intervalType", "time");
                mXML.attribute("", "intervalUnit", "s");
                mXML.attribute("", "intervalValue", "10");
                final ExtendedData e = new ExtendedData(Dim.HR);
                e.buf.append("0");
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

    private boolean emitHeartrateStats(long mID) throws IllegalArgumentException,
            IllegalStateException, IOException {
        String args[] = {
            Long.toString(mID)
        };
        Cursor c = mDB.rawQuery("select min(" + DB.LOCATION.HR + "), max(" + DB.LOCATION.HR
                + "), avg(" + DB.LOCATION.HR + ") FROM " + DB.LOCATION.TABLE + " WHERE "
                + DB.LOCATION.ACTIVITY + " = ?", args);
        if (c.moveToFirst() &&
                !(c.isNull(0) || c.isNull(1) || c.isNull(2))) {

            int minHR = c.getInt(0);
            int maxHR = c.getInt(1);
            int avgHR = c.getInt(2);
            c.close();

            mXML.startTag("", "heartrate");
            mXML.startTag("", "average");
            mXML.text(Integer.toString(avgHR));
            mXML.endTag("", "average");

            emitHRPosition(mID, "minimum", minHR);
            emitHRPosition(mID, "maximum", maxHR);
            mXML.endTag("", "heartrate");

            return true;
        }
        c.close();
        return false;
    }

    private void emitHRPosition(long mID, String string, int hrVal)
            throws IllegalArgumentException, IllegalStateException, IOException {
        long _id = 0;
        { // 1 find a point with specified value
            String args[] = {
                    Long.toString(mID), Integer.toString(hrVal)
            };
            Cursor c = mDB.rawQuery(
                    "select min(_id) from location where activity_id = ? and hr = ? limit 1", args);
            if (!c.moveToFirst()) {
                c.close();
                return;
            }
            _id = c.getLong(0);
            c.close();
        }

        // 2 iterate to that position from start...
        String cols[] = {
                DB.LOCATION.TYPE, DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE, DB.LOCATION.TIME,
                DB.LOCATION.SPEED
        };
        String args[] = {
                Long.toString(mID), Long.toString(_id)
        };
        Cursor c = mDB.query(DB.LOCATION.TABLE, cols,
                DB.LOCATION.ACTIVITY + " = ? AND _id <= ?", args, null, null, "_id");

        if (c.moveToFirst()) {
            Location last = null;
            double sumDist = 0;
            long sumTime = 0;
            do {
                switch (c.getInt(0)) {
                    case DB.LOCATION.TYPE_START:
                    case DB.LOCATION.TYPE_RESUME:
                        last = new Location("Dill");
                        last.setLatitude(c.getDouble(1));
                        last.setLongitude(c.getDouble(2));
                        last.setTime(c.getLong(3));
                        break;
                    case DB.LOCATION.TYPE_PAUSE:
                    case DB.LOCATION.TYPE_END:
                        last = null;
                        break;
                    case DB.LOCATION.TYPE_GPS:
                        Location l = new Location("Sill");
                        l.setLatitude(c.getDouble(1));
                        l.setLongitude(c.getDouble(2));
                        l.setTime(c.getLong(3));
                        if (!c.isNull(4))
                            l.setSpeed(c.getFloat(4));
                        sumDist += l.distanceTo(last);
                        sumTime += l.getTime() - last.getTime();
                        last = l;
                }
            } while (c.moveToNext());
            mXML.startTag("", string);
            mXML.startTag("", "duration");
            mXML.text(Long.toString(sumTime)); // ms
            mXML.endTag("", "duration");

            mXML.startTag("", "distance");
            mXML.text(Double.toString(sumDist / 1000.0d)); // km
            mXML.endTag("", "distance");

            mXML.startTag("", "pace");
            double pace = 0;
            if (last != null && last.hasSpeed() && last.getSpeed() != 0) {
                pace = 1000.0d / last.getSpeed();
            } else {
                if (sumDist != 0)
                    pace = sumTime / sumDist;
            }
            mXML.text(Long.toString(Math.round(1000.0d * pace)));
            mXML.endTag("", "pace");

            mXML.startTag("", "bpm");
            mXML.text(Integer.toString(hrVal));
            mXML.endTag("", "bpm");

            mXML.endTag("", string);
        }
        c.close();
    }

    abstract class Emitter {

        public abstract void emit(Pos p, Vector<Pos> posHist, Vector<Location> hist)
                throws Exception;
    }

    class SnapshotList extends Emitter
    {
        String event = null;

        public SnapshotList() {
        }

        public SnapshotList(final String evnt) {
            event = evnt;
        }

        @Override
        public void emit(final Pos p, final Vector<Pos> posHist, final Vector<Location> hist)
                throws Exception {
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
            double deltaHR = p.sumHR;
            if (!posHist.isEmpty()) {
                deltaTime -= posHist.lastElement().sumTime;
                deltaDist -= posHist.lastElement().sumDistance;
                deltaHR -= posHist.lastElement().sumHR;
            }
            double pace = 0;
            if (deltaDist != 0) {
                pace = 1000d * deltaTime / deltaDist;
            }
            mXML.text(Long.toString(Math.round(pace)));
            mXML.endTag("", "pace");

            if (deltaHR > 0 && deltaTime > 0) {
                double avgHR = deltaHR / deltaTime;
                mXML.startTag("", "bpm");
                mXML.text(Long.toString(Math.round(avgHR)));
                mXML.endTag("", "bpm");
            }

            mXML.endTag("", "snapShot");
        }
    }

    class ExtendedData extends Emitter
    {
        Dim d = null;
        final StringBuffer buf = new StringBuffer();

        public ExtendedData(final Dim dim) {
            d = dim;
        }

        @Override
        public void emit(final Pos p, final Vector<Pos> posHist, final Vector<Location> hist)
                throws Exception {
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
            } else if (d == Dim.HR) {
                double deltaTime = p.sumTime;
                double deltaHR = p.sumHR;
                if (!posHist.isEmpty()) {
                    deltaTime -= posHist.lastElement().sumTime;
                    deltaHR -= posHist.lastElement().sumHR;
                }
                double avgHR = 0;
                if (deltaTime != 0) {
                    avgHR = deltaHR / deltaTime;
                }
                buf.append(' ');
                buf.append(Long.toString(Math.round(avgHR)));
            }
        }
    }

    class Pos {
        public Pos() {
        }

        public Pos(final Pos p) {
            sumTime = p.sumTime;
            sumDistance = p.sumDistance;
            sumHR = p.sumHR;
        }

        long sumTime = 0;
        double sumDistance = 0;
        long sumHR = 0;
    }

    private void emitList(final long activityId, final Dim d, final double add, final Emitter out)
            throws Exception {
        double first = add;
        final String[] pColumns = {
                DB.LOCATION.LAP, // 0
                DB.LOCATION.TIME, // 1
                DB.LOCATION.LATITUDE, // 2
                DB.LOCATION.LONGITUDE,// 3
                DB.LOCATION.ALTITUDE, // 4
                DB.LOCATION.TYPE, // 5
                DB.LOCATION.HR
        }; // 6

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

                    long hr = 0;
                    if (!c.isNull(6)) {
                        hr = c.getLong(6);
                    }

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
                        p.sumHR += diffTime * hr;
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
                                || (!posHist.isEmpty() && p.sumTime > posHist.lastElement().sumTime && p.sumDistance > posHist
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
                    p.sumHR += hr * deltaTime;
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
     * @see <url>http://www.movable-type.co.uk/scripts/latlong.html</url>
     */
    public static void move(final Location l, final double bearing, final double dist) {
        final double d = dist / 1000.0; // in km
        final double lat1 = Math.toRadians(l.getLatitude());
        final double lon1 = Math.toRadians(l.getLongitude());
        final double brng = Math.toRadians(bearing);
        final double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d / R) +
                Math.cos(lat1) * Math.sin(dist / R) * Math.cos(brng));
        final double lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(d / R) * Math.cos(lat1),
                Math.cos(d / R) - Math.sin(lat1) * Math.sin(lat2));

        l.setLatitude(Math.toDegrees(lat2));
        l.setLongitude(Math.toDegrees(lon2));
    }
}
