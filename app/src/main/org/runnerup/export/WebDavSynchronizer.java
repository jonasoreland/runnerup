/*
 * Copyright (C) 2020 Timo Lüttig <runnerup@tluettig.de>
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
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

public class WebDavSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "WebDav";

    private long id = 0;

    private PathSimplifier simplifier;
    private FileFormats format;
    private String username;
    private String password;
    private String url;
    private FileFormats mFormat;

    public WebDavSynchronizer() {
        super();
    }

    public WebDavSynchronizer(Context mContext, PathSimplifier simplifier) {
        this();
        this.simplifier = simplifier;
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
        return url;
    }

   /* @Override
    public int getIconId() {return R.drawable.service_webdav;}*/

    @Override
    public int getColorId() {
        return R.color.colorPrimary;
    }

    @Override
    public void init(ContentValues config) {
        id = config.getAsLong("_id");
        String authToken = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (authToken != null) {
            try {
                mFormat = new FileFormats(config.getAsString(Constants.DB.ACCOUNT.FORMAT));
                JSONObject tmp = new JSONObject(authToken);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
                url = tmp.optString("url", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        return username != null && password != null && url != null;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
            tmp.put("url", url);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        url = null;
    }

    @Override
    public Status connect() {

        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS_URL;
        if (username == null || password == null || url == null) {
            return s;
        }

        try {
            OkHttpClient client = getAuthClient();
            // Empty body
            RequestBody body = RequestBody.create(null, "");
            Request request = new Request.Builder().url(url).method("PROPFIND", null).build();

            Response response = client.newCall(request).execute();


            int responseCode = response.code();

            response.close();
            switch(responseCode) {
                case 404:
                    //path does not exist
                    request = new Request.Builder().url(url).method("MKCOL", null).build();
                    response = client.newCall(request).execute();
                    responseCode = response.code();
                    //accept any response code
                    if (responseCode <= 299) {
                        return Status.OK;
                    }
                    break;
                case 207:
                    // Multistatuscode
                    return Status.OK;
                default:
                    return Status.ERROR;
            }
        } catch (Exception e) {
            return Status.ERROR;
        }
        return Status.ERROR;
    }

    /**
     * creates the basic auth for OkHttpClient
     *
     * @return the OkHttpClient containing basic auth
     */
    private OkHttpClient getAuthClient() {
        return new OkHttpClient().newBuilder().authenticator(new Authenticator() {
            @Override
            public Request authenticate(Route route, Response response) throws IOException {
                if (response.request().header("Authorization") != null) {
                    return null; // Give up, we've already failed to authenticate.
                }
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder().header("Authorization", credential).build();
            }
        }).build();
    }

    @Override
    public Status upload(SQLiteDatabase db, final long mID){
        Status s = connect();
        if (s != Status.OK) {
            return s;
        }

        Sport sport = Sport.RUNNING;
        try {

            String[] columns = { Constants.DB.ACTIVITY.SPORT };
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

            if (mFormat.contains(FileFormats.TCX)) {
                TCX tcx = new TCX(db, simplifier);
                StringWriter writer = new StringWriter();
                tcx.export(mID, writer);
                s = uploadFile(db, mID, sport, writer, FileFormats.TCX.getValue());
            }
            if (s == Status.OK && mFormat.contains(FileFormats.GPX)) {
                GPX gpx = new GPX(db, true, true, simplifier);
                StringWriter writer = new StringWriter();
                gpx.export(mID, writer);
                s = uploadFile(db, mID, sport, writer, FileFormats.GPX.getValue());
            }

        } catch (Exception e) {
            Log.e(getName(),"Error uploading, exception: ", e);
            s = Status.ERROR;
            s.ex = e;
        }
        return s;
    }

    private Status uploadFile(SQLiteDatabase db, long mID, Sport sport, StringWriter writer, String fileExt) {

        String file = String.format(Locale.getDefault(), "/RunnerUp_%s_%04d_%s.%s",
                android.os.Build.MODEL.replaceAll("\\s","_"), mID, sport.TapiriikType(),
                fileExt);

        Status s = Status.ERROR;
        try{
            OkHttpClient client = getAuthClient();
            RequestBody body = RequestBody.create(MediaType.parse("application/"+fileExt+"+xml"),writer.toString());
            Request request = new Request.Builder().url(url +file).method("PUT", body).build();

            Response response = client.newCall(request).execute();
            int responseCode = response.code();
            if(responseCode<=299){
                s =  Status.OK;
            }
        }catch(Exception e){
            s = Status.ERROR;
        }
        return s;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
            case FILE_FORMAT:
                return true;
            default:
                return false;
        }
    }
}

