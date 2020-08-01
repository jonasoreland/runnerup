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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathCursor;
import org.runnerup.db.PathSimplifier;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.db.entities.LapEntity;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.export.RunKeeperSynchronizer;
import org.runnerup.util.JsonWriter;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * @author jonas.oreland@gmail.com
 * 
 */


public class RunKeeper {

    private final SQLiteDatabase mDB;
    private final PathSimplifier simplifier;

    public RunKeeper(SQLiteDatabase db, PathSimplifier simplifier) {
        mDB = db;
        this.simplifier = simplifier;
    }

    private static String formatTime(long time) {
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
                exportHeartRate(activityId, w);
                w.endArray();
            }
            exportPath("path", activityId, w);
            w.name("post_to_facebook").value(false);
            w.name("post_to_twitter").value(false);
            w.endObject();
        } catch (IOException e) {
            throw e;
        }
    }

    private void exportHeartRate(long activityId, JsonWriter w)
            throws IOException {
        String[] pColumns = {
                DB.LOCATION.TIME, DB.LOCATION.HR
        };
        Cursor cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null,
                null);
        if (cursor.moveToFirst()) {
            long startTime = cursor.getLong(0);
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

    private void exportPath(String name, long activityId, JsonWriter w)
            throws IOException {
        String[] pColumns = {
                DB.LOCATION.TIME, DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE, DB.LOCATION.ALTITUDE, DB.LOCATION.TYPE,
                DB.PRIMARY_KEY
        };
        PathCursor cursor = new PathCursor(mDB, activityId, pColumns, 5, simplifier);
        if (cursor.moveToFirst()) {
            w.name(name);
            w.beginArray();
            long startTime = cursor.getLong(0);
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

    public static ActivityEntity parseToActivity(JSONObject response, double unitMeters) throws JSONException {
        ActivityEntity newActivity = new ActivityEntity();
        newActivity.setSport(RunKeeperSynchronizer.runkeeper2sportMap.get(response.getString("type")).getDbValue());
        if (response.has("notes")) {
            newActivity.setComment(response.getString("notes"));
        }
        newActivity.setTime((long) Float.parseFloat(response.getString("duration")));
        newActivity.setDistance(Double.parseDouble(response.getString("total_distance")));

        String startTime = response.getString("start_time");
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        try {
            newActivity.setStartTime(format.parse(startTime));
        } catch (ParseException e) {
            Log.e(Constants.LOG, e.getMessage());
            return null;
        }

        List<LapEntity> laps = new ArrayList<>();
        List<LocationEntity> locations = new ArrayList<>();

        JSONArray distance = response.getJSONArray("distance");
        JSONArray path = response.getJSONArray("path");
        JSONArray hr = response.getJSONArray("heart_rate");

        SortedMap<Long, HashMap<String, String>> pointsValueMap = createPointsMap(distance, path, hr);
        Iterator<Map.Entry<Long, HashMap<String, String>>> points = pointsValueMap.entrySet().iterator();

        //lap hr
        int maxHr = 0;
        int sumHr = 0;
        int count = 0;
        //point speed
        long time = 0;
        float meters = 0.0f;
        //activity hr
        int maxHrOverall = 0;
        int sumHrOverall = 0;
        int countOverall = 0;

        while (points.hasNext()) {
            Map.Entry<Long, HashMap<String, String>> timePoint = points.next();
            HashMap<String, String> values = timePoint.getValue();

            LocationEntity lv = new LocationEntity();
            lv.setActivityId(newActivity.getId());
            lv.setTime(TimeUnit.SECONDS.toMillis(newActivity.getStartTime()) + timePoint.getKey());

            String dist = values.get("distance");
            if (dist == null) {
                continue;
            }
            String lat = values.get("latitude");
            String lon = values.get("longitude");
            String alt = values.get("altitude");
            String heart = values.get("heart_rate");
            String type = values.get("type");

            if (lat == null || lon == null) {
                continue;
            } else {
                lv.setLatitude(Double.valueOf(lat));
                lv.setLongitude(Double.valueOf(lon));
            }
            if (alt != null) {
                lv.setAltitude(Double.valueOf(alt));
            }

            if (pointsValueMap.firstKey().equals(timePoint.getKey())) {
                lv.setType(DB.LOCATION.TYPE_START);
            } else if (!points.hasNext()) {
                lv.setType(DB.LOCATION.TYPE_END);
            } else if (type != null) {
                lv.setType(RunKeeperSynchronizer.POINT_TYPE.get(type));
            }
            // lap and activity max and avg hr
            if (heart != null) {
                lv.setHr(Integer.valueOf(heart));
                maxHr = Math.max(maxHr, lv.getHr());
                maxHrOverall = Math.max(maxHrOverall, lv.getHr());
                sumHr += lv.getHr();
                sumHrOverall += lv.getHr();
                count++;
                countOverall++;
            }

            meters = Float.parseFloat(dist) - meters;
            time = timePoint.getKey() - time;
            if (time > 0) {
                float speed = meters / (float)TimeUnit.MILLISECONDS.toSeconds(time);
                BigDecimal s = new BigDecimal(speed);
                s = s.setScale(2, BigDecimal.ROUND_UP);
                lv.setSpeed(s.doubleValue());
            }

            // create lap if distance greater than configured lap distance

            if (Float.parseFloat(dist) >= unitMeters * laps.size()) {
                LapEntity newLap = new LapEntity();
                newLap.setLap(laps.size());
                newLap.setDistance(Double.valueOf(dist));
                newLap.setTime((int) TimeUnit.MILLISECONDS.toSeconds(timePoint.getKey()));
                newLap.setActivityId(newActivity.getId());
                laps.add(newLap);

                // update previous lap with duration and distance
                if (laps.size() > 1) {
                    LapEntity previousLap = laps.get(laps.size() - 2);
                    previousLap.setDistance(Float.parseFloat(dist) - previousLap.getDistance());
                    previousLap.setTime((int) TimeUnit.MILLISECONDS.toSeconds(timePoint.getKey()) - previousLap.getTime());

                    if (hr != null && hr.length() > 0) {
                        previousLap.setMaxHr(maxHr);
                        previousLap.setAvgHr(sumHr / count);
                    }
                    maxHr = 0;
                    sumHr = 0;
                    count = 0;
                }
            }
            // update last lap with duration and distance
            if (!points.hasNext()) {
                LapEntity previousLap = laps.get(laps.size() - 1);
                previousLap.setDistance(Float.parseFloat(dist) - previousLap.getDistance());
                previousLap.setTime((int) TimeUnit.MILLISECONDS.toSeconds(timePoint.getKey()) - previousLap.getTime());

                if (hr != null && hr.length() > 0) {
                    previousLap.setMaxHr(maxHr);
                    previousLap.setAvgHr(sumHr / count);
                }
            }

            lv.setLap(laps.size()-1);

            locations.add(lv);
        }
        // calculate avg and max hr
        // update the activity
        newActivity.setMaxHr(maxHrOverall);
        if (countOverall > 0) {
            newActivity.setAvgHr(sumHrOverall / countOverall);
        }

        newActivity.putPoints(locations);
        newActivity.putLaps(laps);

        return newActivity;
    }

    private static SortedMap<Long, HashMap<String, String>> createPointsMap(JSONArray distance, JSONArray path, JSONArray hr) throws JSONException {
        SortedMap<Long, HashMap<String, String>> result = new TreeMap<>();

        if (distance != null && distance.length() > 0) {
            for (int i = 0; i < distance.length(); i++) {
                JSONObject o = distance.getJSONObject(i);
                Long key = TimeUnit.SECONDS.toMillis((long) Float.parseFloat(o.getString("timestamp")));
                HashMap<String, String> value = new HashMap<>();
                String valueMapKey = "distance";
                String valueMapValue = o.getString(valueMapKey);
                value.put(valueMapKey, valueMapValue);
                result.put(key, value);
            }
        }

        if (path != null && path.length() > 0) {
            for (int i = 0; i < path.length(); i++) {
                JSONObject o = path.getJSONObject(i);
                Long key = TimeUnit.SECONDS.toMillis((long)Float.parseFloat(o.getString("timestamp")));
                HashMap<String, String> value = result.get(key);
                if (value == null) {
                    value = new HashMap<>();
                }
                String[] attrs = new String[] {"latitude", "longitude", "altitude", "type"};
                for (String valueMapKey : attrs) {
                    String valueMapValue = o.getString(valueMapKey);
                    value.put(valueMapKey, valueMapValue);
                }
                result.put(key, value);
            }
        }

        if (hr != null && hr.length() > 0) {
            for (int i = 0; i < hr.length(); i++) {
                JSONObject o = hr.getJSONObject(i);
                Long key = TimeUnit.SECONDS.toMillis((long)Float.parseFloat(o.getString("timestamp")));
                HashMap<String, String> value = result.get(key);
                if (value == null) {
                    value = new HashMap<>();
                }
                String valueMapKey = "heart_rate";
                String valueMapValue = o.getString(valueMapKey);
                value.put(valueMapKey, valueMapValue);
                result.put(key, value);
            }
        }
        return result;
    }
}
