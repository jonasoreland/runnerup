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
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.common.util.Constants.DB;

public class GooglePlus extends FormCrawler implements Uploader, OAuth2Server {

    public static final String NAME = "Google+";

    /**
     * @todo register OAuth2Server
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;

    public static final String AUTH_URL = "https://accounts.google.com/o/oauth2/auth";
    public static final String TOKEN_URL = "https://accounts.google.com/o/oauth2/token";
    public static final String REDIRECT_URI = "http://localhost";

    public static final String SCOPES =
            "https://www.googleapis.com/auth/plus.me " +
                    "https://www.googleapis.com/auth/plus.login " +
                    "https://www.googleapis.com/auth/plus.stream.write";

    private long id = 0;
    private String access_token = null;
    private String refresh_token = null;
    private long token_now = 0;
    private long expire_time = 0;

    GooglePlus(UploadManager uploadManager) {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            try {
                JSONObject tmp = new JSONObject(uploadManager.loadData(this));
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
    public String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    public String getAuthExtra() {
        return "scope=" + FormCrawler.URLEncode(SCOPES)
                + "&request_visible_actions="
                + FormCrawler.URLEncode("http://schemas.google.com/AddActivity");
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

    public static final long ONE_DAY = 24 * 60 * 60;

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.OAUTH2;
        if (access_token == null)
            return s;

        long diff = (System.currentTimeMillis() - token_now) / 1000;
        if (diff > ONE_DAY) {
            return s;
        }

        return Uploader.Status.OK;
    }

    @Override
    public Uploader.Status upload(SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        return Status.SKIP;
    }

    @Override
    public boolean checkSupport(Uploader.Feature f) {
        return false;
    }

    @Override
    public void logout() {
    }
}
