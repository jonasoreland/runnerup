/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 * Copyright (C) 2018 Clayton Craft <clayton@craftyguy.net>
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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;


public class RunalyzeSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Runalyze";
    public static final int ENABLED = BuildConfig.RUNALYZE_ENABLED;
    private static final String BASE_URL = BuildConfig.RUNALYZE_ENABLED > 0 ? "https://runalyze.com" : "https://testing.runalyze.com";
    private static final String PUBLIC_URL = BASE_URL;

    private static final String UPLOAD_URL = BASE_URL + "/api/v1/activities/uploads";
    private static final String AUTH_URL = BASE_URL + "/oauth/v2/auth";
    private static final String TOKEN_URL = BASE_URL + "/oauth/v2/token";
    private static final String REDIRECT_URI = "http://localhost:8080/runnerup/runalyze";

    private long id = 0;
    private String access_token = null;
    private String refresh_token = null;
    private long access_expire = -1;
    private final PathSimplifier simplifier;

    RunalyzeSynchronizer(PathSimplifier simplifier) {
        if (ENABLED == 0) {
            Log.w(NAME, "No client id configured in this build");
        }
        this.simplifier = simplifier;
    }

    @DrawableRes
    @Override
    public int getIconId() {
        return R.drawable.service_runalyze;
    }

    @ColorRes
    @Override
    public int getColorId() {
        return R.color.serviceRunalyze;
    }

    public String getClientId() {
        return BuildConfig.RUNALYZE_ID;
    }

    @Override
    public String getRedirectUri() {
        return REDIRECT_URI;
    }

    @Override
    public String getClientSecret() {
        return BuildConfig.RUNALYZE_SECRET;
    }

    @Override
    public String getAuthUrl() {
        return AUTH_URL;
    }

    public String getAuthExtra() {
        return "scope=activity_push";
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

    @NonNull
    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("refresh_token", refresh_token);
            tmp.put("access_token", access_token);
            tmp.put("access_expire", access_expire);
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
            if (obj.has("refresh_token")) {
                refresh_token = obj.getString("refresh_token");
            }
            access_token = obj.getString("access_token");
            if (obj.has("access_expire")) {
                access_expire = obj.getInt("access_expire");
            }
            else if (obj.has("expires_in")) {
                access_expire = obj.getInt("expires_in") + System.currentTimeMillis() / 1000;
            }
            return Status.OK;

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return Status.ERROR;
    }

    @Override
    public boolean isConfigured() {
        return refresh_token != null;
    }

    @Override
    public void reset() {
        refresh_token = null;
        access_token = null;
    }

    @Override
    @NonNull
    public Status connect() {
        Status s = Status.OK;

        if (getClientId() == null || getClientSecret() == null) {
            //Not configured in this build
            s = Status.INCORRECT_USAGE;
        }
        else if (refresh_token == null) {
            s = Status.NEED_AUTH;
            s.authMethod = AuthMethod.OAUTH2;
        }
        else if (access_token == null || access_expire - 10 < System.currentTimeMillis() / 1000) {
            // Token times out within seconds
            s = Status.NEED_REFRESH;
            s.authMethod = AuthMethod.OAUTH2;
        }

        //Log.v(getName(), "connect: " +s+ " "+refresh_token+" "+access_token);
        return s;
    }

    @NonNull
    public Status refreshToken() {
        Status s;

        try {
            final FormValues fv = new FormValues();
            fv.put(OAuth2Activity.OAuth2ServerCredentials.CLIENT_ID, getClientId());
            fv.put(OAuth2Activity.OAuth2ServerCredentials.CLIENT_SECRET, getClientSecret());
            fv.put("grant_type", "refresh_token");
            fv.put("refresh_token", refresh_token);

            URL url = new URL(getTokenUrl());
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            SyncHelper.postData(conn, fv);

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            JSONObject obj = SyncHelper.parse(conn, getName()+"Refresh");

            if (obj != null && responseCode >= HttpURLConnection.HTTP_OK &&
                    responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                s = parseAuthData(obj);
            } else {
                // token no longer valid (normally HTTP_UNAUTHORIZED)
                s = Status.NEED_AUTH;
                s.authMethod = AuthMethod.OAUTH2;
                access_token = null;

                String error = obj != null && obj.has("error") ?
                        noNullStr(obj.getString("error")) :
                        "";
                Log.d(getName(),
                        "Error uploading, code: " +
                                responseCode + ", amsg: " + amsg + " " + error + ", json: " + (obj == null ? "" : obj));
            }
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

    private String getDesc(SQLiteDatabase db, final long mID) {
        final String[] aColumns = {DB.ACTIVITY.COMMENT};
        Cursor cursor = db.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + mID, null, null, null, null);
        cursor.moveToFirst();
        String desc = cursor.getString(0);
        cursor.close();
        return desc;
    }


    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        if (s != Status.OK) {
            return s;
        }

        String desc = getDesc(db, mID);
        TCX tcx = new TCX(db, simplifier);
        try {
            StringWriter writer = new StringWriter();
            tcx.export(mID, writer);
            HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.setRequestProperty("Authorization", "Bearer " + access_token);

            Part<StringWritable> filePart = new Part<>("file",
                    new StringWritable(writer.toString()));
            filePart.setFilename(String.format(Locale.getDefault(),
                    "RunnerUp_%04d.tcx", mID));
            filePart.setContentType("application/octet-stream");
            Part<?>[] parts = {
                    filePart, null
            };
            if (!TextUtils.isEmpty(desc)) {
                Part<StringWritable> descPart = new Part<>("description",
                        new StringWritable(desc));
                parts[1] = descPart;
            }
            SyncHelper.postMulti(conn, parts);

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.v(getName(), "code: " + responseCode + ", amsg: " + amsg);

            JSONObject obj = SyncHelper.parse(conn, getName());

            if (obj != null && responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                s = Status.OK;
                s.activityId = mID;
                if (obj.has("activity_id")) {
                    // Note: duplicate will not set activity_id
                    s.externalId = noNullStr(obj.getString("activity_id"));
                    if (s.externalId != null) {
                        s.externalIdStatus = ExternalIdStatus.OK;
                    }
                }
                return s;
            }
            String error = obj != null && obj.has("error") ?
                    noNullStr(obj.getString("error")) :
                    "";
            Log.e(getName(),
                    "Error uploading, code: " +
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
    public boolean checkSupport(Synchronizer.Feature f) {
        return f == Feature.UPLOAD;
    }
}

