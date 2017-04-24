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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.Formatter;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunKeeperSynchronizer extends DefaultSynchronizer implements Synchronizer, OAuth2Server {

    public static final String NAME = "RunKeeper";
    private static Context context = null;
    /**
     * @todo register OAuth2Server
     */
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    private static final String AUTH_URL = "https://runkeeper.com/apps/authorize";
    private static final String TOKEN_URL = "https://runkeeper.com/apps/token";
    private static final String REDIRECT_URI = "http://localhost:8080/runnerup/runkeeper";

    private static String REST_URL = "https://api.runkeeper.com";

    private static final String FEED_TOKEN_URL = "https://fitnesskeeperapi.com/RunKeeper/deviceApi/login";
    private static final String FEED_URL = "https://fitnesskeeperapi.com/RunKeeper/deviceApi/getFeedItems";
    private static final String FEED_ITEM_TYPES = "[ 0 ]"; // JSON array

    private long id = 0;
    private String access_token = null;
    private String fitnessActivitiesUrl = null;

    private String feed_username = null;
    private String feed_password = null;
    private String feed_access_token = null;

    public static final Map<String, Sport> runkeeper2sportMap = new HashMap<String, Sport>();
    public static final Map<Sport, String> sport2runkeeperMap = new HashMap<Sport, String>();
    static {
        //sports list can be found at http://developer.runkeeper.com/healthgraph/fitness-activities#past
        /**
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

    public static final Map<String, Integer> POINT_TYPE = new HashMap<String, Integer>();
    static {
        POINT_TYPE.put("start", DB.LOCATION.TYPE_START);
        POINT_TYPE.put("end", DB.LOCATION.TYPE_END);
        POINT_TYPE.put("gps", DB.LOCATION.TYPE_GPS);
        POINT_TYPE.put("pause", DB.LOCATION.TYPE_PAUSE);
        POINT_TYPE.put("resume", DB.LOCATION.TYPE_RESUME);
        POINT_TYPE.put("manual", DB.LOCATION.TYPE_GPS);
    }

    public RunKeeperSynchronizer(SyncManager syncManager) {
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

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        id = config.getAsLong("_id");
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.optString("access_token", null);
                feed_access_token = tmp.optString("feed_access_token", null);
                if (feed_access_token == null) {
                    feed_username = tmp.optString("username", null);
                    feed_password = tmp.optString("password", null);
                } else {
                    feed_username = null;
                    feed_password = null;
                }
            } catch (Exception e) {
                Log.e(Constants.LOG, e.getMessage());
            }
        }
    }

    @Override
    public boolean isConfigured() {
        return access_token != null;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("access_token", access_token);
            tmp.put("feed_access_token", feed_access_token);
            if (feed_access_token == null) {
                tmp.put("username", feed_username);
                tmp.put("password", feed_password);
            } else {
                tmp.put("username", null);
                tmp.put("password", null);
            }
        } catch (JSONException e) {
            Log.e(Constants.LOG, e.getMessage());
        }

        return tmp.toString();
    }

    @Override
    public Intent getAuthIntent(Activity activity) {
        return OAuth2Activity.getIntent(activity, this);
    }

    @Override
    public Status getAuthResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            String authConfig = data.getStringExtra(DB.ACCOUNT.AUTH_CONFIG);
            try {
                access_token = new JSONObject(authConfig).getString("access_token");
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
        feed_access_token = null;
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (access_token == null) {
            return s;
        }

        if (feed_access_token == null && (feed_username != null && feed_password != null)) {
            return getFeedAccessToken();
        }

        if (fitnessActivitiesUrl != null) {
            return Synchronizer.Status.OK;
        }

        /**
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
                InputStream in = new BufferedInputStream(conn.getInputStream());
                uri = SyncHelper.parse(in).getString("fitness_activities");
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
                Float time = Float.parseFloat(item.getString("duration"));
                ai.setDuration(time.longValue());
                BigDecimal dist = new BigDecimal(Float.parseFloat(item.getString("total_distance")));
                dist = dist.setScale(2, BigDecimal.ROUND_UP);
                ai.setDistance(dist.floatValue());
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

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        /**
         * Get the fitnessActivities end-point
         */
        HttpURLConnection conn = null;
        Exception ex;
        try {
            URL newurl = new URL(REST_URL + fitnessActivitiesUrl);
            Log.e(Constants.LOG, "url: " + newurl.toString());
            conn = (HttpURLConnection) newurl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Authorization", "Bearer " + access_token);
            conn.addRequestProperty("Content-type",
                    "application/vnd.com.runkeeper.NewFitnessActivity+json");
            RunKeeper rk = new RunKeeper(db);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    conn.getOutputStream()));
            rk.export(mID, w);
            w.flush();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            conn.disconnect();
            conn = null;
            if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                s = Status.OK;
                s.activityId = mID;
                return s;
            }
            ex = new Exception(amsg);
        } catch (Exception e) {
            ex = e;
        }

        if (ex != null) {
            Log.e(getName(), "Failed to upload: " + ex.getMessage());
        }

        if (conn != null) {
            conn.disconnect();
        }
        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        return s;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case FEED:
            case UPLOAD:
            case ACTIVITY_LIST:
            case GET_ACTIVITY:
                return true;
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case LIVE:
            case SKIP_MAP:
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

        HttpURLConnection conn = null;
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

    private Status getFeedAccessToken() {
        Synchronizer.Status s = Status.OK;
        HttpURLConnection conn = null;
        try {
            URL newurl = new URL(FEED_TOKEN_URL);
            conn = (HttpURLConnection) newurl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());

            FormValues kv = new FormValues();
            kv.put("email", feed_username);
            kv.put("password", feed_password);

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();
            }

            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            feed_access_token = obj.getString("accessToken");
            return s;
        } catch (MalformedURLException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (ProtocolException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (IOException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s = Status.NEED_AUTH;
            s.authMethod = AuthMethod.USER_PASS;
            s.ex = e;
        }

        Log.e(Constants.LOG, s.ex.getMessage());

        return s;
    }

    //TODO This is not according to RunKeeper documentation, probably not working
    @Override
    public Status getFeed(FeedUpdater feedUpdater) {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS;
        if (feed_access_token == null) {
            return s;
        }

        List<ContentValues> reply = new ArrayList<ContentValues>();
        long from = System.currentTimeMillis();
        final int MAX_ITER = 5;
        for (int iter = 0; iter < MAX_ITER && reply.size() < 25; iter++) {
            try {
                JSONObject feed = requestFeed(from);
                JSONArray arr = feed.getJSONArray("feedItems");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.getJSONObject(i);
                    try {
                        if (e.getInt("type") != 0) {
                            continue;
                        }

                        ContentValues c = new ContentValues();
                        c.put(FEED.ACCOUNT_ID, getId());
                        c.put(FEED.EXTERNAL_ID, e.getString("id"));
                        c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                        JSONObject d = e.getJSONObject("data");
                        Sport sport = runkeeper2sportMap.get(d.getString("activityType"));
                        if (sport != null) {
                            c.put(FEED.FEED_SUBTYPE, sport.getDbValue());
                        } else {
                            Log.w(getName(), "Unknown sport with id " + d.getString("activityType"));
                            c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_OTHER);
                            break;
                        }
                        c.put(FEED.START_TIME, e.getLong("posttime"));
                        c.put(FEED.FLAGS, "brokenStartTime"); // BUH!!
                        if (e.has("data")) {
                            JSONObject p = e.getJSONObject("data");
                            if (p.has("duration")) {
                                c.put(FEED.DURATION, p.getLong("duration"));
                            }
                            if (p.has("distance")) {
                                c.put(FEED.DISTANCE, p.getDouble("distance"));
                            }
                            if (p.has("notes") && p.getString("notes") != null
                                    && !p.getString("notes").equals("null")) {
                                c.put(FEED.NOTES, p.getString("notes"));
                            }
                        }

                        SyncHelper.setName(c, e.getString("sourceUserDisplayName"));
                        if (e.has("sourceUserAvatarUrl")
                                && e.getString("sourceUserAvatarUrl").length() > 0) {
                            c.put(FEED.USER_IMAGE_URL, e.getString("sourceUserAvatarUrl"));
                        }

                        reply.add(c);
                        from = e.getLong("posttime");
                    } catch (Exception ex) {
                        Log.e(Constants.LOG, ex.getMessage());
                        iter = MAX_ITER;
                    }
                }
            } catch (IOException e) {
                Log.e(Constants.LOG, e.getMessage());
                break;
            } catch (JSONException e) {
                Log.e(Constants.LOG, e.getMessage());
                break;
            }
        }
        feedUpdater.addAll(reply);

        return Status.OK;
    }

    JSONObject requestFeed(long from) throws IOException, JSONException {
        URL newurl = new URL(FEED_URL);
        HttpURLConnection conn = (HttpURLConnection) newurl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        conn.addRequestProperty("Authorization", "Bearer " + feed_access_token);

        FormValues kv = new FormValues();
        kv.put("lastPostTime", Long.toString(from));
        kv.put("feedItemTypes", FEED_ITEM_TYPES);

        {
            OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
            kv.write(wr);
            wr.flush();
            wr.close();
        }

        int responseCode = conn.getResponseCode();
        String amsg = conn.getResponseMessage();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        JSONObject obj = SyncHelper.parse(in);

        conn.disconnect();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return obj;
        }
        throw new IOException(amsg);
    }
}
