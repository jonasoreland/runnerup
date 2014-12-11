/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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
import android.util.Pair;
import android.util.Xml;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.workout.Sport;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
/**
 * TCX - export an activity in TCX format
 * 
 * @todo Handle pauses
 * @todo Other sports than running
 * 
 * @author jonas.oreland@gmail.com
 * 
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class TCX {

    long mID = 0;
    SQLiteDatabase mDB = null;
    XmlSerializer mXML = null;
    String notes = null;
    SimpleDateFormat simpleDateFormat = null;

    private boolean addGratuitousTrack = false;

    public TCX(SQLiteDatabase mDB) {
        this.mDB = mDB;
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    String formatTime(long time) {
        return simpleDateFormat.format(new Date(time));
    }

    public String export(long activityId, Writer writer) throws IOException {
        Pair<String,Sport> res = exportWithSport(activityId, writer);
        return res.first;
    }

    /**
     * @param activityId
     * @param writer
     * @return TCX id
     * @throws IOException
     */
    public Pair<String,Sport> exportWithSport(long activityId, Writer writer) throws IOException {

        Sport sport = null;
        String[] aColumns = {
                DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.SPORT
        };
        Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = cursor.getLong(2); // epoch
        try {
            mXML = Xml.newSerializer();
            mXML.setOutput(writer);
            mXML.startDocument("UTF-8", true);
            mXML.startTag("", "TrainingCenterDatabase");
            mXML.attribute("", "xmlns:ext",
                    "http://www.garmin.com/xmlschemas/ActivityExtension/v2");
            mXML.attribute("", "xmlns",
                    "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2");
            mXML.startTag("", "Activities");
            mXML.startTag("", "Activity");
            if (cursor.isNull(3)) {
                mXML.attribute("", "Sport", "Running");
            } else {
                switch (cursor.getInt(3)) {
                    case DB.ACTIVITY.SPORT_RUNNING:
                        sport = Sport.RUNNING;
                        mXML.attribute("", "Sport", "Running");
                        break;
                    case DB.ACTIVITY.SPORT_BIKING:
                        sport = Sport.BIKING;
                        mXML.attribute("", "Sport", "Biking");
                        break;
                    default:
                        mXML.attribute("", "Sport", "Other");
                        break;
                }
            }
            mXML.startTag("", "Id");
            String id = formatTime(startTime * 1000);
            mXML.text(id);
            mXML.endTag("", "Id");
            exportLaps(activityId, startTime * 1000);
            if (!cursor.isNull(1)) {
                notes = cursor.getString(1);
                mXML.startTag("", "Notes");
                mXML.text(notes);
                mXML.endTag("", "Notes");
            }
            mXML.endTag("", "Activity");
            mXML.endTag("", "Activities");
            mXML.endTag("", "TrainingCenterDatabase");
            mXML.flush();
            mXML.endDocument();
            mXML = null;
            cursor.close();
            return new Pair<String, Sport>(id, sport);
        } catch (IOException e) {
            cursor.close();
            mXML = null;
            throw e;
        }
    }

    private void exportLaps(long activityId, long startTime) throws IOException {
        String[] lColumns = {
                DB.LAP.LAP, DB.LAP.DISTANCE, DB.LAP.TIME,
                DB.LAP.INTENSITY
        };

        Cursor cLap = mDB.query(DB.LAP.TABLE, lColumns, DB.LAP.DISTANCE + " > 0 and "
                + DB.LAP.ACTIVITY + " = " + activityId, null, null, null, null);
        String[] pColumns = {
                DB.LOCATION.LAP, DB.LOCATION.TIME,
                DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE,
                DB.LOCATION.ALTITUDE, DB.LOCATION.TYPE, DB.LOCATION.HR
        };
        Cursor cLocation = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null,
                null);
        boolean lok = cLap.moveToFirst();
        boolean pok = cLocation.moveToFirst();

        float totalDistance = 0;
        while (lok) {
            if (cLap.getFloat(1) != 0 && cLap.getLong(2) != 0) {
                long lap = cLap.getLong(0);
                while (pok && cLocation.getLong(0) != lap) {
                    pok = cLocation.moveToNext();
                }
                mXML.startTag("", "Lap");
                if (pok && cLocation.getLong(0) == lap) {
                    mXML.attribute("", "StartTime", formatTime(cLocation.getLong(1)));
                } else {
                    mXML.attribute("", "StartTime", formatTime(startTime));
                }
                mXML.startTag("", "TotalTimeSeconds");
                mXML.text("" + cLap.getLong(2));
                mXML.endTag("", "TotalTimeSeconds");
                mXML.startTag("", "DistanceMeters");
                mXML.text("" + cLap.getFloat(1));
                mXML.endTag("", "DistanceMeters");
                mXML.startTag("", "Calories");
                mXML.text("0");
                mXML.endTag("", "Calories");
                mXML.startTag("", "Intensity");
                mXML.text("Active");
                mXML.endTag("", "Intensity");
                mXML.startTag("", "TriggerMethod");
                mXML.text("Manual");
                mXML.endTag("", "TriggerMethod");
                int maxHR = 0;
                long sumHR = 0;
                long cntHR = 0;
                int cntTrackpoints = 0;

                if (pok && cLocation.getLong(0) == lap) {
                    mXML.startTag("", "Track");
                    float last_lat = 0;
                    float last_longi = 0;
                    long last_time = 0;
                    while (pok && cLocation.getLong(0) == lap) {
                        long time = cLocation.getLong(1);
                        float lat = cLocation.getFloat(2);
                        float longi = cLocation.getFloat(3);
                        if (!(time == last_time && lat == last_lat && longi != last_longi)) {
                            cntTrackpoints++;

                            mXML.startTag("", "Trackpoint");
                            mXML.startTag("", "Time");
                            mXML.text(formatTime(time));
                            mXML.endTag("", "Time");
                            mXML.startTag("", "Position");
                            mXML.startTag("", "LatitudeDegrees");
                            mXML.text("" + lat);
                            mXML.endTag("", "LatitudeDegrees");
                            mXML.startTag("", "LongitudeDegrees");
                            mXML.text("" + longi);
                            mXML.endTag("", "LongitudeDegrees");
                            mXML.endTag("", "Position");
                            if (!cLocation.isNull(4)) {
                                mXML.startTag("", "AltitudeMeters");
                                mXML.text("" + cLocation.getLong(4));
                                mXML.endTag("", "AltitudeMeters");
                            }
                            float d[] = {
                                0
                            };
                            if (!(last_lat == 0 && last_longi == 0)) {
                                Location.distanceBetween(last_lat, last_longi,
                                        lat, longi, d);
                            }
                            totalDistance += d[0];
                            mXML.startTag("", "DistanceMeters");
                            mXML.text("" + totalDistance);
                            mXML.endTag("", "DistanceMeters");
                            if (!cLocation.isNull(6)) {
                                int hr = cLocation.getInt(6);
                                maxHR = hr > maxHR ? hr : maxHR;
                                sumHR += hr;
                                cntHR++;

                                mXML.startTag("", "HeartRateBpm");
                                mXML.startTag("", "Value");
                                String bpm = Integer.toString(hr);
                                mXML.text(bpm);
                                mXML.endTag("", "Value");
                                mXML.endTag("", "HeartRateBpm");
                            }
                            mXML.endTag("", "Trackpoint");
                            last_time = time;
                            last_lat = lat;
                            last_longi = longi;
                        }
                        pok = cLocation.moveToNext();
                    }
                    mXML.endTag("", "Track");
                }
                // Digifit chokes if there isn't at least *1* trackpoint, but is
                // ok
                // even if it's empty.
                if (cntTrackpoints == 0 && addGratuitousTrack) {
                    mXML.startTag("", "Track");
                    mXML.startTag("", "Trackpoint");
                    mXML.startTag("", "Time");
                    mXML.text(formatTime(startTime));
                    mXML.endTag("", "Time");
                    mXML.endTag("", "Trackpoint");
                    mXML.endTag("", "Track");
                }

                if (cntHR > 0) {
                    mXML.startTag("", "AverageHeartRateBpm");
                    mXML.startTag("", "Value");
                    mXML.text(Integer.toString((int) (sumHR / cntHR)));
                    mXML.endTag("", "Value");
                    mXML.endTag("", "AverageHeartRateBpm");

                    mXML.startTag("", "MaximumHeartRateBpm");
                    mXML.startTag("", "Value");
                    mXML.text(Integer.toString(maxHR));
                    mXML.endTag("", "Value");
                    mXML.endTag("", "MaximumHeartRateBpm");
                }
                mXML.endTag("", "Lap");
            }
            lok = cLap.moveToNext();
        }
        cLap.close();
        cLocation.close();
    }

    public String getNotes() {
        return notes;
    }

    public void setAddGratuitousTrack(boolean addGratuitousTrack) {
        this.addGratuitousTrack = addGratuitousTrack;
    }
}
