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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;
import org.runnerup.util.FileNameHelper;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

import java.io.StringWriter;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebDavSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "WebDAV";

    private long id = 0;

    private PathSimplifier simplifier;
    private String username;
    private String password;
    private String url;
    private FileFormats mFormat;

    public WebDavSynchronizer() {
        super();
    }

    public WebDavSynchronizer(PathSimplifier simplifier) {
        this();
        this.simplifier = simplifier;
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
        if (url == null || url.isEmpty()) {
            // Some default to help formatting
            // TODO Separate URL for upload and access
            return "https://site.com/remote.php/dav/files/useremail/runnerup";
        }
        return url;
    }

   /* @Override
    public int getIconId() {return R.drawable.service_webdav;}*/

    @ColorRes
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
                //noinspection ConstantConditions
                username = tmp.optString("username", null);
                //noinspection ConstantConditions
                password = tmp.optString("password", null);
                //noinspection ConstantConditions
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

    @NonNull
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

    @NonNull
    @Override
    public Status connect() {

        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS_URL;
        if (username == null || password == null || url == null) {
            return s;
        }

        try {
            OkHttpClient client = getAuthClient();
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
        return new OkHttpClient().newBuilder().addInterceptor(chain -> {
            if(!TextUtils.isEmpty(username) || !TextUtils.isEmpty(password)) {
                String credentials = Credentials.basic(username, password);
                return chain.proceed(chain.request().newBuilder().header("Authorization", credentials).build());
            }
            return chain.proceed(chain.request());
        }).build();
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID){
        Status s = connect();
        if (s != Status.OK) {
            return s;
        }

        Sport sport = Sport.RUNNING;
        long startTime = 0;

        try {

            String[] columns = {
                    Constants.DB.ACTIVITY.SPORT,
                    Constants.DB.ACTIVITY.START_TIME,
            };
            try (Cursor c = db.query(Constants.DB.ACTIVITY.TABLE, columns, "_id = " + mID,
                    null, null, null, null)) {
                if (c.moveToFirst()) {
                    sport = Sport.valueOf(c.getInt(0));
                    startTime = c.getLong(1);
                }
            }

            String fileBase = FileNameHelper.getExportFileNameWithModel(startTime, sport.TapiriikType());
            if (mFormat.contains(FileFormats.TCX)) {
                TCX tcx = new TCX(db, simplifier);
                StringWriter writer = new StringWriter();
                tcx.export(mID, writer);
                s = uploadFile(writer, fileBase, FileFormats.TCX.getValue());
            }
            if (s == Status.OK && mFormat.contains(FileFormats.GPX)) {
                GPX gpx = new GPX(db, true, true, simplifier);
                StringWriter writer = new StringWriter();
                gpx.export(mID, writer);
                s = uploadFile(writer, fileBase, FileFormats.GPX.getValue());
            }

        } catch (Exception e) {
            Log.e(getName(),"Error uploading, exception: ", e);
            s = Status.ERROR;
            s.ex = e;
        }
        return s;
    }

    private Status uploadFile(StringWriter writer, String fileBase, String fileExt) {
        Status s;
        try{
            OkHttpClient client = getAuthClient();
            RequestBody body = RequestBody.create(MediaType.parse("application/" + fileExt + "+xml"), writer.toString());
            Request request = new Request.Builder().url(url + fileBase + fileExt).method("PUT", body).build();

            Response response = client.newCall(request).execute();
            int responseCode = response.code();
            s = responseCode<=299 ? Status.OK : Status.ERROR;
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

