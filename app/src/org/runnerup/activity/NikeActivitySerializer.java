package org.runnerup.activity;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.export.NikePlus;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

/**
 * Created by itdog on 18.01.15.
 */
public class NikeActivitySerializer extends ExternalActivitySerializer<NikePlus> {

    public NikeActivitySerializer(Context context) {
        super(context);
    }

    @Override
    public SportActivity deserialize(File f) throws FileNotFoundException, JSONException {
        SportActivity result = null;
        if(f != null && f.exists()) {
            result = new SportActivity();
            String jsonString = new Scanner(f).useDelimiter("\\A").next();
            JSONObject root = new JSONObject(jsonString);
            JSONObject metricSummary = root.getJSONObject("metricSummary");


            result.setExternalId(f.getName());
            result.setType(NikePlus.NAME);
            result.setDistance(Float.valueOf(metricSummary.getString("distance")) * 1000);

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            try {
                Date startTime = dateFormat.parse(root.getString("startTime"));
                result.setStartTime(startTime.getTime() / 1000);
                DateFormat durationFormat = new SimpleDateFormat("HH:mm:ss.SSS");
                durationFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date duration = durationFormat.parse(metricSummary.getString("duration"));
                result.setTime(duration.getTime() / 1000);
            } catch (ParseException e) {
            }

            Lap lap = new Lap();
            lap.setDistance(result.getDistance());
            lap.setCadenceAvg(result.getCadenceAvg());
            lap.setHrAvg(result.getHrAvg());
            lap.setHrMax(result.getHrMax());
            lap.setTime(result.getTime());
            lap.setLap(0);
            result.laps().add(lap);

            if (root.getBoolean("isGpsActivity")) {
                JSONObject gps = root.getJSONObject("gps");
                JSONArray waypoints = gps.getJSONArray("waypoints");

                long timeInterval = (result.getTime() * 1000) / waypoints.length();
                long time=timeInterval;
                long last_time =0;

                for (int i = 0; i < waypoints.length(); i++) {
                    try {
                        JSONObject waypoint = waypoints.getJSONObject(i);
                        LocationData location = new LocationData();
                        location.setLap(0);

                        location.setLatitude(waypoint.getDouble("latitude"));
                        location.setLongitude(waypoint.getDouble("longitude"));
                        location.setAltitude(waypoint.getDouble("elevation"));

                        location.setTime(time);

                        int type;
                        if (i == 0) {
                            type = Constants.DB.LOCATION.TYPE_RESUME;
                        } else {
                            type = Constants.DB.LOCATION.TYPE_GPS;
                        }

                        location.setType((short) type);

                        result.locationData().add(location);

                        time += timeInterval;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        return result;
    }
}
