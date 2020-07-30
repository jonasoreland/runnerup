/*
 * Copyright (C) 2014 paradix@10g.pl
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
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.export.format.GoogleFitData;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.SyncHelper;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class GoogleFitSynchronizer extends DefaultSynchronizer implements OAuth2Server {

    public static final String NAME = "GoogleFit";
    private static final String PUBLIC_URL = "https://fit.google.com";
    private static final String REST_URL = "https://www.googleapis.com/fitness/v1/users/me/";
    public static final String REST_DATASOURCE = "dataSources";
    public static final String REST_DATASETS = "datasets";
    public static final String REST_SESSIONS = "sessions";
    private static final String SCOPES =
            " https://www.googleapis.com/auth/fitness.activity.write " +
                    "https://www.googleapis.com/auth/fitness.activity.read " +
                    "https://www.googleapis.com/auth/fitness.body.write " +
                    "https://www.googleapis.com/auth/fitness.body.read " +
                    "https://www.googleapis.com/auth/fitness.location.write " +
                    "https://www.googleapis.com/auth/fitness.location.read";
    private static final int MAX_ATTEMPTS = 3;
    private static final String DUMMY_REDIRECT_URI = "https://localhost";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_URL = "https://accounts.google.com/o/oauth2/token";
    
    private String mClientId = null;
    private String mClientSecret = null;
    private String projectId = null;
    private String access_token;

    private final Context context;

    GoogleFitSynchronizer(Context ctx, SyncManager syncManager) {
        if (getClientId() == null || getClientSecret() == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                this.mClientId = tmp.getString("CLIENT_ID");
                this.mClientSecret = tmp.getString("CLIENT_SECRET");
                this.projectId = tmp.getString("PROJECT_ID");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        this.context = ctx;
    }

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.optString("access_token", null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    String getScopes() {
        return SCOPES;
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getClientId() {
        return mClientId;
    }

    @Override
    public String getRedirectUri() {
        return DUMMY_REDIRECT_URI;
    }

    @Override
    public String getClientSecret() {
        return mClientSecret;
    }

    @Override
    public String getAuthUrl() {
        return AUTH_URL;
    }

    @Override
    public String getPublicUrl() {
        return PUBLIC_URL;
    }

    @ColorRes
    @Override
    public int getColorId() { return R.color.serviceGoogleFit; }

    private Context getContext() {
        return context;
    }

    private String getAccessToken() {
        return access_token;
    }

    private String getProjectId() {
        return projectId;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case WORKOUT_LIST:
            case GET_WORKOUT:
            case SKIP_MAP:
                return false;
        }
        return false;
    }

    @Override
    public String getAuthExtra() {
        return "scope=" + SyncHelper.URLEncode(getScopes());
    }

    @Override
    public String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    public String getRevokeUrl() {
        return null;
    }

    @NonNull
    @Override
    public String getAuthConfig() {
        Log.e(getName(), "getAuthConfig: stub, must be implemented");
        return "";
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        //export DataSource if not yet existing
        GoogleFitData gfd = new GoogleFitData(db, getProjectId(), getContext());
        List<String> presentDataSources;
        try {
            presentDataSources = listExistingDataSources();
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        List<GoogleFitData.DataSourceType> activitySources = gfd.getActivityDataSourceTypes(mID);

        s = exportActivityDataSourceTypes(gfd, presentDataSources, activitySources);
        if (s.equals(Status.ERROR)) {
            return s;
        }

        //export all DataPoint types for activity
        for (GoogleFitData.DataSourceType source : activitySources) {
            s = exportActivityData(gfd, source, mID);
            if(s.equals(Status.ERROR)) {
                return s;
            }
        }

        //export Session
        s = exportActivitySession(gfd, mID);
        if (!s.equals(Status.ERROR)) {
            s.activityId = mID;
        }

        return s;
    }

    private Status exportActivityDataSourceTypes(GoogleFitData gfd, List<String> presentDataSources, List<GoogleFitData.DataSourceType> activitySources) {
        Status status = Status.OK;
        try {
            for (GoogleFitData.DataSourceType type : activitySources) {
                if (!presentDataSources.contains(type.getDataStreamId(gfd))) {
                    StringWriter w = new StringWriter();
                    gfd.exportDataSource(type, w);
                    status = sendData(w, REST_DATASOURCE, RequestMethod.POST);
                    if (status == Status.ERROR) {
                        return status;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            status = Status.ERROR;
        }
        return status;
    }

    private Status exportActivityData(GoogleFitData gfd, GoogleFitData.DataSourceType source, long activityId) {
        Status status = Status.ERROR;
        try {
            StringWriter w = new StringWriter();
            String suffix = gfd.exportTypeData(source, activityId, w);
            status = sendData(w, suffix, RequestMethod.PATCH);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Status exportActivitySession(GoogleFitData gfd, long mID) {
        Status status = Status.ERROR;
        try {
            StringWriter w = new StringWriter();
            String suffix = gfd.exportSession(mID, w);
            status = sendData(w, suffix, RequestMethod.PUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Status sendData(StringWriter w, String suffix, RequestMethod method) throws IOException {
        Status status = Status.ERROR;
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            HttpURLConnection connect = getHttpURLConnection(suffix, method);
            GZIPOutputStream gos = new GZIPOutputStream(connect.getOutputStream());
            gos.write(w.toString().getBytes());
            gos.flush();
            gos.close();

            int code = connect.getResponseCode();
            try {
                if (code != HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    if (code != HttpURLConnection.HTTP_OK) {
                        Log.i(getName(), SyncHelper.parse(new GZIPInputStream(connect.getErrorStream())).toString());
                        status = Status.ERROR;
                    } else {
                        Log.i(getName(), SyncHelper.parse(new GZIPInputStream(connect.getInputStream())).toString());
                        status = Status.OK;
                    }
                    break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                connect.disconnect();
            }
        }
        return status;
    }

    private HttpURLConnection getHttpURLConnection(String suffix, RequestMethod requestMethod) throws IOException {
        URL url = new URL(REST_URL + suffix);
        HttpURLConnection connect = (HttpURLConnection) url.openConnection();
        connect.addRequestProperty("Authorization", "Bearer " + getAccessToken());
        connect.addRequestProperty("Accept-Encoding", "gzip");
        connect.addRequestProperty("User-Agent", "RunnerUp (gzip)");
        if (requestMethod.equals(RequestMethod.GET)) {
            connect.setDoInput(true);
        } else {
            connect.setDoOutput(true);
            connect.addRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connect.addRequestProperty("Content-Encoding", "gzip");
        }
        if (requestMethod.equals(RequestMethod.PATCH)) {
            connect.addRequestProperty("X-HTTP-Method-Override", "PATCH");
            connect.setRequestMethod(RequestMethod.POST.name());
        } else {
            connect.setRequestMethod(requestMethod.name());
        }
        return connect;
    }

    private List<String> listExistingDataSources() throws Exception {
        HttpURLConnection conn;
        List<String> dataStreamIds = new ArrayList<>();
        try {
            conn = getHttpURLConnection(REST_DATASOURCE, RequestMethod.GET);
            final JSONObject reply = SyncHelper.parse(new GZIPInputStream(conn.getInputStream()));
            int code = conn.getResponseCode();
            conn.disconnect();

            if (code != HttpURLConnection.HTTP_OK) {
                throw new Exception("got a " + code + " response code from upload");
            } else {
                JSONArray data = reply.getJSONArray("dataSource");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject dataSource = data.getJSONObject(i);
                    dataStreamIds.add(dataSource.getString("dataStreamId"));
                }
                return dataStreamIds;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dataStreamIds;
    }
}
