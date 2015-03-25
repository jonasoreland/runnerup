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
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityValues;
import org.runnerup.db.entities.IObjectValues;
import org.runnerup.db.entities.LapValues;
import org.runnerup.db.entities.LocationValues;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunKeeperUploader extends FormCrawler implements Uploader, OAuth2Server {

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

    private static final int ONE_THOUSEND = 1000;

    static final Map<Integer, Sport> RK2SPORT = new HashMap<Integer, Sport>();
    static {
        RK2SPORT.put(0, Sport.RUNNING);
        RK2SPORT.put(1, Sport.BIKING);
    }

    static final Map<String, Integer> SPORT_MAP = new HashMap<String, Integer>();
    static {
        SPORT_MAP.put("Running", Sport.RUNNING.getDbValue());
        SPORT_MAP.put("Cycling", Sport.BIKING.getDbValue());
        SPORT_MAP.put("Mountain Biking", Sport.BIKING.getDbValue());
    }

    static final Map<String, Integer> POINT_TYPE = new HashMap<String, Integer>();
    static {
        POINT_TYPE.put("start", DB.LOCATION.TYPE_START);
        POINT_TYPE.put("end", DB.LOCATION.TYPE_END);
        POINT_TYPE.put("gps", DB.LOCATION.TYPE_GPS);
        POINT_TYPE.put("pause", DB.LOCATION.TYPE_PAUSE);
        POINT_TYPE.put("resume", DB.LOCATION.TYPE_RESUME);
        POINT_TYPE.put("manual", DB.LOCATION.TYPE_GPS);
    }

    public RunKeeperUploader(UploadManager uploadManager) {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(uploadManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
            } catch (Exception ex) {
                Log.e(Constants.LOG, ex.getMessage());
            }
        }
        context = uploadManager.getContext();
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
            return Uploader.Status.OK;
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
                uri = parse(in).getString("fitness_activities");
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
            return Uploader.Status.OK;
        }
        s = Uploader.Status.ERROR;
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
                if (conn.getResponseCode() == HttpStatus.SC_OK) {
                    JSONObject resp = parse(input);
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
                    ai.setStartTime(format.parse(startTime).getTime()/ONE_THOUSEND);
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
                if (SPORT_MAP.containsKey(sport)) {
                    ai.setSport(SPORT_MAP.get(sport));
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
    public Pair<Status, Long> upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return Pair.create(s, mID);
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
            if (responseCode >= HttpStatus.SC_OK && responseCode < HttpStatus.SC_MULTIPLE_CHOICES) {
                return Pair.create(Uploader.Status.OK, mID);
            }
            ex = new Exception(amsg);
        } catch (MalformedURLException e) {
            ex = e;
        } catch (IOException e) {
            ex = e;
        }

        if (ex != null) {
            Log.e(Constants.LOG, ex.getMessage());
        }

        if (conn != null) {
            conn.disconnect();
        }
        s = Uploader.Status.ERROR;
        s.ex = ex;
        return Pair.create(s, mID);
    }

    @Override
    public boolean checkSupport(Uploader.Feature f) {
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
    public Pair<Status, Long> download(SQLiteDatabase db, SyncActivityItem item) {
        Long newId = -1L;
        Status s = connect();
        if (s != Status.OK) {
            return Pair.create(s, newId);
        }

        HttpURLConnection conn = null;
        try {
            URL activityUrl = new URL(item.getURI());
            conn = (HttpURLConnection) activityUrl.openConnection();
            conn.setDoInput(true);
            conn.setRequestMethod(RequestMethod.GET.name());
            conn.addRequestProperty("Authorization", "Bearer " + access_token);
            conn.addRequestProperty("Content-type", "application/vnd.com.runkeeper.FitnessActivity+json");

            if (conn.getResponseCode() == HttpStatus.SC_OK) {
                BufferedInputStream input = new BufferedInputStream(conn.getInputStream());
                JSONObject resp = parse(input);
                newId = persistActivity(resp, item, db);
            }
            if (newId < 0) {
                return Pair.create(Status.ERROR, newId);
            } else {
                return Pair.create(Status.OK, newId);
            }
        } catch (IOException e) {
            Log.e(Constants.LOG, e.getMessage());
            s = Status.ERROR;

        } catch (JSONException e) {
            Log.e(Constants.LOG, e.getMessage());
            s = Status.ERROR;
        }
        return Pair.create(s, newId);
    }

    private Long persistActivity(JSONObject response, SyncActivityItem ai, SQLiteDatabase db) throws JSONException {
        // start transaction to make rollback possible in case of exceptions
        db.beginTransaction();

        ActivityValues newActivity = new ActivityValues();
        newActivity.setSport(SPORT_MAP.get(response.getString("type")));
        newActivity.setStartTime(ai.getStartTime());
        newActivity.setTime(ai.getDuration());
        newActivity.setDistance(ai.getDistance());

        newActivity.setId(newActivity.insert(db));
        // if activity inserted properly than proceed
        if (newActivity.getId() != null && newActivity.getId() > -1) {

            List<IObjectValues> laps = new ArrayList<IObjectValues>();
            List<IObjectValues> locations = new ArrayList<IObjectValues>();

            JSONArray distance = response.getJSONArray("distance");
            JSONArray path = response.getJSONArray("path");
            JSONArray hr = response.getJSONArray("heart_rate");

            SortedMap<Long, HashMap<String, String>> pointsValueMap = createPointsMap(distance, path, hr);
            Iterator<Map.Entry<Long, HashMap<String, String>>> points = pointsValueMap.entrySet().iterator();

            //lap hr
            int maxHr = 0;
            int sumHr = 0;
            int count = 0;
            //point speed
            long time = 0;
            float meters = 0.0f;
            //activity hr
            int maxHrOverall = 0;
            int sumHrOverall = 0;
            int countOverall = 0;

            while (points.hasNext()) {
                Map.Entry<Long, HashMap<String, String>> timePoint = points.next();
                HashMap<String, String> values = timePoint.getValue();

                LocationValues lv = new LocationValues();
                lv.setActivityId(newActivity.getId());
                lv.setTime(ai.getStartTime() + timePoint.getKey());

                String dist = values.get("distance");
                String lat = values.get("latitude");
                String lon = values.get("longitude");
                String alt = values.get("altitude");
                String heart = values.get("heart_rate");
                String type = values.get("type");

                if (lat != null) {
                    lv.setLatitude(Double.valueOf(lat));
                }
                if (lon != null) {
                    lv.setLongitude(Double.valueOf(lon));
                }
                if (alt != null) {
                    lv.setAltitude(Double.valueOf(alt));
                }
                if (type != null) {
                    lv.setType(POINT_TYPE.get(type));
                }
                // lap and activity max and avg hr
                if (heart != null) {
                    lv.setHr(Integer.valueOf(heart));
                    maxHr = Math.max(maxHr, lv.getHr());
                    maxHrOverall = Math.max(maxHrOverall, lv.getHr());
                    sumHr += lv.getHr();
                    sumHrOverall += lv.getHr();
                    count++;
                    countOverall++;
                }

                meters = Float.valueOf(dist) - meters;
                time = timePoint.getKey() - time;
                if (time > 0) {
                    float speed = meters / ((float)time / ONE_THOUSEND);
                    BigDecimal s = new BigDecimal(speed);
                    s = s.setScale(2, BigDecimal.ROUND_UP);
                    lv.setSpeed(s.floatValue());
                }

                // create lap if distance greater than configured lap distance
                double unitMeters = Formatter.getUnitMeters(context);
                if (Float.valueOf(dist) >= unitMeters * laps.size()) {
                    LapValues newLap = new LapValues();
                    newLap.setLap(laps.size());
                    newLap.setDistance(Float.valueOf(dist));
                    newLap.setTime(timePoint.getKey().intValue() / ONE_THOUSEND);
                    newLap.setActivityId(newActivity.getId());
                    laps.add(newLap);

                    // update previous lap with duration and distance
                    if (laps.size() > 1) {
                        LapValues previousLap = (LapValues) laps.get(laps.size() - 2);
                        previousLap.setDistance(Float.valueOf(dist) - previousLap.getDistance());
                        previousLap.setTime((int) (timePoint.getKey() / ONE_THOUSEND) - previousLap.getTime());

                        if (hr != null && hr.length() > 0) {
                            previousLap.setMaxHr(maxHr);
                            previousLap.setAvgHr(sumHr / count);
                        }
                        maxHr = 0;
                        sumHr = 0;
                        count = 0;
                    }
                }
                // update last lap with duration and distance
                if (!points.hasNext()) {
                    LapValues previousLap = (LapValues) laps.get(laps.size() - 1);
                    previousLap.setDistance(Float.valueOf(dist) - previousLap.getDistance());
                    previousLap.setTime((int) (timePoint.getKey() / ONE_THOUSEND) - previousLap.getTime());

                    if (hr != null && hr.length() > 0) {
                        previousLap.setMaxHr(maxHr);
                        previousLap.setAvgHr(sumHr / count);
                    }
                }

                lv.setLap(laps.size()-1);

                locations.add(lv);
            }
            // calculate avg and max hr
            // update the activity
            newActivity.setMaxHr(maxHrOverall);
            if (countOverall > 0) {
                newActivity.setAvgHr(sumHrOverall / countOverall);
            }
            newActivity.update(db);

            // insert location and end transaction unsuccessfully
            if (DBHelper.bulkInsert(locations, db) != locations.size()) {
                db.endTransaction();
                return -1L;
            }

            // insert all lap objects
            if (DBHelper.bulkInsert(laps, db) != laps.size()) {
                db.endTransaction();
                return -1L;
            }

            // successfully end transaction
            db.setTransactionSuccessful();
            db.endTransaction();
        } else {
            //end transaction unsuccessfully
            db.endTransaction();
        }
        return newActivity.getId();
    }

    private SortedMap<Long, HashMap<String, String>> createPointsMap(JSONArray distance, JSONArray path, JSONArray hr) throws JSONException {
        SortedMap<Long, HashMap<String, String>> result = new TreeMap<Long, HashMap<String, String>>();

        if (distance != null && distance.length() > 0) {
            for (int i = 0; i < distance.length(); i++) {
                JSONObject o = distance.getJSONObject(i);
                Long key = (long) (Float.valueOf(o.getString("timestamp"))*ONE_THOUSEND);
                HashMap<String, String> value = new HashMap<String, String>();
                String valueMapKey = "distance";
                String valueMapValue = o.getString(valueMapKey);
                value.put(valueMapKey, valueMapValue);
                result.put(key, value);
            }
        }

        if (path != null && path.length() > 0) {
            for (int i = 0; i < path.length(); i++) {
                JSONObject o = path.getJSONObject(i);
                Long key = (long) (Float.valueOf(o.getString("timestamp"))*ONE_THOUSEND);
                HashMap<String, String> value = result.get(key);
                if (value == null) {
                    value = new HashMap<String, String>();
                }
                String[] attrs = new String[] {"latitude", "longitude", "altitude", "type"};
                for (String valueMapKey : attrs) {
                    String valueMapValue = o.getString(valueMapKey);
                    value.put(valueMapKey, valueMapValue);
                }
                result.put(key, value);
            }
        }

        if (hr != null && hr.length() > 0) {
            for (int i = 0; i < hr.length(); i++) {
                JSONObject o = hr.getJSONObject(i);
                Long key = (long) (Float.valueOf(o.getString("timestamp"))*ONE_THOUSEND);
                HashMap<String, String> value = result.get(key);
                if (value == null) {
                    value = new HashMap<String, String>();
                }
                String valueMapKey = "heart_rate";
                String valueMapValue = o.getString(valueMapKey);
                value.put(valueMapKey, valueMapValue);
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public void logout() {
        this.fitnessActivitiesUrl = null;
    }

    private Status getFeedAccessToken() {
        Uploader.Status s = Status.OK;
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
            JSONObject obj = parse(in);
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
                        Sport sport = RK2SPORT.get(d.getInt("activityType"));
                        if (sport != null) {
                            c.put(FEED.FEED_SUBTYPE, sport.getDbValue());
                        } else {
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

                        setName(c, e.getString("sourceUserDisplayName"));
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

    @Override
    public Status refreshToken() {
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
        JSONObject obj = parse(in);

        conn.disconnect();
        if (responseCode == HttpStatus.SC_OK) {
            return obj;
        }
        throw new IOException(amsg);
    }
}
