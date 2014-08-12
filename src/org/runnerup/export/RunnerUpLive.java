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

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.Formatter;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunnerUpLive extends FormCrawler implements Uploader {

    public static final String NAME = "RunnerUp LIVE";

    private String authUrl = null;
    private String authType = null;
    private String username = null;
    private String password = null;
    private String postUrl = null;
    private Formatter formatter;
    private boolean _loggedin;

    long id = 0;

    RunnerUpLive(UploadManager uploadManager) {
        if (postUrl == null || username == null) {
            try {
                JSONObject tmp = new JSONObject(uploadManager.loadData(this));
                username = tmp.getString("username");
                postUrl = tmp.getString("postUrl");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        formatter = new Formatter(uploadManager.getContext());
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
        String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);

        if (auth != null) {
            try {
                JSONObject tmp = new JSONObject(auth);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
                authUrl = tmp.optString("authurl", null);
                postUrl = tmp.optString("posturl", null);
                authType = tmp.optString("authtype", null);
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
            tmp.put("authurl", authUrl);
            tmp.put("posturl", postUrl);
            tmp.put("authtype", authType);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
    }

    @Override
    public Status connect() {
        if (!isConfigured()) {
            // user/pass needed
            Status s = Status.NEED_AUTH;
            s.authMethod = Uploader.AuthMethod.RUNNERUP_LIVE;
            return s;
        }

        if (authType.equals("user")) {
            _loggedin = true;
        }

        if (_loggedin) {
            return Uploader.Status.OK;
        }

        JSONObject credentials = new JSONObject();
        try {
            credentials.put("userName", username);
            credentials.put("password", password);
            credentials.put("authurl", authUrl);
            credentials.put("posturl", postUrl);
            credentials.put("authtype", authType);
        } catch (JSONException e) {
            e.printStackTrace();
            return Uploader.Status.INCORRECT_USAGE;
        }

        Status errorStatus = Status.ERROR;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(authUrl)
                    .openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream out = conn.getOutputStream();
            out.write(credentials.toString().getBytes());
            out.flush();
            out.close();

            /*
             * A success message looks like:
             * <response><result>success</result></response> A failure message
             * looks like: <response><error code="1102"
             * message="Login or Password is not correct" /></response> For
             * flexibility (and ease), we won't do full XML parsing here. We'll
             * simply look for a few key tokens and hope that's good enough.
             */
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = in.readLine();

            if (conn.getResponseCode() == 200) {
                if (line.contains("success")) {
                    _loggedin = true;
                    return Status.OK;
                } else {
                    Status s = Status.NEED_AUTH;
                    s.authMethod = Uploader.AuthMethod.USER_PASS;
                    return s;
                }
            }
            conn.disconnect();
        } catch (Exception ex) {
            errorStatus.ex = ex;
            ex.printStackTrace();
        }
        return errorStatus;
    }

    // translate between constants used in RunnerUp and those that weide choose
    // to use for his live server
    private int translateType(int type) {
        switch (type) {
            case DB.LOCATION.TYPE_START:
            case DB.LOCATION.TYPE_RESUME:
                return 0;
            case DB.LOCATION.TYPE_GPS:
                return 1;
            case DB.LOCATION.TYPE_PAUSE:
                return 2;
            case DB.LOCATION.TYPE_END:
                return 3;
            case DB.LOCATION.TYPE_DISCARD:
                return 6;
        }
        assert (false);
        return 0;
    }

    @Override
    public void liveLog(Context context, Location location, int type, double mElapsedDistanceMeter,
            double mElapsedTimeMillis) {
        int externalType = translateType(type);
        long elapsedDistanceMeter = Math.round(mElapsedDistanceMeter);
        Intent msgIntent = new Intent(context, LiveService.class);
        msgIntent.putExtra(LiveService.PARAM_IN_LAT, location.getLatitude());
        msgIntent.putExtra(LiveService.PARAM_IN_LONG, location.getLongitude());
        msgIntent.putExtra(LiveService.PARAM_IN_ALTITUDE, location.getAltitude());
        msgIntent.putExtra(LiveService.PARAM_IN_TYPE, externalType);
        msgIntent.putExtra(LiveService.PARAM_IN_ELAPSED_DISTANCE, formatter
                .formatDistance(Formatter.TXT_LONG, elapsedDistanceMeter));
        msgIntent.putExtra(
                LiveService.PARAM_IN_ELAPSED_TIME,
                formatter.formatElapsedTime(Formatter.TXT_LONG,
                        Math.round(mElapsedTimeMillis / 1000)));
        msgIntent.putExtra(
                LiveService.PARAM_IN_PACE,
                formatter.formatPace(Formatter.TXT_SHORT, mElapsedTimeMillis
                        / (1000 * mElapsedDistanceMeter)));
        msgIntent.putExtra(LiveService.PARAM_IN_USERNAME, username);
        msgIntent.putExtra(LiveService.PARAM_IN_PASSWORD, password);
        msgIntent.putExtra(LiveService.PARAM_IN_SERVERADRESS, postUrl);
        context.startService(msgIntent);
    }

    public static class LiveService extends IntentService {

        public static final String PARAM_IN_ELAPSED_DISTANCE = "dist";
        public static final String PARAM_IN_ELAPSED_TIME = "time";
        public static final String PARAM_IN_PACE = "pace";
        public static final String PARAM_IN_USERNAME = "username";
        public static final String PARAM_IN_PASSWORD = "password";
        public static final String PARAM_IN_SERVERADRESS = "serveradress";
        public static final String PARAM_IN_LAT = "lat";
        public static final String PARAM_IN_LONG = "long";
        public static final String PARAM_IN_ALTITUDE = "altitude";
        public static final String PARAM_IN_TYPE = "type";

        public LiveService() {
            super("LiveService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            String mElapsedDistance = intent
                    .getStringExtra(PARAM_IN_ELAPSED_DISTANCE);
            String mElapsedTime = intent.getStringExtra(PARAM_IN_ELAPSED_TIME);
            String pace = intent.getStringExtra(PARAM_IN_PACE);
            String username = intent.getStringExtra(PARAM_IN_USERNAME);
            String password = intent.getStringExtra(PARAM_IN_PASSWORD);
            double lat = intent.getDoubleExtra(PARAM_IN_LAT, 0);
            double lon = intent.getDoubleExtra(PARAM_IN_LONG, 0);
            double alt = intent.getDoubleExtra(PARAM_IN_ALTITUDE, 0);
            int type = intent.getIntExtra(PARAM_IN_TYPE, 0);
            String serverAdress = intent.getStringExtra(PARAM_IN_SERVERADRESS);

            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(serverAdress);

            httpPost.setHeader("content-type", "application/json");
            JSONObject data = new JSONObject();
            try {
                data.put("userName", username);
                data.put("password", password);
                data.put("lat", lat);
                data.put("long", lon);
                data.put("altitude", alt);
                data.put("runningEventType", type);
                data.put("TotalDistance", mElapsedDistance);
                data.put("TotalTime", mElapsedTime);
                data.put("Pace", pace);
                StringEntity entity = new StringEntity(data.toString(), HTTP.UTF_8);
                httpPost.setEntity(entity);

                httpClient.execute(httpPost);
                /* HttpResponse response = */
                // String test = response.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public boolean checkSupport(Uploader.Feature f) {
        switch (f) {
            case LIVE:
                return true;
            case UPLOAD:
            case FEED:
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case SKIP_MAP:
                break;
        }
        return false;
    }
};
