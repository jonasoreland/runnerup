/*
 * Copyright (C) 2021 jonas.oreland@gmail.com
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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathSimplifier;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Summary {
    private final SQLiteDatabase mDB;
    private final SimpleDateFormat simpleDateFormat;
    private final Formatter formatter;

    public Summary(SQLiteDatabase mDB, Formatter formatter) {
        this.mDB = mDB;
        this.simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy hh:mm aa z", Locale.US);
        this.formatter = formatter;
    }

    public String Summary(long activityId) throws IOException {
        String[] fields = new String[]{
                "_id",
                DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE,
                DB.ACTIVITY.TIME,
                DB.ACTIVITY.SPORT
        };

        String[] args = { Long.toString(activityId) };
        try (Cursor c = mDB.query(DB.ACTIVITY.TABLE, fields, "_id=?", args, null, null, null)) {
            if (c.moveToFirst()) {
                Long startTime = c.getLong(1);
                Long distance = c.getLong(2);
                Long durationTime = c.getLong(3);
                Sport sport = Sport.valueOf(c.getInt(4));

                Date t = new Date(startTime * 1000);
                String startStr = simpleDateFormat.format(t);
                String distStr = formatter.format(Formatter.Format.TXT, Dimension.DISTANCE, distance);
                distStr += " " + formatter.getUnitString();
                String durStr = formatter.format(Formatter.Format.TXT_LONG, Dimension.TIME, durationTime);
                String paceStr = formatter.formatPaceSpeed(Formatter.Format.TXT_LONG, Double.valueOf(distance) / durationTime);

                distStr = distStr.replaceAll("\\s+", "");
                durStr = durStr.replaceAll("\\s+", "");
                paceStr = paceStr.replaceAll("\\s+", "");
                return sport.name() + ": " + distStr + " at " + startStr + " for " + durStr + ". Pace: " + paceStr;
            }
        }

        return "";
    }
}
