/*
 * Copyright (C) 2018 Gerhard Olsson <gerhard.nospam@gmail.com>
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
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.workout.Sport;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;


public class DropboxSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Dropbox";
    private static final String PUBLIC_URL = "https://dropbox.com";
    public static int ENABLED = BuildConfig.DROPBOX_ENABLED;

    private static final String UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload";
    private static final String AUTH_URL = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final String REDIRECT_URI = "http://localhost:8080/runnerup/dropbox";

    private long id = 0;
    private String access_token = null;

    DropboxSynchronizer() {
        if (ENABLED == 0) {
            Log.w(NAME, "No client id configured in this build");
        }
    }

    @Override
    public int getIconId() {
        return R.drawable.service_dropbox;
    }

    @Override
    public int getColorId() {
        return R.color.serviceDropbox;
    }

    public String getClientId() {
        return BuildConfig.DROPBOX_ID;
    }

    @Override
    public String getRedirectUri() {
        return REDIRECT_URI;
    }

    @Override
    public String getClientSecret() {
        return BuildConfig.DROPBOX_SECRET;
    }

    @Override
    public String getAuthUrl() {
        return AUTH_URL;
    }

    public String getAuthExtra() {
        return "";
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
    public String getPublicUrl() {
        return PUBLIC_URL;
    }

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                parseAuthData(tmp);
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
            try {
                String authConfig = data.getStringExtra(DB.ACCOUNT.AUTH_CONFIG);
                JSONObject obj = new JSONObject(authConfig);
                return parseAuthData(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return Status.ERROR;
    }

    private Status parseAuthData(JSONObject obj) {
        try {
            access_token = obj.getString("access_token");
            return Status.OK;

        } catch (JSONException e) {
            e.printStackTrace();
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

    @Override
    public Status connect() {
        Status s = Status.OK;

        if (getClientId() == null || getClientSecret() == null) {
            //Not configured in this build
            s = Status.INCORRECT_USAGE;
        }
        else if (access_token == null) {
            s = Status.NEED_AUTH;
            s.authMethod = AuthMethod.OAUTH2;
        }

        //Log.v(getName(), "connect: " +s+ " "+access_token);
        return s;
    }

    private String getDesc(SQLiteDatabase db, final long mID) {
        final String[] aColumns = {DB.ACTIVITY.COMMENT};
        Cursor cursor = db.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + mID, null, null, null, null);
        cursor.moveToFirst();
        String desc = cursor.getString(0);
        cursor.close();
        return desc;
    }

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        if (s != Status.OK) {
            return s;
        }

        Sport sport = Sport.RUNNING;
        try {
            String[] columns = {
                Constants.DB.ACTIVITY.SPORT
        };
        Cursor c = null;
        try {
            c = db.query(Constants.DB.ACTIVITY.TABLE, columns, "_id = " + mID,
                    null, null, null, null);
            if (c.moveToFirst()) {
                sport = Sport.valueOf(c.getInt(0));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
            // Upload to default directory /Apps/RunnerUp
            String file = String.format(Locale.getDefault(), "/RunnerUp_%s_%04d_%s.tcx",
                    android.os.Build.MODEL.replaceAll("\\s","_"), mID, sport.TapiriikType());

            StringWriter writer = new StringWriter();
            TCX tcx = new TCX(db);
            tcx.export(mID, writer);

            HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Authorization", "Bearer " + access_token);
            JSONObject parameters = new JSONObject();
            try {
                parameters.put("path", file);
                parameters.put("mode", "add");
                parameters.put("autorename", true);
            } catch (JSONException e) {
                e.printStackTrace();
                return Status.ERROR;
            }
            conn.addRequestProperty("Dropbox-API-Arg", parameters.toString());
            OutputStream out = new BufferedOutputStream(conn.getOutputStream());
            out.write(writer.getBuffer().toString().getBytes());
            out.flush();
            out.close();

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.v(getName(), "code: " + responseCode + ", amsg: " + amsg+" ");

            JSONObject obj = SyncHelper.parse(conn, getName());

            if (obj != null && responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                s = Status.OK;
                s.activityId = mID;
                if (obj.has("id")) {
                    // Note: duplicate will not set activity_id
                    s.externalId = noNullStr(obj.getString("id"));
                    if (s.externalId != null) {
                        s.externalIdStatus = ExternalIdStatus.OK;
                    }
                }
                return s;
            }
            String error = obj != null && obj.has("error") ?
                    noNullStr(obj.getString("error")) :
                    "";
            Log.e(getName(),"Error uploading, code: " +
                            responseCode + ", amsg: " + amsg + " " + error + ", json: " + (obj == null ? "" : obj));
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // token no longer valid
                access_token = null;
                s = Status.NEED_AUTH;
                s.authMethod = AuthMethod.OAUTH2;
            }
            s = Status.ERROR;
            return s;

        } catch (IOException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s = Status.ERROR;
            s.ex = e;
        }

        s.ex.printStackTrace();
        return s;
    }

    @Override
    public boolean checkSupport(Feature f) {
        return f == Feature.UPLOAD;
    }
}

