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
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.util.Patterns;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RuntasticSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "Runtastic";
    public static final String BASE_URL = "https://www.runtastic.com";
    public static final String START_URL = BASE_URL + "/en/login";
    public static final String LOGIN_URL = BASE_URL + "/en/d/users/sign_in";
    public static final String UPLOAD_URL = BASE_URL + "/import/upload_session";
    public static final String UPDATE_SPORTS_TYPE = BASE_URL + "/import/update_sport_type";

    long id = 0;
    private String username = null;
    private String password = null;

    private Integer userId = null;
    private String authToken = null;

    static final Map<Integer, Sport> runtastic2sportMap = new HashMap<Integer, Sport>();
    static final Map<Sport, Integer> sport2runtasticMap = new HashMap<Sport, Integer>();
    static {
        runtastic2sportMap.put(1, Sport.RUNNING);
        runtastic2sportMap.put(3, Sport.BIKING);
        for (Integer i : runtastic2sportMap.keySet()) {
            sport2runtasticMap.put(runtastic2sportMap.get(i), i);
        }
    }


    RuntasticSynchronizer(SyncManager syncManager) {
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
        id = config.getAsLong("_id");
        String authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authToken != null) {
            try {
                JSONObject tmp = new JSONObject(authToken);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && password != null)
            return true;
        return false;
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        userId = null;
        authToken = null;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
        return tmp.toString();
    }

    private void addRequestHeaders(HttpURLConnection conn) {
        conn.addRequestProperty("Accept", "text/html, application/xhtml+xml, */*");
        conn.addRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.0; Trident/5.0)");
        conn.addRequestProperty("Accept-Encoding", "");
        conn.addRequestProperty("Accept-Language", "en-US");
        conn.addRequestProperty("Connection", "keep-alive");
        conn.addRequestProperty("Origin", "https://www.runtastic.com");
        conn.addRequestProperty("Referer", "https://www.runtastic.com");
        addCookies(conn);
    }

    @Override
    public Status connect() {
        Exception ex = null;
        HttpURLConnection conn = null;
        formValues.clear();

        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        Log.i(getName(), "userId: " + userId + ", authToken: " + authToken);
        if (userId != null && authToken != null) {
            return Status.OK;
        }

        cookies.clear();

        try {
            /**
             * connect to START_URL to get cookies/formValues
             */
            conn = (HttpURLConnection) new URL(START_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addRequestHeaders(conn);
            {
//                int responseCode = conn.getResponseCode();
//                String amsg = conn.getResponseMessage();
                getCookies(conn);
                getFormValues(conn);
                authToken = formValues.get("authenticity_token");
            }
            conn.disconnect();

            if (authToken == null)
                return Status.ERROR;


            /**
             * Then login using a post
             */
            FormValues kv = new FormValues();
            kv.put("user[email]", username);
            kv.put("user[password]", password);
            kv.put("authenticity-token", authToken);
            kv.put("grant_type", "password");

            conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            addRequestHeaders(conn);
            conn.addRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");

            String url2 = null;
            {
                OutputStream wr = new BufferedOutputStream(
                        conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();
                // int responseCode = conn.getResponseCode();
                // String amsg = conn.getResponseMessage();
                getCookies(conn);
                InputStream in = new BufferedInputStream(conn.getInputStream());
                JSONObject ret = SyncHelper.parse(in);
                if (ret != null && ret.has("success") && ret.getBoolean("success")) {
                    Matcher matcher = Patterns.WEB_URL.matcher(ret.getString("update"));
                    while (matcher.find()) {
                        String tmp = matcher.group();
                        final String users = "/users/";
                        if (tmp.contains(users)) {
                            int i = tmp.indexOf(users) + users.length();
                            int i2 = tmp.indexOf('/', i);
                            if (i2 > 0)
                                url2 = tmp.substring(0, i2);
                            else
                                url2 = tmp;

                            if (url2 != null)
                                break;
                        }
                    }
                }
                Log.i(getName(), "found url2: " + url2);
                conn.disconnect();
            }

            if (url2 == null) {
                return s;
            }

            {
                url2 = url2 + "?authenticity_token=" + SyncHelper.URLEncode(authToken);
                conn = (HttpURLConnection) new URL(url2).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod(RequestMethod.GET.name());
                conn.setRequestProperty("Host", "www.runtastic.com");
                conn.addRequestProperty("Accept", "application/json");
                InputStream in = new BufferedInputStream(conn.getInputStream());
                getCookies(conn);
                JSONObject ret = SyncHelper.parse(in);
                userId = ret.getJSONObject("user").getInt("id");
                conn.disconnect();
            }

            if (userId != null && authToken != null) {
                return Status.OK;
            }
        } catch (MalformedURLException e) {
            ex = e;
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

        if (conn != null)
            conn.disconnect();

        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }



    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        StringWriter writer = new StringWriter();
        TCX tcx = new TCX(db);

        HttpURLConnection conn = null;
        try {
            Pair<String, Sport> res = tcx.exportWithSport(mID, writer);
            Sport sport = res.second;
            String filename = String.format("activity%s%d.tcx", Long.toString(Math.round(1000 * Math.random())), mID);

            String url = UPLOAD_URL + "?authenticity_token=" + SyncHelper.URLEncode(authToken) + "&qqfile=" +
                    filename;
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Host", "www.runtastic.com");
            conn.addRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.addRequestProperty("X-File-Name", filename);
            conn.addRequestProperty("Content-Type", "application/octet-stream");
            addRequestHeaders(conn);

            OutputStream out = new BufferedOutputStream(conn.getOutputStream());
            out.write(writer.toString().getBytes());
            out.flush();
            out.close();
            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject ret = SyncHelper.parse(in);
            getCookies(conn);
            if (ret == null || !ret.has("success") || !ret.getBoolean("success")) {
                Log.i(getName(), "ret: " + ret);
                throw new JSONException("Unexpected json return");
            }

            String tmp = ret.getString("content");
            final String name="name='";
            int i1 = tmp.indexOf(name);
            if (i1 < 0) {
                Log.i(getName(), "ret: " + ret);
                throw new JSONException("Unexpected json return");
            }
            i1 += name.length();
            int i2 = tmp.indexOf('\'', i1);
            String import_id = tmp.substring(i1, i2);
            Log.i(getName(), "import_id: " + import_id);
            conn.disconnect();

            if (sport != null && sport != Sport.RUNNING && sport2runtasticMap.containsKey(sport)) {
                conn = (HttpURLConnection) new URL(UPDATE_SPORTS_TYPE).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod(RequestMethod.POST.name());
                conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.addRequestProperty("X-Requested-With", "XMLHttpRequest");
                addRequestHeaders(conn);

                FormValues fv = new FormValues();
                fv.put("authenticity_token", authToken);
                fv.put("data_import_id", import_id);
                fv.put("sport_type_id", sport2runtasticMap.get(sport).toString());
                fv.put("user_id", userId.toString());
                SyncHelper.postData(conn, fv);
                int responseCode = conn.getResponseCode();
                //String amsg = conn.getResponseMessage();
                Log.i(getName(), "code: " + responseCode + ", html: " + getFormValues(conn));
                conn.disconnect();
            }
            logout();
            s = Status.OK;
            s.activityId = mID;
            return s;

        } catch (IOException ex) {
            s = Status.ERROR;
            s.ex = ex;
        } catch (JSONException e) {
            s = Status.ERROR;
            s.ex = e;
        }

        if (s.ex != null)
            Log.e(getName(), "ex: " + s.ex);

        if (conn != null)
            conn.disconnect();

        s.activityId = mID;
        return s;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case LIVE:
            case SKIP_MAP:
                break;
        }

        return false;
    }

    @Override
    public void logout() {
        userId = null;
        authToken = null;
        super.logout();
    }
}
