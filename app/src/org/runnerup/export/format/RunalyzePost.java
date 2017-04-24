/*
 * Copyright (C) 2016 rickyepoderi@yahoo.es
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
import android.location.Location;
import android.util.Log;

import org.runnerup.common.util.Constants;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Class that creates a POST data to submit to Runalyze using the activity information
 * in the runnerup database. The class uses some constants that in Runalyze are
 * inside the database (soports and types IDs, calories assigned to each sport and so on).
 */
public class RunalyzePost {

    private SQLiteDatabase mDB = null;
    private Map<String,Map<String,String>> sports;
    private Map<String,Map<String,String>> types;

    /**
     * Constructor using the database.
     * @param mDB The database to read the activity information
     * @param sports The map of sports read in runalyze.
     * @param types The map of types in runalyze.
     */
    public RunalyzePost(SQLiteDatabase mDB, Map<String,Map<String,String>> sports, Map<String,Map<String,String>> types) {
        this.mDB = mDB;
        this.sports = sports;
        this.types = types;
    }

    //
    // FIELDS ABOUT LOCATION
    //

    /**
     * Methos that creates the cursor over all the location track of the activity.
     * @param activityId The activity id
     * @return The cursor associated to the list of locations of the activity
     */
    public Cursor createLocationCursor(long activityId) {
        String[] columns = {
                Constants.DB.LOCATION.TIME,
                Constants.DB.LOCATION.LATITUDE,
                Constants.DB.LOCATION.LONGITUDE,
                Constants.DB.LOCATION.ALTITUDE,
                Constants.DB.LOCATION.HR,
        };
        return mDB.query(Constants.DB.LOCATION.TABLE, columns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null, null);
    }

