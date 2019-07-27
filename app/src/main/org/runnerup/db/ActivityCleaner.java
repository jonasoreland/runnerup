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

package org.runnerup.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;


public class ActivityCleaner implements Constants {
    private long _totalSumHr = 0;
    private int _totalCount = 0;
    private int _totalMaxHr = 0;
    private long _totalTime = 0;
    private double _totalDistance = 0;
    private Location _lastLocation = null;
    private boolean _isActive = false;

    /**
     * recompute laps aggregates based on locations
     */
    private void recomputeLaps(SQLiteDatabase db, long activityId) {
        final String[] cols = new String[] {
                DB.LAP.LAP
        };

        ArrayList<Long> laps = new ArrayList<>();
        Cursor c = db.query(DB.LAP.TABLE, cols, DB.LAP.ACTIVITY + " = " + activityId,
                null, null, null, "_id", null);
        if (c.moveToFirst()) {
            do {
                laps.add(c.getLong(0));
            } while (c.moveToNext());
        }
        c.close();

        for (long lap : laps) {
            recomputeLap(db, activityId, lap);
        }
    }

    /**
     * recompute a lap aggregate based on locations
     */
    private void recomputeLap(SQLiteDatabase db, long activityId, long lap) {
        long sum_time = 0;
        long sum_hr = 0;
        double sum_distance = 0;
        int count = 0;
        int max_hr = 0;
        final String[] cols = new String[] {
                DB.LOCATION.TIME,
                DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE,
                DB.LOCATION.TYPE,
                DB.LOCATION.HR,
                //DB.LOCATION.CADENCE,
                //DB.LOCATION.TEMPERATURE,
                //DB.LOCATION.PRESSURE,
                "_id"
        };

        Cursor c = db.query(DB.LOCATION.TABLE, cols, DB.LOCATION.ACTIVITY + " = " + activityId
                + " and " + DB.LOCATION.LAP + " = " + lap,
                null, null, null, "_id", null);
        if (c.moveToFirst()) {
            do {
                Location l = new Location("Dill poh");
                l.setTime(c.getLong(0));
                l.setLatitude(c.getDouble(1));
                l.setLongitude(c.getDouble(2));
                l.setProvider("" + c.getLong(3));

                int type = c.getInt(3);
                switch (type) {
                    case DB.LOCATION.TYPE_START:
                    case DB.LOCATION.TYPE_RESUME:
                        _lastLocation = l;
                        _isActive = true;
                        break;
                    case DB.LOCATION.TYPE_END:
                    case DB.LOCATION.TYPE_PAUSE:
                    case DB.LOCATION.TYPE_GPS:
                        if (_lastLocation == null) {
                            _lastLocation = l;
                            break;
                        }

                        if (_isActive) {
                            double diffDist = l.distanceTo(_lastLocation);
                            sum_distance += diffDist;
                            _totalDistance += diffDist;

                            long diffTime = l.getTime() - _lastLocation.getTime();
                            sum_time += diffTime;
                            _totalTime += diffTime;

                            int hr = c.getInt(4);
                            sum_hr += hr;
                            max_hr = Math.max(max_hr, hr);
                            _totalMaxHr = Math.max(_totalMaxHr, hr);
                            count++;
                            _totalCount++;
                            _totalSumHr += hr;
                        }
                        _lastLocation = l;
                        if (type == DB.LOCATION.TYPE_PAUSE || type == DB.LOCATION.TYPE_END) {
                            _isActive = false;
                        }
                        break;
                }
            } while (c.moveToNext());
        }
        c.close();

        ContentValues tmp = new ContentValues();
        tmp.put(DB.LAP.DISTANCE, sum_distance);
        tmp.put(DB.LAP.TIME, Math.round(sum_time / 1000.0d));
        if (sum_hr > 0) {
            long hr = Math.round(sum_hr / (double)count);
            tmp.put(DB.LAP.AVG_HR, hr);
            tmp.put(DB.LAP.MAX_HR, max_hr);
        }
        db.update(DB.LAP.TABLE, tmp, DB.LAP.ACTIVITY + " = " + activityId + " and " + DB.LAP.LAP
                + " = " + lap, null);
    }

