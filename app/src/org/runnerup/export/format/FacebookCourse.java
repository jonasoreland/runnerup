/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.util.Formatter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author jonas.oreland@gmail.com
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class FacebookCourse {

    long mID = 0;
    SQLiteDatabase mDB = null;
    Formatter formatter = null;
    final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSSZ", Locale.getDefault());

    public FacebookCourse(Context ctx, SQLiteDatabase db) {
        mDB = db;
        formatter = new Formatter(ctx);
    }

    String formatTime(long time) {
        return dateFormat.format(new Date(time));
    }

    JSONObject pace(double distance, double duration) throws JSONException {
        if (distance != 0)
            return pace(duration / distance);
        return null;
    }

    JSONObject pace(double val) throws JSONException {
        if (formatter.getUnitMeters() == Formatter.km_meters) {
            return new JSONObject().put("value", val).put("units", "s/m");
        } else {
            return new JSONObject().put("value", val).put("units", " s/ft");
        }
    }

    public JSONObject export(long activityId, boolean showTrail, JSONObject runObj)
            throws IOException, JSONException {

        final String[] aColumns = {
                DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME,
                DB.ACTIVITY.SPORT
        };
        Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + activityId, null, null, null, null);
        cursor.moveToFirst();

        if (runObj != null) {
            runObj.put("sport", cursor.getInt(5));
            runObj.put("startTime", cursor.getLong(2));
            runObj.put("endTime", cursor.getLong(2) + cursor.getLong(4));
            if (!cursor.isNull(1))
                runObj.put("comment", cursor.getString(1));
        }

        JSONObject obj = new JSONObject();
        double distance = cursor.getDouble(3);
        long duration = cursor.getLong(4);
        cursor.close();

        double unitMeters = formatter.getUnitMeters();
        if (distance < unitMeters) {
            obj.put("distance",
                    new JSONObject().put("value", distance).put("units", "m"));
        } else {
            final int decimals = 1;
            double base = distance / unitMeters;
            double val = Formatter.round(base, decimals);
            obj.put("distance",
                    new JSONObject().put("value", val).put("units",
                            formatter.getUnitString()));
        }

        obj.put("duration",
                new JSONObject().put("value", duration).put("units", "s"));
        if (distance != 0) {
            obj.put("pace", pace(distance, duration));
        }

        if (showTrail) {
            JSONArray trail = trail(activityId);
            if (trail != null)
                obj.put("metrics", trail);
        }

        return obj;
    }

    private JSONArray trail(long activityId) throws JSONException {
        final String cols[] = {
                DB.LOCATION.TYPE, DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE, DB.LOCATION.TIME, DB.LOCATION.SPEED
        };
        Cursor c = mDB.query(DB.LOCATION.TABLE, cols, DB.LOCATION.ACTIVITY
                + " = " + activityId, null, null, null, null);
        if (c.moveToFirst()) {
            Location prev = null, last = null;
            double sumDist = 0;
            long sumTime = 0;
            double accTime = 0;
            final double period = 30;
            JSONArray arr = new JSONArray();
            do {
                switch (c.getInt(0)) {
                    case DB.LOCATION.TYPE_START:
                    case DB.LOCATION.TYPE_RESUME:
                        last = new Location("Dill");
                        last.setLatitude(c.getDouble(1));
                        last.setLongitude(c.getDouble(2));
                        last.setTime(c.getLong(3));
                        accTime = period * 1000; // always emit first point
                                                 // start/resume
                        break;
                    case DB.LOCATION.TYPE_END:
                        accTime = period * 1000; // always emit last point
                    case DB.LOCATION.TYPE_GPS:
                    case DB.LOCATION.TYPE_PAUSE:
                        Location l = new Location("Sill");
                        l.setLatitude(c.getDouble(1));
                        l.setLongitude(c.getDouble(2));
                        l.setTime(c.getLong(3));
                        if (!c.isNull(4))
                            l.setSpeed(c.getFloat(4));
                        if (last != null) {
                            sumDist += l.distanceTo(last);
                            sumTime += l.getTime() - last.getTime();
                            accTime += l.getTime() - last.getTime();
                        }
                        prev = last;
                        last = l;
                }
                if (Math.round(accTime / 1000) >= period) {
                    arr.put(point(prev, last, sumTime, sumDist));
                    accTime -= period * 1000;
                }
            } while (c.moveToNext());
            c.close();
            return arr;
        }
        c.close();
        return null;
    }

    private JSONObject point(Location prev, Location last, long sumTime, double sumDist)
            throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("location",
                new JSONObject().put("latitude", last.getLatitude()).put("longitude",
                        last.getLongitude()));
        obj.put("distance", new JSONObject().put("value", sumDist / 1000.0).put("units", "km"));
        obj.put("timestamp", formatTime(last.getTime()));
        if (last.hasSpeed() && last.getSpeed() > 0) {
            obj.put("pace", pace(1.0f / last.getSpeed()));
        }
        return obj;
    }
}
