package org.runnerup.export;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.GoogleFitData;
import org.runnerup.workout.WorkoutSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by LFAJER on 2015-01-29.
 */
public class GoogleFitUploader extends GooglePlus implements Uploader {

    public static final String NAME = "GoogleFit";
    private final Context context;

    public static final String REST_URL = "https://www.googleapis.com/fitness/v1/users/me/dataSources";

    private static final String SCOPES =
            " https://www.googleapis.com/auth/fitness.activity.write " +
                    "https://www.googleapis.com/auth/fitness.activity.read " +
                    "https://www.googleapis.com/auth/fitness.body.write " +
                    "https://www.googleapis.com/auth/fitness.body.read " +
                    "https://www.googleapis.com/auth/fitness.location.write " +
                    "https://www.googleapis.com/auth/fitness.location.read";

    GoogleFitUploader(Context ctx, UploadManager uploadManager) {
        super(uploadManager);
        this.context = ctx;
    }

    @Override
    public String getScopes() {
        return SCOPES;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {

        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        GoogleFitData gfd = new GoogleFitData(db, context);
        List<String> presentDataSources = listExistingDataSources();

        HttpURLConnection connect = null;
        Exception ex = null;
        try {
            URL url = new URL(REST_URL);
            connect = (HttpURLConnection) url.openConnection();
            connect.setDoOutput(true);
            connect.setRequestMethod("POST");
            connect.addRequestProperty("Authorization", "Bearer " + getAccessToken());
            connect.addRequestProperty("Content-Type", "application/json;encoding=utf-8");
            connect.setRequestProperty("charset", "utf-8");

            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(connect.getOutputStream(), "UTF-8"));

            List<GoogleFitData.DataSourceType> activitySources = gfd.getActivityDataSourceTypes(mID);
            for (GoogleFitData.DataSourceType type : activitySources) {
                if (!presentDataSources.contains(type.getDataStreamId())) {
                    gfd.exportDataSource(type, w);
                    w.flush();
                    w.close();
                }
                if (connect.getResponseCode() == 200) {
                    System.out.println("It's OK!!!");
                }
            }

            connect.disconnect();

            ex = new Exception(connect.getResponseMessage());
        } catch (IOException e) {
            ex = e;
        }

        return s;
    }

    private void updateDataSourcesFromActivity(List<GoogleFitData.DataSourceType> dataSources) {

    }

    @Override
    public boolean checkSupport(Feature f) {

        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case WORKOUT_LIST:
            case GET_WORKOUT:
            case SKIP_MAP:
                return false;
        }

        return false;
    }

    @Override
    public String getAuthExtra() {
        return "scope=" + FormCrawler.URLEncode(getScopes());
    }

    public List<String> listExistingDataSources() {

        HttpURLConnection conn = null;
        Exception ex = null;
        List<String> dataStreamIds = new ArrayList<String>();
        try {
            conn = (HttpURLConnection) new URL(REST_URL).openConnection();
            conn.setRequestProperty("Authorization", "Bearer "
                    + getAccessToken());
            conn.setRequestMethod("GET");
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = parse(in);
            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode == 200) {
                JSONArray data = reply.getJSONArray("dataSource");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject dataSource = data.getJSONObject(i);
                    dataStreamIds.add(dataSource.getString("dataStreamId"));
                }
                return dataStreamIds;
            }
            ex = new Exception(conn.getResponseMessage());
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }
        return dataStreamIds;
    }

}
