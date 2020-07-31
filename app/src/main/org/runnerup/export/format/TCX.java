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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Pair;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathCursor;
import org.runnerup.db.PathSimplifier;
import org.runnerup.util.KXmlSerializer;
import org.runnerup.workout.Sport;

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


public class TCX {

    private final SQLiteDatabase mDB;
    private KXmlSerializer mXML = null;
    private String notes = null;
    private final SimpleDateFormat simpleDateFormat;
    private Sport sport = null;
    private final PathSimplifier simplifier;

    public TCX(SQLiteDatabase mDB, PathSimplifier simplifier) {
        this.mDB = mDB;
        this.simplifier = simplifier;
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private String formatTime(long time) {
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

        String[] aColumns = {
                DB.ACTIVITY.NAME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.SPORT, DB.ACTIVITY.META_DATA
        };
        Cursor cursor = mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = cursor.getLong(2); // epoch
        try {
            mXML = new KXmlSerializer();
            mXML.setOutput(writer);
            mXML.startDocument("UTF-8", true);
            mXML.startTag("", "TrainingCenterDatabase");
            mXML.attribute("", "xmlns:xsi",
                    "http://www.w3.org/2001/XMLSchema-instance");
            mXML.attribute("", "xmlns:xsd",
                    "http://www.w3.org/2001/XMLSchema");
            mXML.attribute("", "xmlns:ext",
                    "http://www.garmin.com/xmlschemas/ActivityExtension/v2");
            mXML.attribute("", "xmlns",
                    "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2");
            mXML.startTag("", "Activities");
            mXML.startTag("", "Activity");
            if (cursor.isNull(3)) {
                mXML.attribute("", "Sport", "Running");
            } else {
                // TCX supports only these 3 sports...(cf http://www8.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd)
                sport = Sport.valueOf(cursor.getInt(3));
                if (sport.IsRunning()) {
                    mXML.attribute("", "Sport", "Running");
                }
                else if (sport.IsCycling()) {
                    mXML.attribute("", "Sport", "Biking");
                }
                else {
                    mXML.attribute("", "Sport", "Other");
                }
            }
            mXML.startTag("", "Id");
            String id = formatTime(startTime * 1000);
            mXML.text(id);
            mXML.endTag("", "Id");
            exportLaps(activityId, startTime * 1000, sport);
            if (!cursor.isNull(1)) {
                notes = cursor.getString(1);
                mXML.startTag("", "Notes");
                mXML.text(notes);
                mXML.endTag("", "Notes");
            }
            mXML.startTag("", "Creator");
            mXML.attribute("", "xsi:type", "Device_t");
            mXML.startTag("", "Name");
            String creator = "RunnerUp " + android.os.Build.MODEL;
            if (!cursor.isNull(4)) {
                String metaData = cursor.getString(4);
                if (metaData.contains(DB.ACTIVITY.WITH_BAROMETER)) {
                    creator += " with barometer";
                }
            }
            mXML.text(creator);
            mXML.endTag("", "Name");
            mXML.endTag("", "Creator");
            mXML.endTag("", "Activity");
            mXML.endTag("", "Activities");
            mXML.endTag("", "TrainingCenterDatabase");
            mXML.flush();
            mXML.endDocument();
            mXML = null;
            cursor.close();
            return new Pair<>(id, sport);
        } catch (IOException e) {
            cursor.close();
            mXML = null;
            throw e;
        }
    }

    private void exportLaps(long activityId, long startTime, Sport sport) throws IOException {
        String[] lColumns = {
                DB.LAP.LAP, DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.INTENSITY
        };
        Cursor cLap = mDB.query(DB.LAP.TABLE, lColumns, DB.LAP.DISTANCE + " > 0 and "
                + DB.LAP.ACTIVITY + " = " + activityId, null, null, null, null);

        String[] pColumns = {
                DB.LOCATION.LAP, DB.LOCATION.TYPE,
                DB.LOCATION.TIME, DB.LOCATION.DISTANCE,
                DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE, DB.LOCATION.ALTITUDE,
                DB.LOCATION.HR, DB.LOCATION.CADENCE, DB.PRIMARY_KEY //, DB.LOCATION.TEMPERATURE, DB.LOCATION.PRESSURE
        };
        PathCursor cLocation = new PathCursor(mDB, activityId, pColumns, 9, simplifier);
        boolean lok = cLap.moveToFirst();
        boolean pok = cLocation.moveToFirst();

        double totalDistance = 0;
        while (lok) {
            if (cLap.getFloat(1) != 0 && cLap.getLong(2) != 0) {
                long lap = cLap.getLong(0);
                while (pok && cLocation.getLong(0) != lap) {
                    pok = cLocation.moveToNext();
                }
                mXML.startTag("", "Lap");
                if (pok && cLocation.getLong(0) == lap) {
                    mXML.attribute("", "StartTime", formatTime(cLocation.getLong(2)));
                } else {
                    mXML.attribute("", "StartTime", formatTime(startTime));
                }
                mXML.startTag("", "TotalTimeSeconds");
                mXML.text("" + cLap.getLong(1));
                mXML.endTag("", "TotalTimeSeconds");
                mXML.startTag("", "DistanceMeters");
                mXML.text("" + cLap.getDouble(2));
                mXML.endTag("", "DistanceMeters");
                mXML.startTag("", "Calories");
                mXML.text("0");
                mXML.endTag("", "Calories");
                mXML.startTag("", "Intensity");
                long intensity = cLap.getLong(3);
                mXML.text(intensity == DB.INTENSITY.ACTIVE ? "Active" : "Resting");
                mXML.endTag("", "Intensity");
                mXML.startTag("", "TriggerMethod");
                mXML.text("Manual");//TODO
                mXML.endTag("", "TriggerMethod");
                long maxHR = 0;
                long sumHR = 0;
                long cntHR = 0;
                boolean hasTrackpoints = false;

                if (pok && cLocation.getLong(0) == lap) {
                    double last_lat = 0;
                    double last_longi = 0;
                    long last_time = 0;
                    while (pok && cLocation.getLong(0) == lap) {
                        int locType = cLocation.getInt(1);
                        if (hasTrackpoints && locType == DB.LOCATION.TYPE_RESUME) {
                            // Pauses handling
                            mXML.endTag("", "Track");
                            mXML.startTag("", "Track");
                        }
                        long time = cLocation.getLong(2);
                        if (locType == DB.LOCATION.TYPE_GPS && time > last_time) {
                            if (!hasTrackpoints) {
                                mXML.startTag("", "Track");
                            }
                            hasTrackpoints = true;

                            mXML.startTag("", "Trackpoint");
                            mXML.startTag("", "Time");
                            mXML.text(formatTime(time));
                            mXML.endTag("", "Time");

                            mXML.startTag("", "Position");
                            double lat = cLocation.getDouble(4);
                            double longi = cLocation.getDouble(5);
                            mXML.startTag("", "LatitudeDegrees");
                            mXML.text("" + lat);
                            mXML.endTag("", "LatitudeDegrees");
                            mXML.startTag("", "LongitudeDegrees");
                            mXML.text("" + longi);
                            mXML.endTag("", "LongitudeDegrees");
                            mXML.endTag("", "Position");

                            if (!cLocation.isNull(6)) {
                                mXML.startTag("", "AltitudeMeters");
                                mXML.text("" + cLocation.getDouble(6));
                                mXML.endTag("", "AltitudeMeters");
                            }
                            if (!cLocation.isNull(3)) {
                                totalDistance = cLocation.getDouble(3);
                            } else {
                                // Only for older activities, also increases distance when pausing
                                // Most importers do not use this info anyway
                                float[] d = {
                                        0
                                };
                                if (last_lat != 0 || last_longi != 0) {
                                    Location.distanceBetween(last_lat, last_longi,
                                            lat, longi, d);
                                }
                                totalDistance += d[0];
                            }
                            mXML.startTag("", "DistanceMeters");
                            mXML.text("" + totalDistance);
                            mXML.endTag("", "DistanceMeters");
                            if (!cLocation.isNull(7)) {
                                long hr = cLocation.getInt(7);
                                if (hr > 0) {
                                    maxHR = Math.max(hr, maxHR);
                                    sumHR += hr;
                                    cntHR++;

                                    mXML.startTag("", "HeartRateBpm");
                                    mXML.startTag("", "Value");
                                    String bpm = Long.toString(hr);
                                    mXML.text(bpm);
                                    mXML.endTag("", "Value");
                                    mXML.endTag("", "HeartRateBpm");
                                }
                            }

                            boolean isCad = !cLocation.isNull(8);
                            boolean isBikeCad = isCad && sport.IsCycling();
                            boolean isRunCad = isCad && !isBikeCad;
                            //Not supported in .tcx, uncomment for testing
                            //boolean isTemp = !cLocation.isNull(9);
                            //boolean isPres = !cLocation.isNull(10);
                            //boolean isAnyExt = isRunCad || isTemp || isPres;
                            if (isBikeCad) {
                                int val = cLocation.getInt(8);
                                mXML.startTag("", "Cadence");
                                String sval = Integer.toString(val);
                                mXML.text(sval);
                                mXML.endTag("", "Cadence");
                            }
                            if (isRunCad) {
                                mXML.startTag("", "Extensions");
                                mXML.startTag("", "TPX");
                                mXML.attribute("", "xmlns",
                                        "http://www.garmin.com/xmlschemas/ActivityExtension/v2");
                                //"standard" extensions: RunCadence, Speed, Watts
                            }
                            if (isRunCad) {
                                int val = cLocation.getInt(8);
                                mXML.startTag("", "RunCadence");
                                String sval = Integer.toString(val);
                                mXML.text(sval);
                                mXML.endTag("", "RunCadence");
                                // Not including "CadenceSensor Footpod" etc
                            }
                            //if (isTemp || isPres) {
                            //    if (isTemp) {
                            //        int val = cLocation.getInt(9);
                            //        mXML.startTag("", "ext:Temperature");
                            //        String sval = Float.toString(val);
                            //        mXML.text(sval);
                            //        mXML.endTag("", "ext:Temperature");
                            //    }
                            //    if (isPres) {
                            //        int val = cLocation.getInt(10);
                            //        mXML.startTag("", "ext:Pressure");
                            //        String sval = Float.toString(val);
                            //        mXML.text(sval);
                            //        mXML.endTag("", "ext:Pressure");
                            //    }
                            //}
                            if (isRunCad) {
                                mXML.endTag("", "TPX");
                                mXML.endTag("", "Extensions");
                            }

                            mXML.endTag("", "Trackpoint");
                            last_time = time;
                            last_lat = lat;
                            last_longi = longi;
                        }
                        pok = cLocation.moveToNext();
                    }
                }
                // Digifit chokes if there isn't at least *1* trackpoint, but is
                // ok even if it's empty.
                boolean addGratuitousTrack = false;
                if (!hasTrackpoints && addGratuitousTrack) {
                    mXML.startTag("", "Track");
                    mXML.startTag("", "Trackpoint");
                    mXML.startTag("", "Time");
                    mXML.text(formatTime(startTime));
                    mXML.endTag("", "Time");
                    mXML.endTag("", "Trackpoint");
                }
                mXML.endTag("", "Track");

                if (cntHR > 0) {
                    mXML.startTag("", "AverageHeartRateBpm");
                    mXML.startTag("", "Value");
                    mXML.text(Integer.toString((int) (sumHR / cntHR)));
                    mXML.endTag("", "Value");
                    mXML.endTag("", "AverageHeartRateBpm");

                    mXML.startTag("", "MaximumHeartRateBpm");
                    mXML.startTag("", "Value");
                    mXML.text(Long.toString(maxHR));
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

    public Sport getSport() {
        return sport;
    }
}
