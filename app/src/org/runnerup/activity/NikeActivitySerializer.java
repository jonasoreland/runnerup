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
        String jsonString = new Scanner(f).useDelimiter("\\A").next();
        JSONObject root = new JSONObject(jsonString);
        JSONObject metricSummary = root.getJSONObject("metricSummary");

        SportActivity result = new SportActivity();
        result.setExternalId(f.getName());
        result.setType(NikePlus.NAME);
        result.setDistance(Float.valueOf(metricSummary.getString("distance"))*1000);

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            Date startTime = dateFormat.parse(root.getString("startTime"));
            result.setStartTime(startTime.getTime() / 1000);
            DateFormat durationFormat = new SimpleDateFormat("HH:mm:ss.SSS");
            durationFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date duration = durationFormat.parse(metricSummary.getString("duration"));
            result.setTime(duration.getTime() / 1000);
        } catch ( ParseException e) {}

        Lap lap = new Lap();
        lap.setDistance(result.getDistance());
        lap.setCadenceAvg(result.getCadenceAvg());
        lap.setHrAvg(result.getHrAvg());
        lap.setHrMax(result.getHrMax());
        lap.setTime(result.getTime());
        lap.setLap(0);
        result.laps().add(lap);

        if(root.getBoolean("isGpsActivity")) {
            JSONObject gps = root.getJSONObject("gps");
            JSONArray metrics = root.getJSONArray("metrics");
            JSONArray waypoints = gps.getJSONArray("waypoints");

            //JSONArray jsonSpeedValues = null;
            //for(int i=0; i< metrics.length(); i++){
            //    JSONObject metric = metrics.getJSONObject(i);
            //    if(metric.getString("metricType").equals("SPEED")){
            //        jsonSpeedValues = metric.getJSONArray("values");
            //    }
            //
            //}

            long time = 0;

            for(int i=0; i< waypoints.length(); i++){
                try {
                    JSONObject waypoint = waypoints.getJSONObject(i);
                    LocationData location = new LocationData();
                    location.setLap(0);
                    // There are no enough values in metrics
                    //if (jsonSpeedValues != null) {
                    //    location.setSpeed((float) jsonSpeedValues.getDouble(i));
                    //}
                    location.setLatitude((float) waypoint.getDouble("latitude"));
                    location.setLongitude((float) waypoint.getDouble("longitude"));
                    location.setAltitude((float) waypoint.getDouble("elevation"));
                    location.setTime(time);

                    int type;
                    if(i==0){
                        type = Constants.DB.LOCATION.TYPE_START;
                    }else if (i == waypoints.length() -1){
                        type = Constants.DB.LOCATION.TYPE_END;
                    }else{
                        type = Constants.DB.LOCATION.TYPE_GPS;
                    }

                    location.setType((short) type);

                    result.locationData().add(location);

                    time += 10000;
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }



        return result;
    }
}
