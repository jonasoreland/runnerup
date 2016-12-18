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
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

@TargetApi(Build.VERSION_CODES.FROYO)
public class StravaSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Strava";

    /**
     * @todo register OAuth2Server
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;

    public static final String AUTH_URL = "https://www.strava.com/oauth/authorize";
    public static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    public static final String REDIRECT_URI = "http://localhost:8080/runnerup/strava";

    public static final String REST_URL = "https://www.strava.com/api/v3/uploads";

    private long id = 0;
    private String access_token = null;

    StravaSynchronizer(SyncManager syncManager) {
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
    public String getAuthExtra() {
        return "scope=write";
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
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.optString("access_token", null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        id = config.getAsLong("_id");
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("access_token", access_token);
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
    public boolean isConfigured() {
        if (access_token == null)
            return false;
        return true;
    }

    @Override
    public void reset() {
        access_token = null;
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (access_token == null)
            return s;

        return Synchronizer.Status.OK;
    }

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        String URL = REST_URL;
        GPX gpx = new GPX(db, true, false);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            StringWriter writer = new StringWriter();
            gpx.export(mID, writer);
            conn = (HttpURLConnection) new URL(URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());

            Part<StringWritable> part0 = new Part<>("access_token",
                    new StringWritable(access_token));
            Part<StringWritable> part1 = new Part<>("data_type",
                    new StringWritable("gpx"));
            Part<StringWritable> part2 = new Part<>("file",
                    new StringWritable(writer.toString()));
            part2.setFilename("RunnerUp.gpx");
            part2.setContentType("application/octet-stream");
            Part<?> parts[] = {
                    part0, part1, part2
            };
            SyncHelper.postMulti(conn, parts);

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.e(getName(), "code: " + responseCode + ", amsg: " + amsg);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject obj = SyncHelper.parse(in);

            if (responseCode == HttpURLConnection.HTTP_CREATED && obj.getLong("id") > 0) {
                conn.disconnect();
                s = Status.OK;
                s.activityId = mID;
                return s;
            }
            ex = new Exception(amsg);
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
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
            case UPLOAD:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void logout() {
    }
}
