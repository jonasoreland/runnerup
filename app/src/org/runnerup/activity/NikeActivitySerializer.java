package org.runnerup.activity;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.util.JsonReader;

import org.json.JSONException;
import org.json.JSONObject;
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


        return result;
    }
}
