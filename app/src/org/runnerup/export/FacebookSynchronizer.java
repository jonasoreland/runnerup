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
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.FacebookCourse;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.util.Bitfield;
import org.runnerup.view.FeedActivity;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FacebookSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Facebook";

    /**
     * @todo register OAuth2Server
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;

    public static final String AUTH_URL = "https://www.facebook.com/dialog/oauth";
    public static final String TOKEN_URL = "https://graph.facebook.com/oauth/access_token";
    public static final String REDIRECT_URI = "http://localhost:8080/runnerup/facebook";

    private static final String COURSE_ENDPOINT = "https://graph.facebook.com/me/objects/fitness.course";
    private static final String RUN_ENDPOINT = "https://graph.facebook.com/me/fitness.runs";
    private static final String BIKE_ENDPOINT = "https://graph.facebook.com/me/fitness.bikes";
    private static final String WALK_ENDPOINT = "https://graph.facebook.com/me/fitness.walks";

    final boolean uploadComment = false;
    final boolean explicitly_shared = false; // Doesn't work now...don't know why...

    private long id = 0;
    private String access_token = null;
    private long token_now = 0;
    private long expire_time = 0;
    private boolean skipMapInPost = false;
    private final Context context;

    final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSSZ", Locale.getDefault());

    FacebookSynchronizer(Context context, SyncManager syncManager) {
        this.context = context;
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
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
    public String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    public String getAuthExtra() {
        return "scope=publish_actions";
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
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.optString("access_token", null);
                token_now = tmp.optLong("token_now");
                expire_time = tmp.optLong("expire_time");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        id = config.getAsLong("_id");
        if (config.containsKey(DB.ACCOUNT.FLAGS)) {
            long flags = config.getAsLong(DB.ACCOUNT.FLAGS);
            if (Bitfield.test(flags, DB.ACCOUNT.FLAG_SKIP_MAP)) {
                skipMapInPost = true;
            }
        }
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("access_token", access_token);
            tmp.put("token_now", token_now);
            tmp.put("expire_time", expire_time);
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
            try {
                String authConfig = data.getStringExtra(DB.ACCOUNT.AUTH_CONFIG);
                Uri uri = Uri.parse("http://keso?" + authConfig);
                access_token = uri.getQueryParameter("access_token");
                expire_time = Long.valueOf(uri.getQueryParameter("expires"));
                token_now = System.currentTimeMillis();
                return Status.OK;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return Status.ERROR;
    }

    @Override
    public boolean isConfigured() {
        if (access_token == null)
            return false;

        return true;
    }

    @Override
    public void reset() {
        access_token = null;
        expire_time = 0;
        token_now = 0;
    }

    public static final long ONE_DAY = 24 * 60 * 60;

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (access_token == null)
            return s;

        long endTime = (token_now + 1000 * expire_time) / 1000;
        long now = System.currentTimeMillis() / 1000;

        if (now + ONE_DAY > endTime) {

            Log.e(getName(), "now: " + now + ", endTime: " + endTime + ", now + ONE_DAY: "
                    + (now + ONE_DAY) + " => needs refresh");
            return s;
        }

        return Synchronizer.Status.OK;
    }

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        FacebookCourse courseFactory = new FacebookCourse(context, db);
        try {
            JSONObject runObj = new JSONObject();
            JSONObject course = courseFactory.export(mID, !skipMapInPost, runObj);
            JSONObject ref = createCourse(course);

            Log.e(getName(), "createdCourseObj: " + ref.toString());

            try {
                JSONObject ret = createRun(ref, runObj);
                Log.e(getName(), "createdRunObj: " + ret.toString());
                s = Status.OK;
                s.activityId = mID;
                return s;
            } catch (Exception e) {
                Log.e(getName(), "fail createdRunObj: " + e);
                s.ex = e;
            }
            deleteCourse(ref);
        } catch (Exception e) {
            Log.e(getName(), "fail createdCourseObj: " + e);
            s.ex = e;
        }

        s = Status.ERROR;
        if (s.ex != null)
            s.ex.printStackTrace();
        return s;
    }

    private JSONObject createObj(URL url, Part<?> parts[]) throws Exception{
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        SyncHelper.postMulti(conn, parts);

        int code = conn.getResponseCode();
        String msg = conn.getResponseMessage();

        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new Exception("Got code: " + code + ", msg: " + msg + " from " + url.toString());
        } else {
            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject runRef = SyncHelper.parse(in);

            conn.disconnect();
            return runRef;
        }
    }
    private JSONObject createCourse(JSONObject course) throws JSONException,
            IOException, Exception {
        JSONObject obj = new JSONObject();
        /* create a facebook course instance */
        obj.put("fb:app_id", FacebookSynchronizer.CLIENT_ID);
        obj.put("og:type", "fitness.course");
        obj.put("og:title", "a RunnerUp course");
        obj.put("fitness", course);

        Part<StringWritable> themePart = new Part<StringWritable>(
                "access_token", new StringWritable(
                        SyncHelper.URLEncode(access_token)));
        Part<StringWritable> payloadPart = new Part<StringWritable>("object",
                new StringWritable(obj.toString()));
        Part<?> parts[] = {
                themePart, payloadPart
        };

        return createObj(new URL(COURSE_ENDPOINT), parts);
    }

    private static URL getSportEndPoint(Sport s) throws Exception {
        if (s.IsWalking()) return new URL(WALK_ENDPOINT);
        else if (s.IsRunning()) return new URL(RUN_ENDPOINT);
        else if (s.IsCycling()) return new URL(BIKE_ENDPOINT);
        return null;
    }


    private JSONObject createRun(JSONObject ref, JSONObject runObj) throws Exception {
        Sport sport = Sport.valueOf(runObj.optInt("sport", DB.ACTIVITY.SPORT_OTHER));
        URL url = getSportEndPoint(sport);
        if (url == null) {
            /* only running/biking/walking and similar are supported */
            return null;
        }
        String id = ref.getString("id");
        ArrayList<Part<?>> list = new ArrayList<Part<?>>();
        list.add(new Part<StringWritable>("access_token",
                new StringWritable(SyncHelper.URLEncode(access_token))));
        list.add(new Part<StringWritable>("course",
                new StringWritable(id)));
        if (explicitly_shared)
            list.add(new Part<StringWritable>(
                    "fb:explicitly_shared", new StringWritable("true")));

        list.add(new Part<StringWritable>(
                "start_time", new StringWritable(formatTime(runObj.getLong("startTime")))));
        if (runObj.has("endTime")) {
            list.add(new Part<StringWritable>(
                    "end_time", new StringWritable(formatTime(runObj.getLong("endTime")))));
        }

        if (uploadComment && runObj.has("comment")) {
            list.add(new Part<StringWritable>(
                    "message", new StringWritable(runObj.getString("comment"))));
        }

        Part<?> parts[] = new Part<?>[list.size()];
        list.toArray(parts);

        URL url2 = new URL(sport.IsCycling() ? BIKE_ENDPOINT : RUN_ENDPOINT);
        return createObj(url2, parts);
    }

    private String formatTime(long long1) {
        return dateFormat.format(new Date(1000 * long1));
    }

    private void deleteCourse(JSONObject ref) {
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case SKIP_MAP:
            case UPLOAD:
                return true;
            case FEED:
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case LIVE:
                break;
        }

        return false;
    }

    @Override
    public void logout() {
    }
}
