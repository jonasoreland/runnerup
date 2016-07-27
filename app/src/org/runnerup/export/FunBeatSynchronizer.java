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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.export.format.TCX;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.Encryption;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: 1) serious cleanup needed 2) maybe reverse engineer
 * 1.0.0.api.funbeat.se that I found...
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class FunBeatSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "FunBeat";
    public static final String BASE_URL = "http://www.funbeat.se";
    public static final String START_URL = BASE_URL + "/index.aspx";
    public static final String LOGIN_URL = BASE_URL + "/index.aspx";
    public static final String UPLOAD_URL = BASE_URL
            + "/importexport/upload.aspx";

    public static final String API_URL = "http://1.0.0.android.api.funbeat.se/json/Default.asmx/";
    public static final String FEED_URL = API_URL + "GetMyNewsFeed";

    private static String APP_ID = null;
    private static String APP_SECRET = null;

    long id = 0;
    private String username = null;
    private String password = null;
    private String loginID = null;
    private String loginSecretHashed = null;

    static final Map<Integer, Sport> funbeat2sportMap = new HashMap<Integer, Sport>();
    static final Map<Sport, Integer> sport2funbeatMap = new HashMap<Sport, Integer>();
    static {
        // the best (known) way to get ID for a given sport is:
        // 1) create a workout on the website funbeat.se with the desired sport type
        // 2) launch RunnerUp and go to Feed tab
        // 3) a log should appear "Unknown workout ... with" (fired from setTrainingType method)
        funbeat2sportMap.put(25, Sport.RUNNING);
        funbeat2sportMap.put(7, Sport.BIKING);
        funbeat2sportMap.put(51, Sport.OTHER);
        funbeat2sportMap.put(26, Sport.ORIENTEERING);
        funbeat2sportMap.put(417, Sport.WALKING);
        for (Integer i : funbeat2sportMap.keySet()) {
            sport2funbeatMap.put(funbeat2sportMap.get(i), i);
        }
    }

    FunBeatSynchronizer(SyncManager syncManager) {
        if (APP_ID == null || APP_SECRET == null) {
            try {
                final JSONObject tmp = new JSONObject(syncManager.loadData(this));
                APP_ID = tmp.getString("APP_ID");
                APP_SECRET = tmp.getString("APP_SECRET");
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }
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
                loginID = tmp.optString("loginID", null);
                loginSecretHashed = tmp.optString("loginSecretHashed", null);
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
        loginID = null;
        loginSecretHashed = null;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
            tmp.put("loginID", loginID);
            tmp.put("loginSecretHashed", loginSecretHashed);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
        return tmp.toString();
    }

    @Override
    public Status connect() {
        Exception ex = null;
        HttpURLConnection conn = null;
        cookies.clear();
        formValues.clear();

        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        if (loginID == null || loginSecretHashed == null) {
            if (!validateAndCreateSecrets(username, password))
                return s;
        }

        try {
            /**
             * connect to START_URL to get cookies/formValues
             */
            conn = (HttpURLConnection) new URL(START_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            {
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                getCookies(conn);
                getFormValues(conn);
                Log.i(getName(), "FunBeat.START_URL => code: " + responseCode + "(" + amsg
                        + "), cookies: " + cookies.size() + ", values: " + formValues.size());
            }
            conn.disconnect();

            /**
             * Then login using a post
             */
            FormValues kv = new FormValues();
            String viewKey = SyncHelper.findName(formValues.keySet(), "VIEWSTATE");
            String eventKey = SyncHelper.findName(formValues.keySet(), "EVENTVALIDATION");
            String userKey = SyncHelper.findName(formValues.keySet(), "Username");
            String passKey = SyncHelper.findName(formValues.keySet(), "Password");
            String loginKey = SyncHelper.findName(formValues.keySet(), "LoginButton");
            kv.put(viewKey, formValues.get(viewKey));
            kv.put(eventKey, formValues.get(eventKey));
            kv.put(userKey, username);
            kv.put(passKey, password);
            kv.put(loginKey, "Logga in");

            conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            addCookies(conn);

            boolean ok = false;
            {
                OutputStream wr = new BufferedOutputStream(
                        conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                getCookies(conn);
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    String redirect = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(BASE_URL + redirect)
                            .openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod(RequestMethod.GET.name());
                    addCookies(conn);
                    responseCode = conn.getResponseCode();
                    amsg = conn.getResponseMessage();
                    getCookies(conn);
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(getName(), "FunBeatSynchronizer::connect() - got " + responseCode
                            + ", msg: " + amsg);
                }
                String html = getFormValues(conn);
                ok = html.indexOf("Logga ut") > 0;

                conn.disconnect();
            }

            if (ok) {
                return Synchronizer.Status.OK;
            } else {
                return s;
            }
        } catch (MalformedURLException e) {
            ex = e;
        } catch (IOException e) {
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

    private boolean validateAndCreateSecrets(String username, String password) {
        try {
            JSONObject req = new JSONObject();
            req.put("username", username);
            req.put("passwordHashed", Encryption.toHex(Encryption.SHA1(password)));
            JSONObject reply = makeRequest("ValidateAndCreateSecrets", req);
            if (reply == null || !reply.has("d")) {
                return false;
            }
            reply = reply.getJSONObject("d");
            loginID = reply.getString("LoginID");
            String loginSecret = reply.getString("LoginSecret");
            loginSecretHashed = Encryption.calculateRFC2104HMAC(loginSecret, APP_SECRET);
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    private JSONObject makeRequest(String function, JSONObject arg) {
        HttpURLConnection conn = null;
        try {
            arg.put("applicationID", APP_ID);
            if (loginID != null && loginSecretHashed != null) {
                arg.put("loginID", loginID);
                arg.put("loginSecret", loginSecretHashed);
            }
            conn = (HttpURLConnection) new URL(API_URL + function).openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            OutputStream out = new BufferedOutputStream(conn.getOutputStream());
            out.write(arg.toString().getBytes("UTF-8"));
            out.flush();
            out.close();

            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject ret = SyncHelper.parse(in);
            conn.disconnect();
            return ret;
        } catch (JSONException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (conn != null)
            conn.disconnect();
        return null;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        TCX tcx = new TCX(db);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            StringWriter writer = new StringWriter();
            String id = tcx.export(mID, writer);
            conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addCookies(conn);
            getFormValues(conn); // execute the GET
            conn.disconnect();

            String viewKey = SyncHelper.findName(formValues.keySet(), "VIEWSTATE");
            String eventKey = SyncHelper.findName(formValues.keySet(), "EVENTVALIDATION");
            String fileKey = SyncHelper.findName(formValues.keySet(), "FileUpload");
            String uploadKey = SyncHelper.findName(formValues.keySet(), "UploadButton");

            Part<StringWritable> part1 = new Part<StringWritable>(viewKey,
                    new StringWritable(formValues.get(viewKey)));

            Part<StringWritable> part2 = new Part<StringWritable>(eventKey,
                    new StringWritable(formValues.get(eventKey)));

            Part<StringWritable> part3 = new Part<StringWritable>(fileKey,
                    new StringWritable(writer.toString()));
            part3.setContentType("application/octet-stream");
            part3.setFilename("jonas.tcx");

            Part<StringWritable> part4 = new Part<StringWritable>(uploadKey,
                    new StringWritable(formValues.get(uploadKey)));
            Part<?> parts[] = {
                    part1, part2, part3, part4
            };

            conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            addCookies(conn);
            SyncHelper.postMulti(conn, parts);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            getCookies(conn);
            String redirect = null;
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                redirect = conn.getHeaderField("Location");
                conn.disconnect();
                conn = (HttpURLConnection) new URL(BASE_URL + redirect)
                        .openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod(RequestMethod.GET.name());
                addCookies(conn);
                responseCode = conn.getResponseCode();
                amsg = conn.getResponseMessage();
                getCookies(conn);
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(getName(), "FunBeatSynchronizer::upload() - got " + responseCode + ", msg: "
                        + amsg);
            }
            getFormValues(conn);
            conn.disconnect();

            viewKey = SyncHelper.findName(formValues.keySet(), "VIEWSTATE");
            eventKey = SyncHelper.findName(formValues.keySet(), "EVENTVALIDATION");
            String nextKey = SyncHelper.findName(formValues.keySet(), "NextButton");
            String hidden = SyncHelper.findName(formValues.keySet(), "ChoicesHiddenField");

            FormValues kv = new FormValues();
            kv.put(viewKey, formValues.get(viewKey));
            kv.put(eventKey, formValues.get(eventKey));
            kv.put(nextKey, "Nasta >>");
            kv.put(hidden, "[ \"import///" + id + "///tcx\" ]");

            String surl = BASE_URL + redirect;
            conn = (HttpURLConnection) new URL(surl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            addCookies(conn);
            {
                OutputStream wr = new BufferedOutputStream(
                        conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();
                responseCode = conn.getResponseCode();
                amsg = conn.getResponseMessage();
                getCookies(conn);
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    redirect = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(BASE_URL + redirect)
                            .openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod(RequestMethod.GET.name());
                    addCookies(conn);
                    responseCode = conn.getResponseCode();
                    amsg = conn.getResponseMessage();
                    getCookies(conn);
                }
                String html = getFormValues(conn);
                boolean ok = html.indexOf("r klar") > 0;
                Log.e(getName(), "ok: " + ok);

                conn.disconnect();
                if (ok) {
                    s = Status.OK;
                    s.activityId = mID;
                } else {
                    s = Status.CANCEL;
                }
                return s;
            }
        } catch (IOException e) {
            ex = e;
        }

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case FEED:
            case UPLOAD:
                return true;
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case LIVE:
            case SKIP_MAP:
                break;
        }

        return false;
    }

    @Override
    public Status getFeed(FeedUpdater feedUpdater) {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS;
        if (loginID == null || loginSecretHashed == null) {
            if ((s = connect()) != Status.OK) {
                return s;
            }
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(FEED_URL).openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/json; charset=utf-8");
            final JSONObject req = getRequestObject();
            final OutputStream out = new BufferedOutputStream(conn.getOutputStream());
            out.write(req.toString().getBytes());
            out.flush();
            out.close();
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = SyncHelper.parse(in);
            final int code = conn.getResponseCode();
            conn.disconnect();
            if (code == HttpURLConnection.HTTP_OK) {
                parseFeed(feedUpdater, reply);
                return Status.OK;
            }
        } catch (final MalformedURLException e) {
            e.printStackTrace();
            s.ex = e;
        } catch (final IOException e) {
            e.printStackTrace();
            s.ex = e;
        } catch (final JSONException e) {
            e.printStackTrace();
            s.ex = e;
        }

        s = Status.ERROR;
        if (conn != null)
            conn.disconnect();

        return s;
    }

    private void parseFeed(final FeedUpdater feedUpdater, final JSONObject reply)
            throws JSONException {
        final JSONArray arr = reply.getJSONArray("d");
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject o = arr.getJSONObject(i);
            try {
                final String t = o.getString("What");
                if ("training".contentEquals(t)) {
                    ContentValues c = parseWorkout(o);
                    feedUpdater.add(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ContentValues parseWorkout(JSONObject o) throws JSONException {
        final ContentValues c = new ContentValues();
        c.put(FEED.ACCOUNT_ID, getId());
        c.put(FEED.EXTERNAL_ID, o.getLong("ID"));
        c.put(FEED.FLAGS, "brokenStartTime"); // BUH!!
        c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
        setTrainingType(c, o.getInt("TrainingTypeID"), o.getString("TrainingTypeName"));

        c.put(FEED.START_TIME, parseDateTime(o.getString("DateTime")));
        if (!o.isNull("Distance"))
            c.put(FEED.DISTANCE, 1000 * o.getDouble("Distance"));
        if (!o.isNull("Duration"))
            c.put(FEED.DURATION, getDuration(o.getJSONObject("Duration")));
        if (!o.isNull("PersonID"))
            c.put(FEED.USER_ID, o.getInt("PersonID"));
        if (!o.isNull("Firstname"))
            c.put(FEED.USER_FIRST_NAME, o.getString("Firstname"));
        if (!o.isNull("Lastname"))
            c.put(FEED.USER_LAST_NAME, o.getString("Lastname"));
        if (!o.isNull("PictureURL"))
            c.put(FEED.USER_IMAGE_URL,
                    o.getString("PictureURL").replace("~/", "http://www.funbeat.se/"));
        if (!o.isNull("Description"))
            c.put(FEED.NOTES, o.getString("Description"));
        c.put(FEED.URL,
                "http://www.funbeat.se/training/show.aspx?TrainingID="
                        + Long.toString(o.getLong("ID")));
        // TODO FEED.COMMENTS
        return c;
    }

    private void setTrainingType(ContentValues c, int TypeID, String typeString) {
        Sport s = funbeat2sportMap.get(TypeID);
        if (s != null) {
            c.put(FEED.FEED_SUBTYPE, s.getDbValue());
        } else {
            Log.e(getName(), "Unknown workout " + typeString + " with ID " + TypeID);
            c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_OTHER);
            c.put(FEED.FEED_TYPE_STRING, typeString);
        }
    }

    private int getDuration(final JSONObject obj) {
        final int hours = obj.optInt("Hours", 0);
        final int minutes = obj.optInt("Minutes", 0);
        final int seconds = obj.optInt("Seconds", 0);
        return seconds + 60 * (minutes + 60 * hours);
    }

    private long parseDateTime(final String s) {
        final String s2 = s.substring(s.indexOf('(') + 1);
        final String s3 = s2.substring(0, s2.indexOf(')'));
        return Long.valueOf(s3);
    }

    private JSONObject getRequestObject() throws JSONException {
        final JSONObject req = new JSONObject();
        req.put("applicationID", APP_ID);
        req.put("loginID", loginID);
        req.put("loginSecret", loginSecretHashed);
        return req;
    }
}