    /**
     * recompute an activity summary based on laps
     */
    private void recomputeSummary(SQLiteDatabase db, long activityId) {
        ContentValues tmp = new ContentValues();
        if (_totalSumHr > 0) {
            long hr = Math.round(_totalSumHr / (double)_totalCount);
            tmp.put(DB.ACTIVITY.AVG_HR, hr);
            tmp.put(DB.ACTIVITY.MAX_HR, _totalMaxHr);
        }
        tmp.put(DB.ACTIVITY.DISTANCE, _totalDistance);
        tmp.put(DB.ACTIVITY.TIME, Math.round(_totalTime /1000.0d)); // also used as a flag for conditionalRecompute

        db.update(DB.ACTIVITY.TABLE, tmp, "_id = " + activityId, null);
    }

    public void conditionalRecompute(SQLiteDatabase db){
        // get last activity
        long id = db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE).simpleQueryForLong();

        // check its TIME field - recompute if it isn't set
        String[] cols = new String[]{DB.ACTIVITY.TIME};
        Cursor c = db.query(DB.ACTIVITY.TABLE, cols, "_id = " + id, null, null, null, null);
        if (c.moveToFirst()) {
            if (c.isNull(0)) {
                recompute(db, id);
            }
        }
        c.close();
    }

    public void recompute(SQLiteDatabase db, long activityId) {
        // Init variables used over laps and communicating with summary
        _totalTime = 0;
        _totalDistance = 0;
        _totalSumHr = 0;
        _totalCount = 0;
        _totalMaxHr = 0;
        _lastLocation = null;
        _isActive = false;

        recomputeLaps(db, activityId);
        recomputeSummary(db, activityId);
    }

    public static void trim(SQLiteDatabase db, long activityId) {
        final String[] cols = new String[] {
            DB.LAP.LAP
        };

        ArrayList<Long> laps = new ArrayList<>();
        Cursor c = db.query(DB.LOCATION.LAP, cols, DB.LAP.ACTIVITY + " = "
                + activityId, null, null, null, "_id", null);
        if (c.moveToFirst()) {
            do {
                laps.add(c.getLong(0));
            } while (c.moveToNext());
        }
        c.close();

        for (long lap : laps) {
            int res = trimLap(db, activityId, lap);
            Log.e("ActivityCleaner", "lap " + lap + " removed " + res + " locations");
        }
    }

    private static final float MIN_DISTANCE = 2f;

    private static int trimLap(SQLiteDatabase db, long activityId, long lap) {
        int cnt = 0;
        final String[] cols = new String[] {
                DB.LOCATION.TIME,
                DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE,
                DB.LOCATION.TYPE,
                "_id"
        };

        Cursor c = db.query(DB.LOCATION.TABLE, cols, DB.LOCATION.ACTIVITY + " = " + activityId
                + " and " + DB.LOCATION.LAP + " = " + lap,
                null, null, null, "_id", null);
        if (c.moveToFirst()) {
            Location p[] = {
                    null, null
            };
            do {
                Location l = new Location("Dill poh");
                l.setTime(c.getLong(0));
                l.setLatitude(c.getDouble(1));
                l.setLongitude(c.getDouble(2));
                l.setProvider("" + c.getLong(3));

                int type = c.getInt(3);
                switch (type) {
                    case DB.LOCATION.TYPE_START:
                    case DB.LOCATION.TYPE_RESUME:
                        p[0] = l;
                        p[1] = null;
                        break;
                    case DB.LOCATION.TYPE_END:
                    case DB.LOCATION.TYPE_PAUSE:
                    case DB.LOCATION.TYPE_GPS:
                        if (p[0] == null) {
                            p[0] = l;
                            p[1] = null;
                            break;
                        } else if (p[1] == null) {
                            p[1] = l;
                        } else {
                            float d1 = p[0].distanceTo(p[1]);
                            float d2 = p[0].distanceTo(l);
                            if (Math.abs(d1 - d2) <= MIN_DISTANCE) {
                                // p[1] is redundant...prune it
                                p[1] = l;
                                cnt++;
                            } else {
                                p[0] = p[1];
                                p[1] = null;
                            }
                        }
                        break;
                }
            } while (c.moveToNext());
        }
        c.close();
        return cnt;
    }

    /**
     * Wrapper needed by simplifyPath to use Location as 2D point.
     * Extracts (lat,long) from the Location class needed by com.goebl.simplify.simplify for (x,y).
     *
     * The functions return a multiple of the lat/long values to avoid deltas < 1 between points.
     *
     * https://github.com/hgoebl/simplify-java
     */
    private static PointExtractor<Location> locationPointExtractor = new PointExtractor<Location>() {
        @Override
        public double getX(Location point) {
            return point.getLatitude() * 1e6;
        }

        @Override
        public double getY(Location point) {
            return point.getLongitude() * 1e6;
        }
    };

    /**
     * Simplifies the path of an activity,
     * i.e., deletes locations below a specific tolerance to reduce the resolution.
     *
     * We use only 2D because we cannot mix degrees (lat,long) with meters (altitude),
     * regarding the tolerance of simplify.
     * Conversion of lat and long to meters is not necessary to simplify the path.
     *
     * https://github.com/hgoebl/simplify-java
     *
     * @param db Database.
     * @param activityId ID of the activity to simplify.
     * @param toleranceMeters Squared tolerance in meters.
     */
    public static void simplifyPath(SQLiteDatabase db, long activityId, double toleranceMeters) {
        // columns to query from the database, table "LOCATION"
        String[] pColumns = {
                "_id", DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE
        };

        // get table from database
        Cursor c = db.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId,
                null, null, null, DB.LOCATION.TIME);

        // data base rows to lists
        ArrayList<Location> locations = new ArrayList<>();  /** List of the activity's locations (lat,long). */
        if (c.moveToFirst()) {
            do {
                // save ID of the location entry (row ID) as provider
                Location l = new Location(String.format("%d", c.getInt(0)));
                // get point data
                l.setLatitude(c.getDouble(1));
                l.setLongitude(c.getDouble(2));
                locations.add(l);
            } while (c.moveToNext());
        }
        c.close();

        // squared tolerance in meters has to be transformed to tolerance in degrees
        // get meters per degree with android.location.Location.distanceTo
        Location zeroDegrees = new Location("Dill poh");
        zeroDegrees.setLatitude(0);
        zeroDegrees.setLongitude(0);
        Location oneDegrees = new Location(zeroDegrees);
        oneDegrees.setLatitude(1);
        // tolerance in meters / meters per degree
        double toleranceDeg = toleranceMeters / zeroDegrees.distanceTo(oneDegrees);

        // create an instance of the simplifier (empty array needed by List.toArray)
        Location[] sampleArray = new Location[0];
        Simplify<Location> simplify = new Simplify<Location>(sampleArray, locationPointExtractor);
        // removes unnecessary intermediate points (note this does not change lat/long values!)
        Location[] simplifiedLocations = simplify.simplify(locations.toArray(sampleArray),
                toleranceDeg*1e6, false);

        // remove the locations (skipped by simplify) from the database
        ArrayList<String> ids = new ArrayList<>();  // IDs of all the activity's locations
        ArrayList<String> simplifiedIDs = new ArrayList<>();  // IDs to remove from location table
        for (Location l: locations) {
            ids.add(l.getProvider());
        }
        for (Location l: simplifiedLocations) {
            simplifiedIDs.add(l.getProvider());
        }
        ids.removeAll(simplifiedIDs); // keep IDs to remove from table
        String strIDs = TextUtils.join(",", ids);
        db.execSQL("delete from " + DB.LOCATION.TABLE
                + " where _id in (" + strIDs + ")"
                + " and " + DB.LOCATION.TYPE + " = " + DB.LOCATION.TYPE_GPS);
    }
}
