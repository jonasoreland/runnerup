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

package org.runnerup.export;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathSimplifier;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Note: The HealthGraph API is depreciated

public class RunKeeperSynchronizer extends DefaultSynchronizer implements Synchronizer, OAuth2Server {

    public static final String NAME = "RunKeeper";
    private static final String PUBLIC_URL = "https://runkeeper.com";
    private Context context;
    /**
     * @todo register OAuth2Server
     */
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    private static final String AUTH_URL = "https://runkeeper.com/apps/authorize";
    private static final String TOKEN_URL = "https://runkeeper.com/apps/token";
    private static final String REDIRECT_URI = "https://localhost:8080/runnerup/runkeeper";

    private static String REST_URL = "https://api.runkeeper.com";

    private long id = 0;
    private String access_token = null;
    private String fitnessActivitiesUrl = null;
    private String userName = null;
    private PathSimplifier simplifier;

    public static final Map<String, Sport> runkeeper2sportMap = new HashMap<>();
    public static final Map<Sport, String> sport2runkeeperMap = new HashMap<>();
    static {
        //sports list can be found at http://developer.runkeeper.com/healthgraph/fitness-activities#past
        /*
         * Running, Cycling, Mountain Biking, Walking, Hiking, Downhill Skiing, Cross-Country Skiing,
         * Snowboarding, Skating, Swimming, Wheelchair, Rowing, Elliptical, Other, Yoga, Pilates,
         * CrossFit, Spinning, Zumba, Barre, Group Workout, Dance, Bootcamp, Boxing / MMA, Meditation,
         * Strength Training, Circuit Training, Core Strengthening, Arc Trainer, Stairmaster / Stepwell,
         * Sports, Nordic Walking
         */
        runkeeper2sportMap.put("Running", Sport.RUNNING);
        runkeeper2sportMap.put("Cycling", Sport.BIKING);
        runkeeper2sportMap.put("Mountain Biking", Sport.BIKING);
        runkeeper2sportMap.put("Other", Sport.OTHER);
        runkeeper2sportMap.put("Walking", Sport.WALKING);
        for (String i : runkeeper2sportMap.keySet()) {
            if (!sport2runkeeperMap.containsKey(runkeeper2sportMap.get(i))) {
                sport2runkeeperMap.put(runkeeper2sportMap.get(i), i);
            }
        }
    }

    public static final Map<String, Integer> POINT_TYPE = new HashMap<>();
    static {
        POINT_TYPE.put("start", DB.LOCATION.TYPE_START);
        POINT_TYPE.put("end", DB.LOCATION.TYPE_END);
        POINT_TYPE.put("gps", DB.LOCATION.TYPE_GPS);
        POINT_TYPE.put("pause", DB.LOCATION.TYPE_PAUSE);
        POINT_TYPE.put("resume", DB.LOCATION.TYPE_RESUME);
        POINT_TYPE.put("manual", DB.LOCATION.TYPE_GPS);
    }

