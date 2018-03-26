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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.GPX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class StravaSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Strava";
    private static final String PUBLIC_URL = "https://www.strava.com";

    /**
     * @todo register OAuth2Server
     */
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;

    private static final String AUTH_URL = "https://www.strava.com/oauth/authorize";
    private static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String REDIRECT_URI = "https://localhost:8080/runnerup/strava";

    private static final String REST_URL = "https://www.strava.com/api/v3/uploads";

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
    public int getIconId() {return R.drawable.connect_strava;}

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
        return access_token != null;
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

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        GPX gpx = new GPX(db, true, false);
        Exception ex;
        try {
            StringWriter writer = new StringWriter();
            gpx.export(mID, writer);
            ActivityDbInfo dbInfo = getStravaType(db, mID);

            HttpURLConnection conn = (HttpURLConnection) new URL(REST_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.setRequestProperty("Authorization", "Bearer " + access_token);

            Part<StringWritable> dataTypePart = new Part<>("data_type",
                    new StringWritable("gpx"));
            Part<StringWritable> filePart = new Part<>("file",
                    new StringWritable(writer.toString()));
            filePart.setFilename(String.format(Locale.getDefault(), "RunnerUp_%04d.gpx", mID));
            filePart.setContentType("application/octet-stream");
            Part<StringWritable> activityTypePart = new Part<>("activity_type",
                    new StringWritable(dbInfo.stravaType));
            Part<?> parts[] = {
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

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            String stravaError = noNullStr(obj.getString("error"));

            if (responseCode == HttpURLConnection.HTTP_CREATED && obj.getLong("id") > 0 &&
                    stravaError == null) {
                s = Status.OK;
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
            ex = new Exception(amsg + stravaError);
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

    /**
     * Strava processing
     */
    @Override
    public Status getExternalId(final SQLiteDatabase db, Status uploadStatus) {
        Status result = Status.ERROR;
        result.activityId = uploadStatus.activityId;
        String stravaError = null;
        int remainingAttempts = 60;
        Exception ex;
        try {
            JSONObject json = null;
            int responseCode = 0;
            String amsg = null;
            while (stravaError == null && remainingAttempts-- > 0) {
                try {
                    //Wait about a second between attempts
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(REST_URL + "/" + uploadStatus.externalId).openConnection();
                conn.setRequestMethod(RequestMethod.GET.name());
                conn.setRequestProperty("Authorization", "Bearer " + access_token);

                responseCode = conn.getResponseCode();
                amsg = conn.getResponseMessage();

                final InputStream in = new BufferedInputStream(conn.getInputStream());
                //Log.v(getName(), "code: " + responseCode + " " + conn.getURL() + ", amsg: " + amsg + in.toString());
                json = SyncHelper.parse(in);
                conn.disconnect();

                String extId = noNullStr(json.getString("activity_id"));
                stravaError = noNullStr(json.getString("error"));
                if (extId != null && stravaError == null) {
                    result = Status.OK;
                    result.externalId = extId;
                    result.externalIdStatus = ExternalIdStatus.OK;
                    return result;
                }
            }
            Log.e(getName(), "Error getting id, code: " + responseCode + ", amsg: " + amsg
                    + " (" + remainingAttempts + ")" + ", json: " + json);
            ex = new Exception(amsg + stravaError);
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

            ex.printStackTrace();
        return result;
    }

    @Override
    public String getActivityUrl(String extId) {
        return PUBLIC_URL + "/activities/" + extId;
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
