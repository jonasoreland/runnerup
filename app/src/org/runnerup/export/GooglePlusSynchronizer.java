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

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

@TargetApi(Build.VERSION_CODES.FROYO)
public class GooglePlusSynchronizer extends DefaultSynchronizer implements Synchronizer, OAuth2Server {

    public static final String NAME = "Google+";

    /**
     * @todo register OAuth2Server
     */
    protected String mClientId = null;
    protected String mClientSecret = null;
    protected String projectId = null;

    protected String sAuthUrl = "https://accounts.google.com/o/oauth2/auth";
    protected String sTokenUrl = "https://accounts.google.com/o/oauth2/token";

    protected String sRedirectUrl = "http://localhost";

    private static final String SCOPES = "https://www.googleapis.com/auth/plus.me " +
            "https://www.googleapis.com/auth/plus.login " +
            "https://www.googleapis.com/auth/plus.stream.write";
    private long id = 0;

    public String getAccessToken() {
        return access_token;
    }

    public String getRefreshToken() {
        return refresh_token;
    }

    public long getTokenNow() {
        return token_now;
    }

    public long getExpireTime() {
        return expire_time;
    }

    private String access_token = null;
    private String refresh_token = null;
    private long token_now = 0;
    private long expire_time = 0;

    GooglePlusSynchronizer(SyncManager syncManager) {
        if (getClientId() == null || getClientSecret() == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                this.setClientId(tmp.getString("CLIENT_ID"));
                this.setClientSecret(tmp.getString("CLIENT_SECRET"));
                this.setProjectId(tmp.getString("PROJECT_ID"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public String getClientId() {
        return mClientId;
    }

    private void setClientId(String clientId) {
        this.mClientId = clientId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    @Override
    public String getRedirectUri() {
        return sRedirectUrl;
    }

    private void setClientSecret(String clientSecret) {
        this.mClientSecret = clientSecret;
    }

    @Override
    public String getClientSecret() {
        return mClientSecret;
    }

    @Override
    public String getAuthUrl() {
        return sAuthUrl;
    }

    @Override
    public String getTokenUrl() {
        return sTokenUrl;
    }

    public String getScopes() { return SCOPES; }

    @Override
    public String getAuthExtra() {
        return "scope=" + SyncHelper.URLEncode(getScopes())
                + "&request_visible_actions="
                + SyncHelper.URLEncode("http://schemas.google.com/AddActivity");
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
                refresh_token = tmp.optString("refresh_token", null);
                token_now = tmp.optLong("token_now");
                expire_time = tmp.optLong("expire_time");
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
            if (refresh_token != null)
                tmp.put("refresh_token", refresh_token);
            tmp.put("token_now", token_now);
            tmp.put("expire_time", expire_time);
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
                JSONObject tmp = new JSONObject(authConfig);
                access_token = tmp.getString("access_token");
                refresh_token = tmp.optString("refresh_token", null);
                expire_time = tmp.getLong("expires_in");
                token_now = System.currentTimeMillis();
                return Status.OK;
            } catch (Exception ex) {
                ex.printStackTrace();
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
        expire_time = 0;
        token_now = 0;
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (getAccessToken() == null)
            return s;

        long diff = (System.currentTimeMillis() - getTokenNow());
        if (diff > getExpireTime() * 1000) {
            return refreshToken();
        }

        return Status.OK;
    }

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        return Status.SKIP;
    }

    @Override
    public void logout() {
    }

    public Status refreshToken() {
        Status s = Status.OK;
        HttpURLConnection conn = null;

        final FormValues fv = new FormValues();
        fv.put("client_id", getClientId());
        fv.put("client_secret", getClientSecret());
        fv.put("grant_type", "refresh_token");
        fv.put("refresh_token", getRefreshToken());

        try {
            URL url = new URL(getTokenUrl());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            SyncHelper.postData(conn, fv);

            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();

            access_token = obj.getString("access_token");
            expire_time = obj.getLong("expires_in");
            token_now = System.currentTimeMillis();

            return s;
        } catch (MalformedURLException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (ProtocolException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (IOException e) {
            s = Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s.ex = e;
        }

        if (s.ex != null)
            s.ex.printStackTrace();

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
}
