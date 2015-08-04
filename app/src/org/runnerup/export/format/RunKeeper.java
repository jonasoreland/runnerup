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

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.RunKeeperSynchronizer;
import org.runnerup.util.JsonWriter;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/**
 * @author jonas.oreland@gmail.com
 * 
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunKeeper {

    long mID = 0;
    SQLiteDatabase mDB = null;

    public RunKeeper(SQLiteDatabase db) {
        mDB = db;
    }

    static String formatTime(long time) {
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US)
                .format(new Date(time));
    }

    public void export(long activityId, Writer writer) throws IOException {

        ActivityEntity ae = new ActivityEntity();
        ae.readByPrimaryKey(mDB, activityId);
        long startTime = ae.getStartTime();
        double distance = ae.getDistance();
        long duration = ae.getTime();
        String comment = ae.getComment();
        try {
            JsonWriter w = new JsonWriter(writer);
            w.beginObject();
            Sport s = Sport.valueOf(ae.getSport());
            if (!RunKeeperSynchronizer.sport2runkeeperMap.containsKey(s)) {
                s = Sport.OTHER;
            }
            w.name("type").value(RunKeeperSynchronizer.sport2runkeeperMap.get(s));
            w.name("equipment").value("None");
            w.name("start_time").value(formatTime(startTime * 1000));
            w.name("total_distance").value(distance);
            w.name("duration").value(duration);
            if (comment != null && comment.length() > 0) {
                w.name("notes").value(comment);
            }
            //it seems that upload fails if writting an empty array...
            if (ae.getMaxHr()!=null) {
                w.name("heart_rate");
                w.beginArray();
                exportHeartRate(activityId, startTime, w);
                w.endArray();
            }
            exportPath("path", activityId, startTime, w);
            w.name("post_to_facebook").value(false);
            w.name("post_to_twitter").value(false);
            w.endObject();
        } catch (IOException e) {
            throw e;
        }
    }

    private void exportHeartRate(long activityId, long startTime, JsonWriter w)
            throws IOException {
        String[] pColumns = {
                DB.LOCATION.TIME, DB.LOCATION.HR
        };
        Cursor cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null,
                null);
        if (cursor.moveToFirst()) {
            startTime = cursor.getLong(0);
            do {
                if (!cursor.isNull(1)) {
                    w.beginObject();
                    w.name("timestamp").value(
                            (cursor.getLong(0) - startTime) / 1000);
                    w.name("heart_rate").value(Integer.toString(cursor.getInt(1)));
                    w.endObject();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    private void exportPath(String name, long activityId, long startTime, JsonWriter w)
            throws IOException {
        String[] pColumns = {
                DB.LOCATION.TIME, DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE, DB.LOCATION.ALTITUDE, DB.LOCATION.TYPE
        };
        Cursor cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null,
                null);
        if (cursor.moveToFirst()) {
            w.name(name);
            w.beginArray();
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
            w.endArray();
        }
        cursor.close();
    }
}