    RunKeeperSynchronizer(SyncManager syncManager, PathSimplifier simplifier) {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
            } catch (Exception ex) {
                Log.e(Constants.LOG, ex.getMessage());
            }
        }
        context = syncManager.getContext();
        this.simplifier = simplifier;
    }

    @Override
    public String getClientId() {
        return CLIENT_ID;
    }

    @Override
    public String getRedirectUri() {
        return REDIRECT_URI;
    }

    @Override
    public String getClientSecret() {
        return CLIENT_SECRET;
    }

    @Override
    public String getAuthUrl() {
        return AUTH_URL;
    }

    @Override
    public String getAuthExtra() {
        return null;
    }

    @Override
    public String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    public String getRevokeUrl() {
        return null;
    }

    @Override
    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getPublicUrl() {
        return PUBLIC_URL;
    }

    @ColorRes
    @Override
    public int getColorId() {return R.color.serviceRunkeeper;}

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        id = config.getAsLong("_id");
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.optString("access_token", null);
            } catch (Exception e) {
                Log.e(Constants.LOG, e.getMessage());
            }
        }
    }

    @Override
    public boolean isConfigured() {
        return access_token != null;
    }

    @NonNull
    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("access_token", access_token);
        } catch (JSONException e) {
            Log.e(Constants.LOG, e.getMessage());
        }

        return tmp.toString();
    }

    @NonNull
    @Override
    public Intent getAuthIntent(AppCompatActivity activity) {
        return OAuth2Activity.getIntent(activity, this);
    }

    @NonNull
    @Override
    public Status getAuthResult(int resultCode, Intent data) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            String authConfig = data.getStringExtra(DB.ACCOUNT.AUTH_CONFIG);
            try {
                JSONObject obj = new JSONObject(authConfig);
                access_token = obj.getString("access_token");
                return Status.OK;
            } catch (JSONException e) {
                Log.e(Constants.LOG, e.getMessage());
            }
        }

        return Status.ERROR;
    }

    @Override
    public void reset() {
        access_token = null;
    }

    @NonNull
    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (access_token == null) {
            return s;
        }

        if (fitnessActivitiesUrl != null) {
            return Synchronizer.Status.OK;
        }

        /*
         * Get the fitnessActivities end-point
         */
        String uri = null;
        HttpURLConnection conn = null;
        Exception ex = null;
        do {
            try {
                URL newurl = new URL(REST_URL + "/user");
                conn = (HttpURLConnection) newurl.openConnection();
                conn.setRequestProperty("Authorization", "Bearer "
                        + access_token);
                conn.addRequestProperty("Content-Type", "application/vnd.com.runkeeper.User+json");
                InputStream in = new BufferedInputStream(conn.getInputStream());
                JSONObject obj = SyncHelper.parse(in);
                conn.disconnect();
                conn = null;
                uri = obj.getString("fitness_activities");
            } catch (MalformedURLException e) {
                ex = e;
            } catch (IOException e) {
                if (REST_URL.contains("https")) {
                    REST_URL = REST_URL.replace("https", "http");
                    Log.e(Constants.LOG, e.getMessage());
                    Log.e(Constants.LOG, " => retry with REST_URL: " + REST_URL);
                    continue; // retry
                }
                ex = e;
            } catch (JSONException e) {
                ex = e;
            }
            break;
        } while (true);

        if (conn != null) {
            conn.disconnect();
        }

        if (ex != null) {
            Log.e(Constants.LOG, ex.getMessage());
        }

        if (uri != null) {
            fitnessActivitiesUrl = uri;
            return Synchronizer.Status.OK;
        }
        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        return s;
    }

    @NonNull
    public Status listActivities(List<SyncActivityItem> list) {
        Status s = connect();
        if (s != Status.OK) {
            return s;
        }

        String requestUrl = REST_URL + fitnessActivitiesUrl;
        while(requestUrl != null) {
            try {
                URL nextUrl = new URL(requestUrl);
                HttpURLConnection conn = (HttpURLConnection) nextUrl.openConnection();
                conn.setDoInput(true);
                conn.setRequestMethod(RequestMethod.GET.name());
                conn.addRequestProperty("Authorization", "Bearer " + access_token);
                conn.addRequestProperty("Content-Type", "application/vnd.com.runkeeper.FitnessActivityFeed+json");

                InputStream input = new BufferedInputStream(conn.getInputStream());
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    JSONObject resp = SyncHelper.parse(input);
                    requestUrl = parseForNext(resp, list);
                    s = Status.OK;
                } else {
                    s = Status.ERROR;
                }
                input.close();
                conn.disconnect();
            } catch (IOException e) {
                Log.e(Constants.LOG, e.getMessage());
                requestUrl = null;
                s = Status.ERROR;
            } catch (JSONException e) {
                Log.e(Constants.LOG, e.getMessage());
                requestUrl = null;
                s = Status.ERROR;
            }
        }
        return s;
    }

    private String parseForNext(JSONObject resp, List<SyncActivityItem> items) throws JSONException {
        if (resp.has("items")) {
            JSONArray activities = resp.getJSONArray("items");
            for (int i = 0; i < activities.length(); i++) {
                JSONObject item = activities.getJSONObject(i);
                SyncActivityItem ai = new SyncActivityItem();

                String startTime = item.getString("start_time");
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
                try {
                    ai.setStartTime(TimeUnit.MILLISECONDS.toSeconds(format.parse(startTime).getTime()));
                } catch (ParseException e) {
                    Log.e(Constants.LOG, e.getMessage());
                    return null;
                }
                @SuppressWarnings("WrapperTypeMayBePrimitive") Float time = Float.parseFloat(item.getString("duration"));
                ai.setDuration(time.longValue());
                BigDecimal dist = BigDecimal.valueOf(Float.parseFloat(item.getString("total_distance")));
                dist = dist.setScale(2, BigDecimal.ROUND_UP);
                ai.setDistance(dist.doubleValue());
                ai.setURI(REST_URL + item.getString("uri"));
                ai.setId((long) items.size());
                String sport = item.getString("type");
                if (runkeeper2sportMap.containsKey(sport)) {
                    ai.setSport(runkeeper2sportMap.get(sport).getDbValue());
                } else {
                    ai.setSport(Sport.OTHER.getDbValue());
                }
                items.add(ai);
            }
        }
        if (resp.has("next")) {
            return REST_URL + resp.getString("next");
        }
        return null;
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        /*
         * Get the fitnessActivities end-point
         */
        HttpURLConnection conn = null;
        Exception ex;
        try {
            URL newurl = new URL(REST_URL + fitnessActivitiesUrl);
            //Log.e(Constants.LOG, "url: " + newurl.toString());
            conn = (HttpURLConnection) newurl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Authorization", "Bearer " + access_token);
            conn.addRequestProperty("Content-type",
                    "application/vnd.com.runkeeper.NewFitnessActivity+json");
            RunKeeper rk = new RunKeeper(db, simplifier);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    conn.getOutputStream()));
            rk.export(mID, w);
            w.flush();

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            String externalId = noNullStr(conn.getHeaderField("Location"));
            conn.disconnect();
            conn = null;

            if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                s.activityId = mID;
                if (!TextUtils.isEmpty(externalId)) {
                    s.externalId = externalId;
                    s.externalIdStatus = ExternalIdStatus.OK;
                }
                return s;
            }
            Log.e(getName(), "Error code: " + responseCode + ", amsg: " + amsg);
            ex = new Exception(amsg);
        } catch (Exception e) {
            ex = e;
        }

        Log.e(getName(), "Failed to upload: " + ex.getMessage());

        if (conn != null) {
            conn.disconnect();
        }
        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        return s;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public String getActivityUrl(String extId) {
        //username is part of the "web" URL but is not directly accessible in the API
        //the numeric userID is in the "User" info (see connect()), but that is not accepted in URLs
        //The userName could be retrieved from getAuthResult() too and saved in auth_config (but retries will not be handled)
        if (userName == null) {
            //try to get the information (cannot run in UI thread, use timeout)
            try {
                userName = new AsyncTask<Void, Void, String>() {

                    @Override
                    protected String doInBackground(Void... args) {
                        try {
                            URL newurl = new URL(REST_URL + "/profile");
                            HttpURLConnection conn = (HttpURLConnection) newurl.openConnection();
                            conn.setRequestProperty("Authorization", "Bearer " + access_token);
                            conn.addRequestProperty("Content-Type", "application/vnd.com.runkeeper.Profile+json");

                            InputStream in = new BufferedInputStream(conn.getInputStream());
                            JSONObject obj = SyncHelper.parse(in);
                            conn.disconnect();

                            String uri = obj.getString("profile");
                            return uri.substring(uri.lastIndexOf("/") + 1);
                        } catch (Exception e) {
                        }
                        return null;
                    }
                }.execute().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
        }
        String url;
        if (userName == null || extId == null) {
            url = null;
        } else {
            //Do not bother with fitnessActivitiesUrl
            url = PUBLIC_URL + "/user/" + userName + extId.replace("/fitnessActivities/", "/activity/");
        }
        return url;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case UPLOAD:
            case ACTIVITY_LIST:
            case GET_ACTIVITY:
                return true;
            default:
                break;
        }
        return false;
    }

    @Override
    public ActivityEntity download(SyncActivityItem item) {
        ActivityEntity activity = new ActivityEntity();
        Status s = connect();
        if (s != Status.OK) {
            return null;
        }

        HttpURLConnection conn;
        try {
            URL activityUrl = new URL(item.getURI());
            conn = (HttpURLConnection) activityUrl.openConnection();
            conn.setDoInput(true);
            conn.setRequestMethod(RequestMethod.GET.name());
            conn.addRequestProperty("Authorization", "Bearer " + access_token);
            conn.addRequestProperty("Content-type", "application/vnd.com.runkeeper.FitnessActivity+json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedInputStream input = new BufferedInputStream(conn.getInputStream());
                JSONObject resp = SyncHelper.parse(input);
                activity = RunKeeper.parseToActivity(resp, getLapLength());
            }

        } catch (IOException e) {
            Log.e(Constants.LOG, e.getMessage());
            return activity;

        } catch (JSONException e) {
            Log.e(Constants.LOG, e.getMessage());
            return activity;
        }
        return activity;
    }

    private double getLapLength() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Resources res = context.getResources();
        double lapLength = Formatter.getUnitMeters(context);
        if (prefs.getBoolean(res.getString(R.string.pref_autolap_active), false)) {
            String autoLap = prefs.getString(res.getString(R.string.pref_autolap), String.valueOf(lapLength));
            try {
                lapLength = Double.parseDouble(autoLap);
            } catch (NumberFormatException e) {
                return lapLength;
            }
            return lapLength;
        }
        return lapLength;
    }

    @Override
    public void logout() {
        this.fitnessActivitiesUrl = null;
    }
}
