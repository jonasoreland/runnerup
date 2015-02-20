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
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Pair;
import android.util.Xml;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.content.db.provider.activity.ActivityCursor;
import org.runnerup.content.db.provider.activity.ActivitySelection;
import org.runnerup.content.db.provider.lap.LapCursor;
import org.runnerup.content.db.provider.lap.LapSelection;
import org.runnerup.content.db.provider.location.LocationCursor;
import org.runnerup.content.db.provider.location.LocationSelection;
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
    XmlSerializer mXML = null;
    String notes = null;
    SimpleDateFormat simpleDateFormat = null;
    Context context = null;
    private boolean addGratuitousTrack = false;

    public TCX(Context ctx) {
        context = ctx;
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
        ActivitySelection activity = new ActivitySelection();
        ActivityCursor c = activity.id(activityId).query(context.getContentResolver());
        c.moveToFirst();
        long startTime = c.getStartTime(); // epoch
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
            if (c.getType() == null) {
                mXML.attribute("", "Sport", "Running");
            } else {
                switch (c.getType()) {
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
            if (c.getComment() != null ) {
                notes = c.getComment();
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
            c.close();
            return new Pair<String, Sport>(id, sport);
        } catch (IOException e) {
            c.close();
            mXML = null;
            throw e;
        }
    }

    private void exportLaps(long activityId, long startTime) throws IOException {
        String[] lColumns = {
                DB.LAP.LAP, DB.LAP.DISTANCE, DB.LAP.TIME,
                DB.LAP.INTENSITY
        };

        LapSelection lap = new LapSelection();
        LapCursor cLap = lap.id(activityId).and().distanceGt(0).query(context.getContentResolver());

        String[] pColumns = {
                DB.LOCATION.LAP, DB.LOCATION.TIME,
                DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE,
                DB.LOCATION.ALTITUDE, DB.LOCATION.TYPE, DB.LOCATION.HR
        };

        LocationSelection location = new LocationSelection();
        LocationCursor cLocation = location.id(activityId).query(context.getContentResolver());

        boolean lok = cLap.moveToFirst();
        boolean pok = cLocation.moveToFirst();

        float totalDistance = 0;
        while (lok) {
            if (cLap.getDistance() != 0 && cLap.getTime() != 0) {
                int lapNumber = cLap.getLap();
                while (pok && cLocation.getLap() != lapNumber) {
                    pok = cLocation.moveToNext();
                }
                mXML.startTag("", "Lap");
                if (pok && cLocation.getLap() == lapNumber) {
                    mXML.attribute("", "StartTime", formatTime(cLocation.getTime()));
                } else {
                    mXML.attribute("", "StartTime", formatTime(startTime));
                }
                mXML.startTag("", "TotalTimeSeconds");
                mXML.text("" + cLap.getTime());
                mXML.endTag("", "TotalTimeSeconds");
                mXML.startTag("", "DistanceMeters");
                mXML.text("" + cLap.getDistance());
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

                if (pok && cLocation.getLap() == lapNumber) {
                    mXML.startTag("", "Track");
                    float last_lat = 0;
                    float last_longi = 0;
                    long last_time = 0;
                    while (pok && cLocation.getLap() == lapNumber) {
                        long time = cLocation.getTime();
                        float lat = cLocation.getLatitude();
                        float longi = cLocation.getLongitude();
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
                            if (cLocation.getAltitude() != null) {
                                mXML.startTag("", "AltitudeMeters");
                                mXML.text("" + cLocation.getAltitude());
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
                            if (cLocation.getHr() != null) {
                                int hr = cLocation.getHr();
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
