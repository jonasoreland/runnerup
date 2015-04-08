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
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunKeeperUploader extends DefaultUploader implements Uploader, OAuth2Server {

    public static final String NAME = "RunKeeper";

    /**
     * @todo register OAuth2Server
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;

    public static final String AUTH_URL = "https://runkeeper.com/apps/authorize";
    public static final String TOKEN_URL = "https://runkeeper.com/apps/token";
    public static final String REDIRECT_URI = "http://localhost:8080/runnerup/runkeeper";

    public static String REST_URL = "https://api.runkeeper.com";

    public static final String FEED_TOKEN_URL = "https://fitnesskeeperapi.com/RunKeeper/deviceApi/login";
    public static final String FEED_URL = "https://fitnesskeeperapi.com/RunKeeper/deviceApi/getFeedItems";
    public static final String FEED_ITEM_TYPES = "[ 0 ]"; // JSON array

    private long id = 0;
    private String access_token = null;
    private String fitnessActivitiesUrl = null;

    private String feed_username = null;
    private String feed_password = null;
    private String feed_access_token = null;

    static final Map<Integer, Sport> runkeeper2sportMap = new HashMap<Integer, Sport>();
    static final Map<Sport, Integer> sport2runkeeperMap = new HashMap<Sport, Integer>();
    static {
        runkeeper2sportMap.put(0, Sport.RUNNING);
        runkeeper2sportMap.put(1, Sport.BIKING);
        for (Integer i : runkeeper2sportMap.keySet()) {
            sport2runkeeperMap.put(runkeeper2sportMap.get(i), i);
        }
    }

    RunKeeperUploader(UploadManager uploadManager) {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(uploadManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (access_token == null)
            return false;
        return true;
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
            e.printStackTrace();
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
                e.printStackTrace();
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
            return getFeedAccessToken(feed_username, feed_password);
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
                uri = SyncHelper.parse(in).getString("fitness_activities");
            } catch (MalformedURLException e) {
                ex = e;
            } catch (IOException e) {
                if (REST_URL.contains("https")) {
                    REST_URL = REST_URL.replace("https", "http");
                    e.printStackTrace();
                    System.err.println(" => retry with REST_URL: " + REST_URL);
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

        if (ex != null)
            ex.printStackTrace();

        if (uri != null) {
            fitnessActivitiesUrl = uri;
            return Uploader.Status.OK;
        }
        s = Uploader.Status.ERROR;
        s.ex = ex;
        return s;
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
        Exception ex = null;
        try {
            URL newurl = new URL(REST_URL + fitnessActivitiesUrl);
            System.err.println("url: " + newurl.toString());
            conn = (HttpURLConnection) newurl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
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
            if (responseCode >= 200 && responseCode < 300) {
                s = Status.OK;
                s.activityId = mID;
                return s;
            }
            ex = new Exception(amsg);
        } catch (MalformedURLException e) {
            ex = e;
        } catch (IOException e) {
            ex = e;
        }

        if (ex != null)
            ex.printStackTrace();

        if (conn != null) {
            conn.disconnect();
        }
        s = Uploader.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        return s;
    }

    @Override
    public boolean checkSupport(Uploader.Feature f) {
        switch (f) {
            case FEED:
            case UPLOAD:
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
    public void logout() {
        this.fitnessActivitiesUrl = null;
    }

    private Status getFeedAccessToken(String username, String password) {
        Uploader.Status s = Status.OK;
        HttpURLConnection conn = null;
        try {
            URL newurl = new URL(FEED_TOKEN_URL);
            conn = (HttpURLConnection) newurl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

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

        if (s.ex != null)
            s.ex.printStackTrace();

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
                        if (e.getInt("type") != 0)
                            continue;

                        ContentValues c = new ContentValues();
                        c.put(FEED.ACCOUNT_ID, getId());
                        c.put(FEED.EXTERNAL_ID, e.getString("id"));
                        c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                        JSONObject d = e.getJSONObject("data");
                        Sport sport = runkeeper2sportMap.get(d.getInt("activityType"));
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
                            if (p.has("duration"))
                                c.put(FEED.DURATION, p.getLong("duration"));
                            if (p.has("distance"))
                                c.put(FEED.DISTANCE, p.getDouble("distance"));
                            if (p.has("notes") && p.getString("notes") != null
                                    && !p.getString("notes").equals("null"))
                                c.put(FEED.NOTES, p.getString("notes"));
                        }

                        SyncHelper.setName(c, e.getString("sourceUserDisplayName"));
                        if (e.has("sourceUserAvatarUrl")
                                && e.getString("sourceUserAvatarUrl").length() > 0) {
                            c.put(FEED.USER_IMAGE_URL, e.getString("sourceUserAvatarUrl"));
                        }

                        reply.add(c);
                        from = e.getLong("posttime");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        iter = MAX_ITER;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            } catch (JSONException e) {
                e.printStackTrace();
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
        conn.setRequestMethod("POST");
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
        if (responseCode == 200) {
            return obj;
        }
        throw new IOException(amsg);
    }
}