    /**
     * Method that writes a value in POST format, the value is encoded
     * properly. If it is null the defaultValue is used instead.
     * @param value The value to write
     * @param defaultValue The default value to use in case value is null
     * @param writer The writer to write with
     * @throws IOException Some error writing the value
     */
    public void writeValue(String value, String defaultValue, Writer writer) throws IOException {
        try {
            if (value == null) {
                value = defaultValue;
            }
            writer.write(URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // it's impossible "UTF-8" is a valid encoding
        }
    }

    /**
     * This method writes the <em>arr_time</em> for runalyze. This field is an array of seconds
     * since the beginning for each track point.
     * @param c The cursor to iterate with the locations
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeArrTime(Cursor c, Writer writer) throws IOException {
        writer.write("arr_time");
        writer.write("=");
        if (c.moveToFirst()) {
            long start = c.getLong(0) / 1000;
            writer.write(Long.toString(c.getLong(0) / 1000 - start));
            while (c.moveToNext()) {
                writer.write("|");
                writer.write(Long.toString(c.getLong(0) / 1000 - start));
            }
        }
        writer.write("&");
    }

    /**
     * Method that writes the distance for each track point in kilometers (arr_dist).
     * @param c The cursor to iterate with the locations
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeArrDist(Cursor c, Writer writer) throws IOException {
        writer.write("arr_dist");
        writer.write("=");
        if (c.moveToFirst()) {
            double lat = c.getDouble(1);
            double lon = c.getDouble(2);
            float totalDistance = 0;
            writer.write(Float.toString(totalDistance));
            while (c.moveToNext()) {
                writer.write("|");
                float d[] = {0};
                double newLat = c.getDouble(1);
                double newLon = c.getDouble(2);
                Location.distanceBetween(lat, lon, newLat, newLon, d);
                //Log.d(getClass().getName(), "Distance: [" + lat + ", " + lon + "] -> [" + newLat + ", " + newLon +  "]");
                totalDistance += d[0];
                lat = newLat;
                lon = newLon;
                writer.write(Float.toString(totalDistance/1000.0F));
            }
        }
        writer.write("&");
    }

    /**
     * Generic method to write an array for runalyze with a direct column of the location cursor.
     * @param name The name of the field in runalyze
     * @param column The column position of the value
     * @param defaultValue The default value if the column value is null
     * @param c The cursor to iterate with the locations
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeLocationField(String name, int column, String defaultValue, Cursor c, Writer writer) throws IOException {
        writer.write(name);
        writer.write("=");
        if (c.moveToFirst()) {
            writeValue(c.getString(column), defaultValue, writer);
            while (c.moveToNext()) {
                writer.write("|");
                writeValue(c.getString(column), defaultValue, writer);
            }
        }
        writer.write("&");
    }

    /**
     * Method that writes oll the post inputs that are arrays dependent of the location table.
     * @param activityId The activity id to export
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeLocationFields(long activityId, Writer writer) throws IOException {
        Cursor c = null;
        try {
            c = createLocationCursor(activityId);
            writeArrTime(c, writer);
            writeArrDist(c, writer);
            writeLocationField("arr_lat", 1, null, c, writer);
            writeLocationField("arr_lon", 2, null, c, writer);
            writeLocationField("arr_alt", 3, "0", c, writer);
            writeLocationField("arr_heart", 4, "0", c, writer);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    //
    // FIELDS ABOUT LAP
    //

    /**
     * Methos that creates the cursor over all the laps of the activity.
     * @param activityId The activity id
     * @return The cursor associated to the list of laps of the activity
     */
    public Cursor createLapCursor(long activityId) {
        String[] columns = {
                Constants.DB.LAP.DISTANCE,
                Constants.DB.LAP.TIME,
                //Constants.DB.LAP.INTENSITY,
        };
        return mDB.query(Constants.DB.LAP.TABLE, columns,
                Constants.DB.LAP.ACTIVITY + " = " + activityId, null, null, null, null);
    }

    /**
     * Method that write a typical post input <em>name=value</em> with the value properly encoded.
     * @param name The name of the value
     * @param value The value
     * @param defaultValue The default value to use if the value is null
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeNomalField(String name, String value, String defaultValue, Writer writer) throws IOException {
        writer.write(name);
        writer.write("=");
        writeValue(value, defaultValue, writer);
        writer.write("&");
    }

    /**
     * Method that transforms a time in seconds to runalyze TIME_MINUTES format (mm:ss).
     * @param seconds The seconds to transform
     * @return The string with the seconds in format mm:ss
     */
    public String seconds2MinuteAndSeconds(long seconds) {
        // runalyze has some data that is expressed in mm:ss (TIME_MINUTES)
        // you can check it in class.FormularValueParser.php method validateTimeMinutes
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /**
     * Method that writes all runalyze fields that are dependant of the laps information.
     * @param activityId The activity id to export
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeLapFields(long activityId, Writer writer) throws IOException {
        Cursor c = null;
        try {
            c = createLapCursor(activityId);
            boolean more = c.moveToFirst();
            while (more) {
                writeNomalField("splits[km][]", Float.toString(c.getFloat(0)/1000.0F), null, writer);
                writeNomalField("splits[time][]", seconds2MinuteAndSeconds(c.getLong(1)), null, writer);
                writeNomalField("splits[active][]", "1", null, writer);
                more = c.moveToNext();
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    //
    // FIELDS ABOUT ACTIVITY
    //

    /**
     * Methos that creates the cursor over the activity itself (one row).
     * @param activityId The activity id
     * @return The cursor associated to the activity
     */
    public Cursor createActivityCursor(long activityId) {
        String[] columns = {
                Constants.DB.ACTIVITY.START_TIME,
                Constants.DB.ACTIVITY.TIME,
                Constants.DB.ACTIVITY.DISTANCE,
                Constants.DB.ACTIVITY.AVG_HR,
                Constants.DB.ACTIVITY.MAX_HR,
                Constants.DB.ACTIVITY.COMMENT,
                Constants.DB.ACTIVITY.SPORT,
        };
        return mDB.query(Constants.DB.ACTIVITY.TABLE, columns, "_id = " + activityId,
                null, null, null, null);
    }

    /**
     * <em>Runalyze</em> calculates the calories using the table of sports. By default they
     * are using this:
     * <pre>
     * | id | name       | kcal |
     * |  1 | Running    |  880 |
     * |  2 | Swimming   |  743 |
     * |  3 | Biking     |  770 |
     * |  4 | Gymnastics |  280 |
     * |  5 | Other      |  500 |
     * </pre>
     * @param dbValue The sport id used in runnerup
     * @param seconds The time of the activity for the calculation
     * @return The kCal used in string
     */
    public String calculateKCal(int dbValue, long seconds) {
        Sport sport = Sport.valueOf(dbValue);
        if (sport.IsRunning()) {
            return Integer.toString(Math.round((880.0F / 3600.0F) * seconds));
        }
        if (sport.IsCycling()) {
            return Integer.toString(Math.round((770.0F / 3600.0F) * seconds));
        }
        //Constants.DB.ACTIVITY.SPORT_OTHER:
        return Integer.toString(Math.round((500.0F / 3600.0F) * seconds));
    }

    /**
     * Method that calculates sportid, typeid and kcal.
     * Runalyze sport is read in the sports map. The exporter looks for a sport that contains the
     * <em>runnerup</em> sport name ignoring case. If no sport is found a default "1" is returned.
     * The <em>typeid</em> is just the lowest type if found.
     * The <em>kcal</em> is calculated using the sport data if found, if not the calculateKCal default
     * values is used.
     *
     * @param dbValue The value in the ddbb
     * @param seconds The time of the activity for the calculation
     * @throws IOException Some error
     */
    public void calculateSportTypeAndKCal(int dbValue, long seconds, Writer writer) throws IOException {
        //
        // calculate sportid
        Sport sport = Sport.valueOf(dbValue);
        String sportName = sport.name().toLowerCase();
        String sportId = "1"; // default values is 1 (just something)
        String sportFound = null;
        for (String runalyzeSport: sports.keySet()) {
            if (runalyzeSport.toLowerCase().contains(sportName) &&
                    sports.get(runalyzeSport).containsKey("value")) {
                sportId = sports.get(runalyzeSport).get("value");
                sportFound = runalyzeSport;

            }
        }
        Log.d(getClass().getName(), "Using runalyze sport=" + sportFound + "(" + sportId + ")");
        writeNomalField("sportid", sportId, null, writer);
        //
        // calculate typeid
        String typeFound = null;
        String typeId = "";
        if (sportFound != null) {
            // the typeid will be the min value of that type
            int typeIdInt = Integer.MAX_VALUE;
            for (Map.Entry<String,Map<String,String>> e: types.entrySet()) {
                if (sportId.equals(e.getValue().get("data-sport")) &&
                        e.getValue().containsKey("value") &&
                        typeIdInt > Integer.parseInt(e.getValue().get("value"))) {
                    typeIdInt = Integer.parseInt(e.getValue().get("value"));
                    typeFound = e.getKey();
                }
            }
            if (typeIdInt != Integer.MAX_VALUE) {
                typeId = Integer.toString(typeIdInt);
            }
        }
        Log.d(getClass().getName(), "Using runalyze type=" + typeFound + "(" + typeId + ")");
        writeNomalField("typeid", typeId, null, writer);
        //
        // calculate kcal
        String kcal = null;
        if (sportFound != null && sports.get(sportFound).containsKey("data-kcal")) {
            kcal = Integer.toString(Math.round((Float.parseFloat(sports.get(sportFound).get("data-kcal")) / 3600.0F) * seconds));
            Log.d(getClass().getName(), "Kcal using sports value: " + sports.get(sportFound).get("data-kcal"));
        } else {
            // calculate in default numbers
            Log.d(getClass().getName(), "Kcal using defaults value");
            kcal = calculateKCal(dbValue, seconds);
        }
        writeNomalField("kcal", kcal, null, writer);
    }

    /**
     * Method that writes all the fields that are related to the activity information.
     * @param activityId The activity id to export
     * @param writer The writer to write
     * @throws IOException Some error
     */
    public void writeActivityFields(long activityId, Writer writer) throws IOException {
        Cursor c = null;
        try {
            c = createActivityCursor(activityId);
            if (c.moveToFirst()) {
                writeNomalField("time_day", new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date(c.getLong(0) * 1000)), null, writer);
                writeNomalField("time_daytime", new SimpleDateFormat("HH:mm", Locale.US).format(new Date(c.getLong(0) * 1000)), null, writer);
                writeNomalField("s", seconds2MinuteAndSeconds(c.getLong(1)), null, writer);
                writeNomalField("elapsed_time", c.getString(1), null, writer);
                writeNomalField("distance", Float.toString(c.getFloat(2)/1000.0F), null, writer);
                writeNomalField("pulse_avg", c.getString(3), "0", writer);
                writeNomalField("pulse_max", c.getString(4), "0", writer);
                writeNomalField("comment", c.getString(5), "", writer);
                writeNomalField("pace", seconds2MinuteAndSeconds(Math.round(c.getFloat(1)*1000.0F/c.getFloat(2))), "", writer);
                // write sportid, typeid and kcal depending the sports and types read from runalyze
                calculateSportTypeAndKCal(c.getInt(6), c.getLong(1), writer);
                // it seems that the activity_id in runalyze is set in "class.ParserAbstractSingle.php"
                // and it is just the timestamp in seconds of the activity
                writeNomalField("activity_id", Long.toString(c.getLong(0)), null, writer);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    //
    // FIELDS THAT ARE FIXED NOW
    //

    /**
     * Method that writes the rest of information that is fixed.
     * @param writer The writer to write.
     * @throws IOException Some error
     */
    public void writeFixedFields(Writer writer) throws IOException {
        writeNomalField("creator", "", null, writer);
        writeNomalField("creator_details", "", null, writer);
        writeNomalField("timezone_offset", Integer.toString(TimeZone.getDefault().getRawOffset() / 60000), null, writer);
        writeNomalField("arr_geohashes", "", null, writer);
        writeNomalField("arr_alt_original", "", null, writer);
        writeNomalField("arr_cadence", "", null, writer); // TODO: Maybe needed for biking???
        writeNomalField("arr_power", "", null, writer);
        writeNomalField("arr_temperature", "", null, writer);
        writeNomalField("arr_groundcontact", "", null, writer);
        writeNomalField("arr_vertical_oscillation", "", null, writer);
        writeNomalField("arr_groundcontact_balance", "", null, writer);
        writeNomalField("pauses", "[]", null, writer); // TODO: How to get the pauses?
        writeNomalField("hrv", "", null, writer);
        writeNomalField("fit_vdot_estimate", "0.00", null, writer);
        writeNomalField("fit_recovery_time", "0", null, writer);
        writeNomalField("fit_hrv_analysis", "0", null, writer);
        writeNomalField("fit_training_effect", "", null, writer);
        writeNomalField("fit_performance_condition", "", null, writer);
        writeNomalField("elevation_calculated", "0", null, writer);
        writeNomalField("groundcontact", "0", null, writer);
        writeNomalField("vertical_oscillation", "0", null, writer);
        writeNomalField("groundcontact_balance", "0", null, writer);
        writeNomalField("vertical_ratio", "0", null, writer);
        writeNomalField("stroke", "", null, writer);
        writeNomalField("stroketype", "", null, writer);
        writeNomalField("total_strokes", "0", null, writer);
        writeNomalField("swolf", "0", null, writer);
        writeNomalField("pool_length", "0", null, writer);
        writeNomalField("weather_source", "", null, writer);
        writeNomalField("is_night", "0", null, writer); // TODO: Calculate from position and time???
        writeNomalField("distance-to-km-factor", "1", null, writer);
        writeNomalField("is_race_sent", "true", null, writer);
        writeNomalField("elevation", "0", null, writer);
        writeNomalField("power", "0", null, writer);
        writeNomalField("cadence", "0", null, writer);
        writeNomalField("use_vdot", "on", null, writer);
        writeNomalField("rpe", "", null, writer);
        writeNomalField("partner", "", null, writer);
        writeNomalField("route", "", null, writer);
        writeNomalField("notes", "", null, writer); // TODO: Use notes or comment???
        writeNomalField("weatherid", "1", null, writer);
        writeNomalField("temperature", "", null, writer);
        writeNomalField("wind_speed", "", null, writer);
        writeNomalField("wind_deg", "", null, writer);
        writeNomalField("humidity", "", null, writer);
        writeNomalField("pressure", "", null, writer);
        writeNomalField("tag_old", "", null, writer);
        writeNomalField("equipment_old", "", null, writer);
        writeNomalField("submit", "submit", null, writer);
    }

    /**
     * Method that construct the complete post information using the database.
     * @param activityId The activity id to export
     * @param writer Thw writer to write
     * @throws IOException Some error
     */
    public void export(long activityId, Writer writer) throws IOException {
        writeActivityFields(activityId, writer);
        writeLapFields(activityId, writer);
        writeLocationFields(activityId, writer);
        writeFixedFields(writer);
    }
}
