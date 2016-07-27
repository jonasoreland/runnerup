/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import org.runnerup.export.format.EndomondoTrack;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * @author jonas Based on https://github.com/cpfair/tapiriik
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class EndomondoSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "Endomondo";
    public static final String AUTH_URL = "https://api.mobile.endomondo.com/mobile/auth";
    public static final String UPLOAD_URL = "http://api.mobile.endomondo.com/mobile/track";
    public static final String FEED_URL = "http://api.mobile.endomondo.com/mobile/api/feed";

    long id = 0;
    private String username = null;
    private String password = null;
    private String deviceId = null;
    private String authToken = null;

    public static final Map<Integer, Sport> endomondo2sportMap = new HashMap<Integer, Sport>();
    public static final Map<Sport, Integer> sport2endomondoMap = new HashMap<Sport, Integer>();
    static {
        //list of sports ID can be found at
        // https://github.com/isoteemu/sports-tracker-liberator/blob/master/endomondo/workout.py
        endomondo2sportMap.put(0, Sport.RUNNING);
        endomondo2sportMap.put(2, Sport.BIKING);
        endomondo2sportMap.put(22, Sport.OTHER);
        endomondo2sportMap.put(17, Sport.ORIENTEERING);
        endomondo2sportMap.put(18, Sport.WALKING);
        for (Integer i : endomondo2sportMap.keySet()) {
            sport2endomondoMap.put(endomondo2sportMap.get(i), i);
        }
    }

    EndomondoSynchronizer(SyncManager syncManager) {
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
        String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (auth != null) {
            try {
                JSONObject tmp = new JSONObject(auth);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
                deviceId = tmp.optString("deviceId", null);
                authToken = tmp.optString("authToken", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && password != null && deviceId != null && authToken != null) {
            return true;
        }
        return false;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
            tmp.put("deviceId", deviceId);
            tmp.put("authToken", authToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        deviceId = null;
        authToken = null;
    }

    @Override
    public Status connect() {
        if (isConfigured()) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        /**
         * Generate deviceId
         */
        deviceId = UUID.randomUUID().toString();

        Exception ex = null;
        HttpURLConnection conn = null;
        logout();
        try {

            /**
			 *
			 */
            String login = AUTH_URL;
            FormValues kv = new FormValues();
            kv.put("email", username);
            kv.put("password", password);
            kv.put("v", "2.4");
            kv.put("action", "pair");
            kv.put("deviceId", deviceId);
            kv.put("country", "N/A");

            conn = (HttpURLConnection) new URL(login).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
            kv.write(wr);
            wr.flush();
            wr.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject res = parseKVP(in);
            conn.disconnect();

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK &&
                    "OK".contentEquals(res.getString("_0")) &&
                    res.has("authToken")) {
                authToken = res.getString("authToken");
                return Status.OK;
            }
            Log.e(getName(), "FAIL: code: " + responseCode + ", msg=" + amsg + ", res="
                    + res.toString());
            return s;
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

    private static JSONObject parseKVP(BufferedReader in) throws IOException, JSONException {
        JSONObject obj = new JSONObject();
        int lineno = 0;
        String s;
        while ((s = in.readLine()) != null) {
            int c = s.indexOf('=');
            if (c == -1) {
                obj.put("_" + Integer.toString(lineno), s);
            } else {
                obj.put(s.substring(0, c), s.substring(c + 1));
            }
            lineno++;
        }
        return obj;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        EndomondoTrack tcx = new EndomondoTrack(db);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            EndomondoTrack.Summary summary = new EndomondoTrack.Summary();
            StringWriter writer = new StringWriter();
            tcx.export(mID, writer, summary);

            String workoutId = deviceId + "-" + Long.toString(mID);
            Log.e(getName(), "workoutId: " + workoutId);

            StringBuilder url = new StringBuilder();
            url.append(UPLOAD_URL).append("?authToken=").append(authToken);
            url.append("&workoutId=").append(workoutId);
            url.append("&sport=").append(summary.sport);
            url.append("&duration=").append(summary.duration);
            url.append("&distance=").append(summary.distance);
            if (summary.hr != null) {
                url.append("&heartRateAvg=").append(summary.hr.toString());
            }
            url.append("&gzip=true");
            url.append("&extendedResponse=true");

            conn = (HttpURLConnection) new URL(url.toString()).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/octet-stream");
            OutputStream out = new GZIPOutputStream(
                    new BufferedOutputStream(conn.getOutputStream()));
            out.write(writer.getBuffer().toString().getBytes());
            out.flush();
            out.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            JSONObject res = parseKVP(in);
            conn.disconnect();

            Log.e(getName(), "res: " + res.toString());

            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK &&
                    "OK".contentEquals(res.getString("_0"))) {
                s.activityId = mID;
                return s;
            }
            ex = new Exception(amsg);
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
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
            case UPLOAD:
            case FEED:
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
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        StringBuilder url = new StringBuilder();
        url.append(FEED_URL).append("?authToken=").append(authToken);
        url.append("&maxResults=25");

        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            conn = (HttpURLConnection) new URL(url.toString()).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = SyncHelper.parse(in);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();

            conn.disconnect();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                parseFeed(feedUpdater, reply);
                return Status.OK;
            }
            ex = new Exception(amsg);
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    /*
     * {"message":{"short":"was out <0>running<\/0>.", "text":"was out
     * <0>running<\/0>. He tracked 6.64 km in 28m:56s.",
     * "date":"Yesterday at 10:31", "actions":[
     * {"id":233354212,"sport":0,"type":"workout","sport2":0}],
     * "text.win":"6.64 km in 28m:56s"}, "id":200472103,
     * "order_time":"2013-08-20 08:31:52 UTC",
     * "from":{"id":6408321,"picture":5521936, "name":"Jonas Oreland"},
     * "type":"workout"},
     */
    private void parseFeed(FeedUpdater feedUpdater, JSONObject reply) throws JSONException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.getDefault());
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        JSONArray arr = reply.getJSONArray("data");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            try {
                if ("workout".contentEquals(o.getString("type"))) {
                    final ContentValues c = parseWorkout(df, o);
                    feedUpdater.add(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ContentValues parseWorkout(SimpleDateFormat df, JSONObject o) throws JSONException,
            ParseException {
        final ContentValues c = new ContentValues();
        c.put(FEED.ACCOUNT_ID, getId());
        c.put(FEED.EXTERNAL_ID, o.getLong("id"));
        c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
        SyncHelper.setName(c, o.getJSONObject("from").getString("name"));
        final String IMAGE_URL = "http://image.endomondo.com/resources/gfx/picture/%d/thumbnail.jpg";
        c.put(FEED.USER_IMAGE_URL,
                String.format(IMAGE_URL, o.getJSONObject("from").getLong("picture")));
        c.put(FEED.START_TIME, df.parse(o.getString("order_time")).getTime());

        final JSONObject m = o.getJSONObject("message");
        setTrainingType(c, m.getJSONArray("actions").getJSONObject(0), m.getString("short"));
        setDistanceDuration(c, m.getString("text.win"));

        final String WORKOUT_URL = "http://www.endomondo.com/workouts/%d/%d";
        c.put(DB.FEED.URL, String.format(WORKOUT_URL,
                m.getJSONArray("actions").getJSONObject(0).getLong("id"),
                o.getJSONObject("from").getLong("id")));
        return c;
    }

    private void setDistanceDuration(ContentValues c, String string) {
        // 6.64 km in 28m:56s

        if (!string.contains(" in ")) {
            // either time or distance specified
            if (string.contains("km") || string.contains("mi")) {
                String dist[] = string.split(" ", 2);
                if (dist.length == 2) {
                    double d = Double.valueOf(dist[0]);
                    if (dist[1].contains("km"))
                        d *= Formatter.km_meters;
                    else if (dist[1].contains("mi"))
                        d *= Formatter.mi_meters;
                    c.put(DB.FEED.DISTANCE, d);
                }
            } else {
                boolean hms = string.matches("([0-9]+h:)?([0-9]{2}m:)?([0-9]{2}s)");
                String time[] = string.replaceAll("[hms]", "").split(":");
                if (hms) {
                    long duration = 0;
                    long mul = 1;
                    for (int i = 0; i < time.length; i++) {
                        duration += (mul * Long.valueOf(time[time.length - 1 - i]));
                        mul = mul * 60;
                    }
                    c.put(DB.FEED.DURATION, duration);
                }
            }
        } else {
            String arr[] = string.split(" in ");
            if (arr.length >= 1) {
                String dist[] = arr[0].split(" ", 2);
                if (dist.length == 2) {
                    double d = Double.valueOf(dist[0]);
                    if (dist[1].contains("km"))
                        d *= Formatter.km_meters;
                    else if (dist[1].contains("mi"))
                        d *= Formatter.mi_meters;
                    c.put(DB.FEED.DISTANCE, d);
                }
            }
            if (arr.length >= 2) {
                String time[] = arr[1].replaceAll("[hms]", "").split(":");
                long duration = 0;
                long mul = 1;
                for (int i = 0; i < time.length; i++) {
                    duration += (mul * Long.valueOf(time[time.length - 1 - i]));
                    mul = mul * 60;
                }
                c.put(DB.FEED.DURATION, duration);
            }
        }
    }

    private void setTrainingType(ContentValues c, JSONObject obj, String txt) throws JSONException {
        if ("workout".contentEquals(obj.getString("type"))) {
            Sport s = endomondo2sportMap.get(obj.getInt("sport"));
            if (s != null) {
                c.put(DB.FEED.FEED_SUBTYPE, s.getDbValue());
                return;
            }
        }
        String sportTxt = "something";
        // <0>running<\/0>
        if (txt.matches(".*<0>.*</0>.*")) {
            int start = txt.indexOf('>');
            int end = txt.indexOf('<', start);
            sportTxt = txt.substring(start+1, end);
        }
        // put in string instead...
        c.put(DB.FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_OTHER);
        c.put(DB.FEED.FEED_TYPE_STRING, sportTxt);
    }
}
