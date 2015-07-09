package org.runnerup.db;

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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;


public class ActivityMerger implements Constants {
    public static boolean canMerge(SQLiteDatabase db, long activityId) {
        // previous activity must exist and be the same activity type
        // there is a deleted flag in the DB, but it doesn't seem to be used?
        // I deleted a previous activity (id = 50) and I successfully get the
        // (undeleted) previous activity (id = 49)...
        try {
            long previousActivityId = previousActivityId(db, activityId);
            long type = activityType(db, activityId);
            long previousType = activityType(db, previousActivityId);
            return type == previousType;
        } catch (Exception e) {
            return false;
        }
    }

    public static void merge(SQLiteDatabase db, long activityId) {
        long previousActivityId = previousActivityId(db, activityId);

        // update the 3 activity tables - LOCATION, LAP and ACTIVITY
        //  through this operation we re-order the new laps based on the older laps
        //  and move everything to the current activity ID
        updateLocation(db, activityId, previousActivityId);
        updateLap(db, activityId, previousActivityId);
        updateActivity(db, activityId, previousActivityId);

        // cleanup
        DBHelper.deleteActivity(db, previousActivityId);
        new ActivityCleaner().recompute(db, activityId);
    }

    private static void updateActivity(SQLiteDatabase db, long activityId, long previousActivityId) {
        // update current activity start time with older activity start time
        db.execSQL("UPDATE " + DB.ACTIVITY.TABLE + " SET " + DB.ACTIVITY.START_TIME + " = " + startTime(db, previousActivityId) +
                " WHERE _id = " + activityId);
    }

    private static void updateLap(SQLiteDatabase db, long activityId, long previousActivityId) {
        // # VERIFY THAT time IS UPDATED BY recompute IF NECESSARY

        // newer activity lap numbers should continue where previousActivity laps stopped
        long nextLap = maxLap(db, previousActivityId) + 1;
        db.execSQL("UPDATE " + DB.LAP.TABLE + " SET " + DB.LAP.LAP + "=" + DB.LAP.LAP + "+" + nextLap +
                " WHERE " + DB.LAP.ACTIVITY + "=" + activityId);
        // assign all older laps to the current activity
        db.execSQL("UPDATE " + DB.LAP.TABLE + " SET " + DB.LAP.ACTIVITY + " = " + activityId +
                " WHERE " + DB.LAP.ACTIVITY + " = " + previousActivityId);
    }

    private static void updateLocation(SQLiteDatabase db, long activityId, long previousActivityId) {
        // location: set middle start/end type to pause/resume
        ContentValues values = new ContentValues();
        values.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_PAUSE);
        db.update(DB.LOCATION.TABLE, values, DB.LOCATION.TYPE + "=" + DB.LOCATION.TYPE_END + " AND " + DB.LOCATION.ACTIVITY + "=" + previousActivityId, null);
        values = new ContentValues();
        values.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_RESUME);
        db.update(DB.LOCATION.TABLE, values, DB.LOCATION.TYPE + "=" + DB.LOCATION.TYPE_START + " AND " + DB.LOCATION.ACTIVITY + "=" + activityId, null);

        // location: newer lap numbers should continue where previousActivity laps stopped
        long nextLap = maxLap(db, previousActivityId) + 1;
        db.execSQL("UPDATE " + DB.LOCATION.TABLE + " SET " + DB.LOCATION.LAP + "=" + DB.LOCATION.LAP + "+" + nextLap +
                   " WHERE " + DB.LOCATION.ACTIVITY + "=" + activityId);

        // location: assign newer activityId to older locations
        values = new ContentValues();
        values.put(DB.LOCATION.ACTIVITY, activityId);
        db.update(DB.LOCATION.TABLE, values, DB.LOCATION.ACTIVITY+"="+previousActivityId, null);
    }

    private static long activityType(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT type FROM " + DB.ACTIVITY.TABLE +
                                   " WHERE _id = " + activityId).simpleQueryForLong();
    }

    private static long previousActivityId(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE +
                " WHERE _id < " + activityId).simpleQueryForLong();
    }

    private static long startTime(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT " + DB.ACTIVITY.START_TIME + " FROM " + DB.ACTIVITY.TABLE +
                " WHERE _id = " + activityId).simpleQueryForLong();
    }

    private static long maxLap(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT MAX(" + DB.LAP.LAP + ") FROM " + DB.LAP.TABLE +
                " WHERE " + DB.LAP.ACTIVITY + "=" + activityId).simpleQueryForLong();
    }
}
