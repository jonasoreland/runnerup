package org.runnerup.db.entities;

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

import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;

public class ActivityMerger implements Constants {
    public void merge(SQLiteDatabase db, long activityId) {
        assert(this.canMerge(db, activityId));
        long previousActivityId = this.previousActivityId(db, activityId);

        // assign all older laps to the current activity
        db.execSQL("UPDATE " + DB.LAP.TABLE + " SET " + DB.LAP.ACTIVITY + " = " + activityId +
                   " WHERE " + DB.LAP.ACTIVITY + " = " + previousActivityId);
        // update current activity start time with older activity start time
        db.execSQL("UPDATE " + DB.ACTIVITY.TABLE + " SET " + DB.ACTIVITY.START_TIME + " = " + startTime(db, previousActivityId) +
                   " WHERE _id = " + activityId);
        // clean up
        db.delete(DB.ACTIVITY.TABLE, "_id = " + previousActivityId, null);

        new ActivityCleaner().recompute(db, activityId);
    }

    public boolean canMerge(SQLiteDatabase db, long activityId) {
        // previous activity must exist and be the same activity type
        // there is a deleted flag in the DB, but it doesn't seem to be used?
        // I deleted a previous activity (id = 50) and I successfully get the
        // (undeleted) previous activity (id = 49)...
        try {
            long previousActivityId = this.previousActivityId(db, activityId);
            long type = activityType(db, activityId);
            long previousType = activityType(db, previousActivityId);
            return type == previousType;
        } catch (Exception e) {
            return false;
        }
    }

    private long activityType(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT type FROM " + DB.ACTIVITY.TABLE +
                                   " WHERE _id = " + activityId).simpleQueryForLong();
    }

    private long previousActivityId(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE +
                " WHERE _id < " + activityId).simpleQueryForLong();
    }

    private long startTime(SQLiteDatabase db, long activityId) {
        return db.compileStatement("SELECT " + DB.ACTIVITY.START_TIME + " FROM " + DB.ACTIVITY.TABLE +
                " WHERE _id = " + activityId).simpleQueryForLong();
    }
}
