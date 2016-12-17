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

import org.runnerup.common.util.Constants;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Class that creates a POST data to submit to Runalyze using the activity information
 * in the runnerup database. The class uses some constants that in Runalyze are
 * inside the database (soports and types IDs, calories assigned to each sport and so on).
 */
public class RunalyzePost {

    private SQLiteDatabase mDB = null;

    /**
     * Constructor using the database.
     * @param mDB The database to read the activity information
     */
    public RunalyzePost(SQLiteDatabase mDB) {
        this.mDB = mDB;
    }

    //
    // FIELDS ABOUT LOCATION
    //

    /**
     * Methos that creates the cursor over all the location track of the activity.
     * @param activityId The activity id
     * @return The cursor associated to the list of locations of the activity
     */
    private Cursor createLocationCursor(long activityId) {
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
    private void writeValue(String value, String defaultValue, Writer writer) throws IOException {
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
    private void writeArrTime(Cursor c, Writer writer) throws IOException {
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
    private void writeArrDist(Cursor c, Writer writer) throws IOException {
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
    private void writeLocationField(String name, int column, String defaultValue, Cursor c, Writer writer) throws IOException {
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
    private void writeLocationFields(long activityId, Writer writer) throws IOException {
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
    private Cursor createLapCursor(long activityId) {
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
    private void writeNomalField(String name, String value, String defaultValue, Writer writer) throws IOException {
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
    private String seconds2MinuteAndSeconds(long seconds) {
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
    private void writeLapFields(long activityId, Writer writer) throws IOException {
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
    private Cursor createActivityCursor(long activityId) {
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
     * Runalyze sports are stored in the table <em>runalyze_sport</em>. For the moment
     * they are:
     * <pre>
     * |  1 | Running
     * |  2 | Swimming
     * |  3 | Biking
     * |  4 | Gymnastics
     * |  5 | Other
     * </pre>
     *
     * @param dbValue The sport as used in runnerup
     * @return The id of the sport in a string
     */
    public String runnerupSport2RunalyzeSport(int dbValue) {
        Sport sport = Sport.valueOf(dbValue);
        if (sport.IsRunning()) {
            return "1";
        }
        if (sport.IsCycling()) {
            return "3";
        }

        return "5";
    }

    /**
     * Runalyze types are stored in the table <em>runalyze_type</em>. For the moment
     * they are:
     * <pre>
     * | id | name               | abbr | sportid |
     * |  1 | Jogging            | JOG  |       1 |
     * |  2 | Fartlek            | FL   |       1 |
     * |  3 | Interval training  | IT   |       1 |
     * |  4 | Tempo Run          | TR   |       1 |
     * |  5 | Race               | RC   |       1 |
     * |  6 | Regeneration Run   | RG   |       1 |
     * |  7 | Long Slow Distance | LSD  |       1 |
     * |  8 | Warm-up            | WU   |       1 |
     * </pre>
     *
     * @param dbValue The sport id used in runnerup
     * @return The sport id for runalyze in a string
     */
    public String runnerupSport2RunalyzeType(int dbValue) {
        Sport sport = Sport.valueOf(dbValue);
        if (sport.IsRunning()) {
            return "1";
        }
        return "";
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
     * Method that writes all the fields that are related to the activity information.
     * @param activityId The activity id to export
     * @param writer The writer to write
     * @throws IOException Some error
     */
    private void writeActivityFields(long activityId, Writer writer) throws IOException {
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
                writeNomalField("sportid", runnerupSport2RunalyzeSport(c.getInt(6)), null, writer);
                // the type is finally blank, it was decided better not to provide anything
                // previously it was called with runnerupSport2RunalyzeType(c.getInt(6))
                writeNomalField("typeid", "", null, writer);
                // it seems that the activity_id in runalyze is set in "class.ParserAbstractSingle.php"
                // and it is just the timestamp in seconds of the activity
                writeNomalField("activity_id", Long.toString(c.getLong(0)), null, writer);
                writeNomalField("kcal", calculateKCal(c.getInt(6), c.getLong(1)), null, writer);
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
    private void writeFixedFields(Writer writer) throws IOException {
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
