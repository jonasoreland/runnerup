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

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;


public class RunningAHEADSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "RunningAHEAD";
    private static final String PUBLIC_URL = "https://www.runningahead.com";

    /**
     * @todo register OAuth2Server
     */
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    private static final String AUTH_URL = "https://www.runningahead.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.runningahead.com/oauth2/token";
    private static final String REDIRECT_URI = "https://localhost:8080/runnerup/runningahead";

    private static final String REST_URL = "https://api.runningahead.com/rest";
    private static final String IMPORT_URL = REST_URL + "/logs/me/workouts/tcx";

    private long id = 0;
    private String access_token = null;
    private final PathSimplifier simplifier;

    RunningAHEADSynchronizer(SyncManager syncManager, PathSimplifier simplifier) {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
    public int getColorId() {return R.color.serviceRunningAhead;}

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                //noinspection ConstantConditions
                access_token = tmp.optString("access_token", null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        id = config.getAsLong("_id");
    }

    @NonNull
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
        return access_token != null;
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
        if (access_token == null)
            return s;

        return Synchronizer.Status.OK;
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        String URL = IMPORT_URL + "?access_token=" + access_token;
        TCX tcx = new TCX(db, simplifier);
        HttpURLConnection conn;
        Exception ex;
        try {
            StringWriter writer = new StringWriter();
            tcx.export(mID, writer);
            conn = (HttpURLConnection) new URL(URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Encoding", "gzip");
            OutputStream out = new GZIPOutputStream(
                    new BufferedOutputStream(conn.getOutputStream()));
            out.write(writer.toString().getBytes());
            out.flush();
            out.close();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.e(getName(), "code: " + responseCode + ", amsg: " + amsg);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject obj = SyncHelper.parse(in);
            JSONObject data = obj.getJSONObject("data");

            boolean found = false;
            //if (!found) {
                try {
                    found = data.getJSONArray("workoutIds").length() == 1;
                } catch (JSONException e) {
                }
            //}
            if (!found) {
                try {
                    found = data.getJSONArray("ids").length() == 1;
                } catch (JSONException e) {
                }
            }
            if (!found) {
                Log.e(getName(), "Unhandled response from RunningAHEADSynchronizer: " + obj);
            }
            if (responseCode == HttpURLConnection.HTTP_OK && found) {
                conn.disconnect();
                return Status.OK;
            }
            ex = new Exception(amsg);
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        ex.printStackTrace();
        return s;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        return f == Feature.UPLOAD;
    }

    @Override
    public void logout() {
    }
}
