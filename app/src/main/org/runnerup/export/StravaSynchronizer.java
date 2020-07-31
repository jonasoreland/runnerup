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
import org.runnerup.workout.Sport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;


public class StravaSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Strava";
    private static final String PUBLIC_URL = "https://www.strava.com";

    /**
     * @todo register OAuth2Server
     */
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    private static final String AUTH_URL = PUBLIC_URL + "/oauth/authorize";
    private static final String TOKEN_URL = PUBLIC_URL + "/oauth/token";
    private static final String REDIRECT_URI = "https://localhost:8080/runnerup/strava";

    private static final String UPLOAD_URL = PUBLIC_URL + "/api/v3/uploads";

    private long id = 0;
    private String access_token = null;
    private String refresh_token = null;
    private long access_expire = -1;
    private final PathSimplifier simplifier;

    StravaSynchronizer(SyncManager syncManager, PathSimplifier simplifier) {
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
        return "scope=activity:write";
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
    public String getActivityUrl(String extId) {
        return PUBLIC_URL + "/activities/" + extId;
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
    public int getColorId() {return R.color.serviceStrava;}

    @DrawableRes
    @Override
    public int getIconId() {
        return R.drawable.service_strava;
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
            else if (obj.has("expires_at")) {
                access_expire = obj.getInt("expires_at");
            }
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


    @NonNull
    @Override
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
        else if (access_token == null || access_expire - 3600 < System.currentTimeMillis() / 1000) {
            // Token times out within an hour
            s = Status.NEED_REFRESH;
            s.authMethod = AuthMethod.OAUTH2;
        }

        //Log.v(getName(), "connect: " +s+ " "+refresh_token+" "+access_token+ " " + access_expire);
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

    private String stravaActivityType(int sportId) {
        String stravaType;
        Sport sport = Sport.valueOf(sportId);
        if (sport.IsCycling()) {
            stravaType = "ride";
        } else if (sport.IsRunning()) {
            stravaType = "run";
        } else if (sport.IsWalking()) {
            stravaType = "walk";
        } else {
            stravaType = "workout";
        }
        return stravaType;
    }

    private class ActivityDbInfo {
        String desc;
        String stravaType;
    }

    private ActivityDbInfo getStravaType(SQLiteDatabase db, final long mID) {
        final String[] aColumns = {DB.ACTIVITY.COMMENT, DB.ACTIVITY.SPORT};
        Cursor cursor = db.query(DB.ACTIVITY.TABLE, aColumns, "_id = "
                + mID, null, null, null, null);
        cursor.moveToFirst();
        ActivityDbInfo dbInfo = new ActivityDbInfo();
        dbInfo.desc = cursor.getString(0);
        dbInfo.stravaType = stravaActivityType(cursor.getInt(1));
        cursor.close();

        return dbInfo;
    }

    private static byte[] gzip(String string) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return compressed;
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        try {
            TCX tcx = new TCX(db, simplifier);
            StringWriter writer = new StringWriter();
            tcx.export(mID, writer);
            ActivityDbInfo dbInfo = getStravaType(db, mID);

            HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.setRequestProperty("Authorization", "Bearer " + access_token);

            Part<StringWritable> dataTypePart = new Part<>("data_type",
                    new StringWritable("tcx.gz"));
            Part<StringWritable> filePart = new Part<>("file",
                    new StringWritable(gzip(writer.toString())));
            filePart.setFilename(String.format(Locale.getDefault(), "RunnerUp_%04d.tcx.gz", mID));
            filePart.setContentType("application/octet-stream");
            Part<StringWritable> activityTypePart = new Part<>("activity_type",
                    new StringWritable(dbInfo.stravaType));
            Part<?>[] parts = {
                    dataTypePart, filePart, activityTypePart, null
            };
            if (!TextUtils.isEmpty(dbInfo.desc)) {
                Part<StringWritable> descPart = new Part<>("description",
                        new StringWritable(dbInfo.desc));
                parts[3] = descPart;
            }
            SyncHelper.postMulti(conn, parts);

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.v(getName(), "code: " + responseCode + ", amsg: " + amsg);

            JSONObject obj = SyncHelper.parse(conn, getName());
            String stravaError = null;

            if (obj != null && obj.has("error")) {
                stravaError = noNullStr(obj.getString("error"));
            }

            if (responseCode == HttpURLConnection.HTTP_CREATED && obj.getLong("id") > 0 &&
                    stravaError == null) {
                s = Status.OK;
                s.activityId = mID;
                s.externalId = noNullStr(obj.getString("activity_id"));
                if (s.externalId == null) {
                    //The Strava ID is not yet found, request it
                    s.externalIdStatus = ExternalIdStatus.PENDING;
                    s.externalId = noNullStr(obj.getString("id"));
                } else {
                    //Only for very small activities
                    s.externalIdStatus = ExternalIdStatus.OK;
                }
                return s;
            }

            Log.e(getName(), "Error uploading to Strava. code: " + responseCode + ", amsg: " + amsg +
            ", json: " + obj);
            s = Synchronizer.Status.ERROR;
            return s;

        } catch (IOException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        }

        s.ex.printStackTrace();
        return s;
    }

    /**
     * Strava processing
     */
    @NonNull
    @Override
    public Status getExternalId(final SQLiteDatabase db, Status uploadStatus) {
        Status result = Status.ERROR;

        try {
            String stravaError = null;
            int responseCode = 0;
            int remainingAttempts = 60;
            String amsg = null;
            while (stravaError == null && remainingAttempts-- > 0) {
                try {
                    //Wait about a second between attempts
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL + "/" + uploadStatus.externalId).openConnection();
                conn.setRequestMethod(RequestMethod.GET.name());
                conn.setRequestProperty("Authorization", "Bearer " + access_token);

                responseCode = conn.getResponseCode();
                amsg = conn.getResponseMessage();
                Log.v(getName(), "extid code: " + responseCode + ", amsg: " + amsg);

                JSONObject obj = SyncHelper.parse(conn, getName());
                if (obj != null && obj.has("error")) {
                    stravaError = noNullStr(obj.getString("error"));
                }

                if (responseCode <= HttpURLConnection.HTTP_CREATED && obj != null && obj.getLong("id") > 0 &&
                        noNullStr(obj.getString("activity_id")) != null && stravaError == null) {
                    Log.v(getName(), "extid code: " + obj);
                    String extId = noNullStr(obj.getString("activity_id"));
                    if (extId != null) {
                        result = Status.OK;
                        result.activityId = uploadStatus.activityId;
                        result.externalId = extId;
                        result.externalIdStatus = ExternalIdStatus.OK;
                    }
                    return result;
                }
            }
            Log.e(getName(), "Error getting id, code: " + responseCode + ", amsg: " + amsg
                    + " (" + remainingAttempts + ")");
            return result;

        } catch (IOException e) {
            result.ex = e;
        } catch (JSONException e) {
            result.ex = e;
        }

        result.ex.printStackTrace();
        return result;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        return f == Feature.UPLOAD;
    }

    @Override
    public void logout() {
    }
}
