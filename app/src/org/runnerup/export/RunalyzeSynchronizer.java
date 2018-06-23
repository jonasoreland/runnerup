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
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunalyzeSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "Runalyze";
    public static final String PUBLIC_URL = "https://runalyze.com";

    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;

    public static final String AUTH_URL = "https://runalyze.com/oauth/v2/auth";
    public static final String TOKEN_URL = "https://runalyze.com/oauth/v2/token";
    public static final String REDIRECT_URI = "http://localhost:8080/runnerup/runalyze";

    public static final String IMPORT_URL = "https://runalyze.com/api/v1/activities/uploads";

    private long id = 0;
    private String access_token = null;

    RunalyzeSynchronizer(SyncManager syncManager) {
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
    public String getAuthExtra(){
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

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getIconId() {return R.drawable.a17_runalyze;}

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
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        String desc = getDesc(db, mID);
        TCX tcx = new TCX(db);
        Exception ex = null;
        try {
            StringWriter writer = new StringWriter();
            tcx.export(mID, writer);
            HttpURLConnection conn = (HttpURLConnection) new URL(IMPORT_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.setRequestProperty("Authorization", "Bearer " + access_token);

            Part<StringWritable> dataTypePart = new Part<>("data_type",
                new StringWritable("tcx"));
            Part<StringWritable> filePart = new Part<>("file",
                new StringWritable(writer.toString()));
            filePart.setFilename(String.format(Locale.getDefault(),
                                 "RunnerUp_%04d.tcx", mID));
            filePart.setContentType("application/octet-stream");
            Part<?> parts[] = {
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

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            String error = noNullStr(obj.getString("error"));

            if (responseCode == HttpURLConnection.HTTP_CREATED && obj.getLong("id") > 0 &&
                    error == null) {
                s = Status.OK;
                s.externalId = noNullStr(obj.getString("activity_id"));
                if (s.externalId == null) {
                    //The ID is not yet found, request it
                    s.externalIdStatus = ExternalIdStatus.PENDING;
                    s.externalId = noNullStr(obj.getString("id"));
                } else {
                    //Only for very small activities
                    s.externalIdStatus = ExternalIdStatus.OK;
                }
                return s;
            }
            Log.e(getName(),
                  "Error uploading to Runalyze. code: " +
                  responseCode + ", amsg: " + amsg + ", json: " + obj);
            ex = new Exception(amsg + error);
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

