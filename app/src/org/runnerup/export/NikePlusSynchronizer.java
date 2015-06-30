/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.NikeXML;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList;
import org.runnerup.feed.FeedList.FeedUpdater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@TargetApi(Build.VERSION_CODES.FROYO)
public class NikePlusSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "Nike+";
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;
    private static String APP_ID = null;

    private static final String BASE_URL = "https://api.nike.com";
    private static final String LOGIN_URL = BASE_URL
            + "/nsl/v2.0/user/login?client_id=%s&client_secret=%s&app=%s";
    private static final String SYNC_URL = BASE_URL + "/v2.0/me/sync?access_token=%s";
    private static final String SYNC_COMPLETE_URL = BASE_URL
            + "/v2.0/me/sync/complete?access_token=%s";

    private static final String USER_AGENT = "NPConnect";

    private static final String PROFILE_URL = BASE_URL + "/v1.0/me/profile?access_token=%s";
    private static final String MY_FEED_URL = BASE_URL
            + "/v1.0/me/home/feed?access_token=%s&start=%d&count=%d";
    private static final String FRIEND_FEED_URL = BASE_URL
            + "/v1.0/me/friends/feed?access_token=%s&startIndex=%d&count=%d";
    long id = 0;
    private String username = null;
    private String password = null;
    private String access_token = null;
    private long expires_timeout = 0;

    NikePlusSynchronizer(SyncManager syncManager) {
        if (CLIENT_ID == null || CLIENT_SECRET == null || APP_ID == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
                APP_ID = tmp.getString("APP_ID");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
        id = config.getAsLong("_id");
        String authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authToken != null) {
            try {
                JSONObject tmp = new JSONObject(authToken);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && password != null) {
            return true;
        }
        return false;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        access_token = null;
    }

    private static long now() {
        return android.os.SystemClock.elapsedRealtime() / 1000;
    }

    @Override
    public Status connect() {
        if (now() > expires_timeout) {
            access_token = null;
        }

        if (access_token != null) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        HttpURLConnection conn = null;
        try {
            /**
             * get user id/key
             */
            String url = String.format(LOGIN_URL, CLIENT_ID, CLIENT_SECRET, APP_ID);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.addRequestProperty("Accept", "application/json");

            FormValues kv = new FormValues();
            kv.put("email", username);
            kv.put("password", password);

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                String response;
                {
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            conn.getInputStream()));
                    StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        buf.append(line);
                    }
                    response = buf.toString().replaceAll("<User>.*</User>", "\"\"");
                }
                JSONObject obj = SyncHelper.parse(new ByteArrayInputStream(response.getBytes()));

                access_token = obj.getString("access_token");
                String expires = obj.getString("expires_in");
                expires_timeout = now() + Long.parseLong(expires);
                s = Status.OK;
            }
        } catch (UnknownHostException e) {
            // probably no internet connection available
            s = Status.SKIP;
        } catch (Exception e) {
            s.ex = e;
        }

        if (conn != null) {
            conn.disconnect();
        }
        if (s.ex != null) {
            Log.e(getClass().getSimpleName(), s.ex.getMessage());
        }
        return s;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        NikeXML nikeXML = new NikeXML(db);
        GPX nikeGPX = new GPX(db);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            StringWriter xml = new StringWriter();
            nikeXML.export(mID, xml);

            StringWriter gpx = new StringWriter();
            nikeGPX.export(mID, gpx);

            String url = String.format(SYNC_URL, access_token);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);
            Part<StringWritable> part1 = new Part<StringWritable>("runXML",
                    new StringWritable(xml.toString()));
            part1.setFilename("runXML.xml");
            part1.setContentType("text/plain; charset=US-ASCII");
            part1.setContentTransferEncoding("8bit");

            Part<StringWritable> part2 = new Part<StringWritable>("gpxXML",
                    new StringWritable(gpx.toString()));
            part2.setFilename("gpxXML.xml");
            part2.setContentType("text/plain; charset=US-ASCII");
            part2.setContentTransferEncoding("8bit");

            Part<?> parts[] = {
                    part1, part2
            };
            SyncHelper.postMulti(conn, parts);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            conn.connect();

            if (responseCode != HttpStatus.SC_OK) {
                throw new Exception(amsg);
            }

            url = String.format(SYNC_COMPLETE_URL, access_token);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);

            responseCode = conn.getResponseCode();
            amsg = conn.getResponseMessage();
            conn.disconnect();
            if (responseCode == HttpStatus.SC_OK) {
                s = Status.OK;
                s.activityId = mID;
                return s;
            }

            ex = new Exception(amsg);
        } catch (Exception e) {
            ex = e;
        }

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
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
    public Status getFeed(FeedUpdater feedUpdater) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                    Locale.getDefault());
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            List<ContentValues> result = new ArrayList<ContentValues>();
            getOwnFeed(df, result);
            getFriendsFeed(df, result);
            FeedList.sort(result);
            feedUpdater.addAll(result);
            return Status.OK;
        } finally {

        }
    }

    JSONObject makeGetRequest(String url) throws MalformedURLException, IOException, JSONException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            conn.addRequestProperty("Accept", "application/json");
            conn.addRequestProperty("User-Agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = SyncHelper.parse(in);
            final int code = conn.getResponseCode();
            conn.disconnect();
            if (code == HttpStatus.SC_OK)
                return reply;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return new JSONObject();
    }

    private boolean parsePayload(ContentValues c, JSONObject p) throws NumberFormatException,
            JSONException {
        long duration = Long.parseLong(p.getString("duration")) / 1000;
        double distance = 1000 * Double.parseDouble(p.getString("distance"));
        if (duration < 0 || distance < 0) {
            return false;
        }

        if (duration > 0)
            c.put(FEED.DURATION, duration);
        if (distance > 0)
            c.put(FEED.DISTANCE, distance);

        return true;
    }

    private void getOwnFeed(SimpleDateFormat df, List<ContentValues> result) {
        JSONObject profile = null;
        try {
            profile = makeGetRequest(String.format(PROFILE_URL, access_token));
            String first = profile.getString("firstName");
            String last = profile.getString("lastName");
            String userUrl = profile.has("avatarFullUrl") ? profile.getString("avatarFullUrl") : null;
            JSONObject feed = makeGetRequest(String.format(MY_FEED_URL, access_token, 1, 25));
            JSONArray arr = feed.getJSONArray("events");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                try {
                    String type = e.getString("eventType");
                    if (!"APPLICATION.SYNC.RUN".contentEquals(type))
                        continue;

                    ContentValues c = new ContentValues();
                    c.put(FEED.ACCOUNT_ID, getId());
                    c.put(FEED.EXTERNAL_ID, e.getString("entityId"));
                    c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                    c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_RUNNING); // TODO
                    c.put(FEED.START_TIME, df.parse(e.getString("entityDate")).getTime());
                    if (e.has("payload")) {
                        JSONObject p = e.getJSONObject("payload");
                        if (!parsePayload(c, p))
                            continue; // skip this
                    }
                    c.put(FEED.USER_FIRST_NAME, first);
                    c.put(FEED.USER_LAST_NAME, last);
                    if (userUrl != null) {
                        c.put(FEED.USER_IMAGE_URL, userUrl);
                    }
                    result.add(c);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (profile != null) {
                Log.e(getClass().getSimpleName(), "Failed for profile '" + profile + "' because of the following: " + e.getMessage());
            } else {
                e.getStackTrace();
            }
        }
    }

    private void getFriendsFeed(SimpleDateFormat df, List<ContentValues> result) {
        try {
            JSONObject feed = makeGetRequest(String.format(FRIEND_FEED_URL, access_token, 1, 25));

            if (!feed.has("friends")) {
                Log.i(getClass().getSimpleName(), "No friends found, skipping their feed...");
                return;
            }
            JSONArray arr = feed.getJSONArray("friends");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i).getJSONObject("event");
                try {
                    String type = e.getString("eventType");
                    if (!"APPLICATION.SYNC.RUN".contentEquals(type))
                        continue;

                    ContentValues c = new ContentValues();
                    c.put(FEED.ACCOUNT_ID, getId());
                    c.put(FEED.EXTERNAL_ID, e.getString("entityId"));
                    c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                    c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_RUNNING); // TODO
                    c.put(FEED.START_TIME, df.parse(e.getString("entityDate")).getTime());
                    if (e.has("payload")) {
                        JSONObject p = e.getJSONObject("payload");
                        if (!parsePayload(c, p))
                            continue; // skip this
                    }
                    c.put(FEED.USER_IMAGE_URL, e.getString("avatar"));
                    result.add(c);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
