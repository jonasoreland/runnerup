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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.FROYO)
public class GarminSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "Garmin";

    public static final String CHOOSE_URL = "http://connect.garmin.com/";

    public static final String START_URL = "https://connect.garmin.com/signin";
    public static final String LOGIN_URL = "https://connect.garmin.com/signin";
    public static final String CHECK_URL = "http://connect.garmin.com/user/username";
    public static final String UPLOAD_URL = "https://connect.garmin.com/proxy/upload-service-1.1/json/upload/.tcx";
    public static final String LIST_WORKOUTS_URL = "https://connect.garmin.com/proxy/workout-service-1.0/json/workoutlist";
    public static final String GET_WORKOUT_URL = "https://connect.garmin.com/proxy/workout-service-1.0/json/workout/";
    public static final String CALENDAR_URL = "https://connect.garmin.com/proxy/calendar-service/year/%1$tY/month/%2$d/day/%1te/start/1";
    public static final String SCHEDULE_URL = "https://connect.garmin.com/proxy/workout-service-1.0/json/workoutschedule?workoutScheduleId=";
    public static final String SET_TYPE_URL = "https://connect.garmin.com/proxy/activity-service-1.2/json/type/";

    //TCX format supports only 2 sports by default (Running / Biking);
    // Otherwise "other" is chosen and we have to edit the workout to add the real sport
    //list of sports ID can be found on Garmin website when editing an activity:
    public static final Map<Sport, String> sport2garminMap = new HashMap<Sport, String>();
    static {
        sport2garminMap.put(Sport.WALKING, "walking");
    }

    long id = 0;
    private String username = null;
    private String password = null;
    private boolean isConnected = false;

    GarminSynchronizer(SyncManager syncManager) {
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
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        isConnected = false;
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        if (isConnected) {
            return Status.OK;
        }

        Exception ex = null;
        HttpURLConnection conn = null;
        logout();

        try {
            conn = (HttpURLConnection) new URL(CHOOSE_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            getCookies(conn);

            Log.e(getName(), "GarminSynchronizer.connect() CHOOSE_URL => code: " + responseCode
                    + ", msg: " + amsg);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connectOld();
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                return connectNew();
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

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    private Status connectOld() throws MalformedURLException, IOException, JSONException {
        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;

        HttpURLConnection conn = null;

        /**
         * connect to START_URL to get cookies
         */
        conn = (HttpURLConnection) new URL(START_URL).openConnection();
        {
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            getCookies(conn);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(getName(), "GarminSynchronizer::connect() - got " + responseCode + ", msg: "
                        + amsg);
            }
        }
        conn.disconnect();

        /**
         * Then login using a post
         */
        String login = LOGIN_URL;
        FormValues kv = new FormValues();
        kv.put("login", "login");
        kv.put("login:loginUsernameField", username);
        kv.put("login:password", password);
        kv.put("login:signInButton", "Sign In");
        kv.put("javax.faces.ViewState", "j_id1");

        conn = (HttpURLConnection) new URL(login).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        addCookies(conn);

        {
            OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
            kv.write(wr);
            wr.flush();
            wr.close();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            Log.e(getName(), "code: " + responseCode + ", msg=" + amsg);
            getCookies(conn);
        }
        conn.disconnect();

        /**
         * An finally check that all is OK
         */
        return checkLogin();
    }

    private Status checkLogin() throws MalformedURLException, IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(CHECK_URL).openConnection();
        addCookies(conn);
        {
            conn.connect();
            getCookies(conn);
            String str = SyncHelper.readInputStream(conn.getInputStream());
            Log.e(getName(), "checkLogin: str: " + str);
            JSONObject obj = SyncHelper.parse(str);
            conn.disconnect();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            // Returns username(which is actually Displayname from profile) if
            // logged in
            if (obj.optString("username", "").length() > 0) {
                isConnected = true;
                return Synchronizer.Status.OK;
            } else {
                Log.e(getName(), "GarminSynchronizer::connect() missing username, obj: "
                        + obj.toString() + ", code: " + responseCode + ", msg: " + amsg);
            }
            Status s = Status.NEED_AUTH;
            s.authMethod = Synchronizer.AuthMethod.USER_PASS;
            return s;
        }
    }

    private Status connectNew() throws MalformedURLException, IOException, JSONException {
        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;

        FormValues fv = new FormValues();
        fv.put("service", "https://connect.garmin.com/post-auth/login");
        fv.put("clientId", "GarminConnect");
        fv.put("consumeServiceTicket", "false");

        HttpURLConnection conn = get("https://sso.garmin.com/sso/login", fv);
        addCookies(conn);
        expectResponse(conn, 200, "Connection 1: ");
        getCookies(conn);
        getFormValues(conn);
        conn.disconnect();

        // try again
        FormValues data = new FormValues();
        data.put("username", username);
        data.put("password", password);
        data.put("_eventId", "submit");
        data.put("embed", "true");
        data.put("lt", formValues.get("lt"));

        conn = post("https://sso.garmin.com/sso/login", fv);
        conn.setInstanceFollowRedirects(false);
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        addCookies(conn);
        SyncHelper.postData(conn, data);
        expectResponse(conn, 200, "Connection 2: ");
        getCookies(conn);
        String html = getFormValues(conn);
        conn.disconnect();

        /* this is really horrible */
        int start = html.indexOf("?ticket=");
        if (start == -1) {
            throw new IOException("Invalid login, unable to locate ticket");
        }
        start += "?ticket=".length();
        int end = html.indexOf("'", start);
        String ticket = html.substring(start, end);
        Log.e(getName(), "ticket: " + ticket);

        // connection 3...
        fv.clear();
        fv.put("ticket", ticket);

        conn = get("https://connect.garmin.com/post-auth/login", fv);
        conn.setInstanceFollowRedirects(false);
        addCookies(conn);
        for (int i = 0;; i++) {
            int code = conn.getResponseCode();
            Log.e(getName(), "attempt: " + i + " => code: " + code);
            getCookies(conn);
            if (code == HttpURLConnection.HTTP_OK)
                break;
            if (code != HttpURLConnection.HTTP_MOVED_TEMP)
                break;
            List<String> fields = conn.getHeaderFields().get("location");
            conn.disconnect();
            conn = get(fields.get(0), null);
            conn.setInstanceFollowRedirects(false);
            addCookies(conn);
        }
        conn.disconnect();

        return Status.OK; // return checkLogin();
    }

    private HttpURLConnection open(String base, FormValues fv) throws MalformedURLException,
            IOException {
        HttpURLConnection conn;
        if (fv != null) {
            String url = base + "?" + fv.queryString();
            conn = (HttpURLConnection) new URL(url).openConnection();
        } else {
            conn = (HttpURLConnection) new URL(base).openConnection();
        }
        return conn;
    }

    private HttpURLConnection get(String base, FormValues fv) throws MalformedURLException,
            IOException {
        HttpURLConnection conn = open(base, fv);
        conn.setDoOutput(false);
        conn.setRequestMethod(RequestMethod.GET.name());
        return conn;
    }

    private HttpURLConnection post(String base, FormValues fv) throws MalformedURLException,
            IOException {
        HttpURLConnection conn = open(base, fv);
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        return conn;
    }

    private void expectResponse(HttpURLConnection conn, int code, String string) throws IOException {
        if (conn.getResponseCode() != code) {
            throw new IOException(string + ", code: " + conn.getResponseCode() + ", msg: "
                    + conn.getResponseMessage());
        }
    }

    private void setWorkoutType(Sport s, String garminID) throws Exception {
        if (s == Sport.RUNNING || s == Sport.BIKING || s == Sport.OTHER) {
            //nothing to do
            return;
        }

        String value = sport2garminMap.get(s);
        //only change workout type if sport is supported by Garmin..
        if (value == null) {
            Log.w(getName(), "Workout of type " + Sport.valueOf(s.getDbValue()) + " not supported by Garmin");
            return;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(SET_TYPE_URL + garminID).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        addCookies(conn);


        FormValues fv = new FormValues();
        fv.put("value", value);

        Log.e(getName(), "Setting sport activity to " + value + " for workout " + garminID);
        SyncHelper.postData(conn, fv);

        int responseCode = conn.getResponseCode();
        String amsg = conn.getResponseMessage();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            JSONObject reply = SyncHelper.parse(new BufferedReader(new InputStreamReader(
            // if "activityType" not in res or res["activityType"]["key"] != acttype:
            conn.getInputStream())));
            conn.disconnect();
        } else {
            throw new Exception("Impossible to connect" + responseCode + amsg);
        }
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
            tcx.export(mID, writer);

            conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            addCookies(conn);

            Part<StringWritable> part2 = new Part<StringWritable>("data",
                    new StringWritable(writer.toString()));
            part2.setFilename("RunnerUp.tcx");
            part2.setContentType("application/octet-stream");
            Part<?> parts[] = {
                part2
            };

            SyncHelper.postMulti(conn, parts);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                JSONObject reply = SyncHelper.parse(new BufferedReader(new InputStreamReader(
                        conn.getInputStream())));
                conn.disconnect();
                JSONObject result = reply.getJSONObject("detailedImportResult");
                JSONArray successes = result.getJSONArray("successes");
                if (successes.length() == 1) {
                    s = Status.OK;
                    s.activityId = mID;
                    String garminID = successes.getJSONObject(0).getString("internalId");
                    setWorkoutType(tcx.getSport(), garminID);
                    return s;
                } else {
                    JSONArray failures = result.getJSONArray("failures");
                    ex = new Exception("Unexpected reply: " + (failures.length()>0?failures.toString():result.toString()));
                }
            } else {
                ex = new Exception(amsg);
            }
        } catch (Exception e) {
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
            case WORKOUT_LIST:
            case GET_WORKOUT:
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case SKIP_MAP:
                break;
        }
        return false;
    }

    private String getWorkoutIdFromSchedule(String scheduleid) throws IOException {
        HttpURLConnection conn;

        conn = (HttpURLConnection) new URL(SCHEDULE_URL + scheduleid).openConnection();
        conn.setRequestMethod(RequestMethod.GET.name());
        addCookies(conn);
        conn.connect();
        getCookies(conn);
        InputStream in = new BufferedInputStream(conn.getInputStream());
        try {
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            int responseCode = conn.getResponseCode();
            //String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                obj = obj.getJSONObject("com.garmin.connect.workout.dto.WorkoutScheduleDto");
                return obj.getString("workoutId");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public Status listWorkouts(List<Pair<String, String>> list) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }
        // s = Status.OK
        HttpURLConnection conn = null;

        try {

            Calendar today = Calendar.getInstance();
            int month = today.get(Calendar.MONTH); //jan = 0
            String str_today = String.format("%1$tY-%1$tm-%1td", today);
            String workout_url = String.format(CALENDAR_URL, today, month);

            conn = (HttpURLConnection) new URL(workout_url).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            addCookies(conn);
            conn.connect();
            getCookies(conn);
            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                JSONArray arr = obj.getJSONArray("calendarItems");
                for (int i = 0; ; i++) {
                    obj = arr.optJSONObject(i);
                    if (obj == null)
                        break;
                    else if (!obj.getString("itemType").equals("workout"))
                        continue;
                    String title = obj.getString("title");
                    if (obj.optString("date").equals(str_today)) {
                        title = '*' + title; //mark workout scheduled for today
                    }
                    list.add(new Pair<String, String>(getWorkoutIdFromSchedule(obj.getString("id")),
                            title + ".json"));
                }
            } else {
                s = Synchronizer.Status.ERROR;
                s.ex = new Exception(amsg);
            }
        } catch (IOException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        }

        try {
            conn = (HttpURLConnection) new URL(LIST_WORKOUTS_URL).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            addCookies(conn);
            conn.connect();
            getCookies(conn);
            InputStream in = new BufferedInputStream(conn.getInputStream());
            JSONObject obj = SyncHelper.parse(in);
            conn.disconnect();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                obj = obj.getJSONObject("com.garmin.connect.workout.dto.BaseUserWorkoutListDto");
                JSONArray arr = obj.getJSONArray("baseUserWorkouts");
                for (int i = 0;; i++) {
                    obj = arr.optJSONObject(i);
                    if (obj == null)
                        break;
                    list.add(new Pair<String, String>(obj.getString("workoutId"), obj
                            .getString("workoutName") + ".json"));
                }
            } else {
                s = Synchronizer.Status.ERROR;
                s.ex = new Exception(amsg);
            }
        } catch (IOException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        } catch (JSONException e) {
            s = Synchronizer.Status.ERROR;
            s.ex = e;
        }

        if (s != Status.OK) {
            s.ex.printStackTrace();
        }
        return s;
    }

    @Override
    public void downloadWorkout(File dst, String key) throws Exception {
        HttpURLConnection conn = null;
        Exception ex = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(GET_WORKOUT_URL + key).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            addCookies(conn);
            conn.connect();
            getCookies(conn);
            InputStream in = new BufferedInputStream(conn.getInputStream());
            out = new FileOutputStream(dst);
            int cnt = 0;
            byte buf[] = new byte[1024];
            while (in.read(buf) > 0) {
                cnt += buf.length;
                out.write(buf);
            }
            Log.e(getName(), "downloaded workout key: " + key + " " + cnt + " bytes from "
                    + getName());
            in.close();
            out.close();
            conn.disconnect();
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return;
            }
            ex = new Exception(amsg);
        } catch (Exception e) {
            ex = e;
        }

        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception e) {
            }
        }

        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
            }
        }
        ex.printStackTrace();
        throw ex;
    }
}
