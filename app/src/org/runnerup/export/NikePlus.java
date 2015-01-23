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
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.activity.ExternalActivitySerializer;
import org.runnerup.activity.NikeActivitySerializer;
import org.runnerup.db.DBHelper;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.NikeXML;
import org.runnerup.feed.FeedList;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.Constants.DB.FEED;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@TargetApi(Build.VERSION_CODES.FROYO)
public class NikePlus extends FormCrawler implements Uploader, Downloader {


    public static final String NAME = "Nike+";
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;
    private static String APP_ID = null;

    private static final String BASE_URL = "https://api.nike.com";
    private static final String LOGIN_URL = BASE_URL
            + "/nsl/v2.0/user/login?client_id=%s&client_secret=%s&app=%s";
    private static final String DEV_ACCESS_TOKEN_URL = "https://developer.nike.com/services/login";
    private static final String SYNC_URL = BASE_URL + "/v2.0/me/sync?access_token=%s";
    private static final String SYNC_COMPLETE_URL = BASE_URL
            + "/v2.0/me/sync/complete?access_token=%s";

    private static final String USER_AGENT = "NPConnect";

    private static final String PROFILE_URL = BASE_URL + "/v1.0/me/profile?access_token=%s";
    private static final String MY_FEED_URL = BASE_URL
            + "/v1.0/me/home/feed?access_token=%s&start=%d&count=%d";
    private static final String FRIEND_FEED_URL = BASE_URL
            + "/v1.0/me/friends/feed?access_token=%s&startIndex=%d&count=%d";
    long id = 0;
    private String username = null;
    private String password = null;
    private String access_token = null;
    private long expires_timeout = 0;
    private String dev_access_token = null;
    private long dev_expires_timeout = 0;
    public static final String LIST_ACTIVITIES_URL = BASE_URL+"/v1/me/sport/activities?access_token=%s";
    public static final String DOWNLOAD_ACTIVITY_URL  = BASE_URL+"/v1/me/sport/activities/%s?access_token=%s";
    public static final String DOWNLOAD_ACTIVITY_GPS_URL  = BASE_URL+"/v1/me/sport/activities/%s/gps?access_token=%s";
    NikeActivitySerializer serializer;

    NikePlus(UploadManager uploadManager) {
        serializer = new NikeActivitySerializer(uploadManager.getContext());
        if (CLIENT_ID == null || CLIENT_SECRET == null || APP_ID == null) {
            try {
                JSONObject tmp = new JSONObject(uploadManager.loadData(this));
                CLIENT_ID = tmp.getString("CLIENT_ID");
                CLIENT_SECRET = tmp.getString("CLIENT_SECRET");
                APP_ID = tmp.getString("APP_ID");
            } catch (Exception ex) {
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
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && password != null) {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        access_token = null;
    }

    private static long now() {
        return android.os.SystemClock.elapsedRealtime() / 1000;
    }

    @Override
    public Status connect() {
        if (now() > expires_timeout) {
            access_token = null;
        }

        if (access_token != null) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Uploader.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        Exception ex = null;
        HttpURLConnection conn = null;
        try {
            /**
             * get user id/key
             */
            String url = String.format(LOGIN_URL, CLIENT_ID, CLIENT_SECRET, APP_ID);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.addRequestProperty("Accept", "application/json");

            FormValues kv = new FormValues();
            kv.put("email", username);
            kv.put("password", password);

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                String response;
                {
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            conn.getInputStream()));
                    StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        buf.append(line);
                    }
                    response = buf.toString().replaceAll("<User>.*</User>", "\"\"");
                    System.err.println("buf: " + buf.toString());
                    System.err.println("res: " + response);
                }
                JSONObject obj = parse(new ByteArrayInputStream(response.getBytes()));
                conn.disconnect();

                access_token = obj.getString("access_token");
                String expires = obj.getString("expires_in");
                expires_timeout = now() + Long.parseLong(expires);
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

    public Status devConnect() {
        if (now() > dev_expires_timeout) {
            dev_access_token = null;
        }

        if (dev_access_token != null) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Uploader.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        Exception ex = null;
        HttpURLConnection conn = null;
        try {
            /**
             * get user id/key
             */
            conn = (HttpURLConnection) new URL(DEV_ACCESS_TOKEN_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.addRequestProperty("Accept", "application/json");
            //conn.addRequestProperty("Cookie","NIKE_LANG_LOCALE=en_US; guidS=a80d858b-6aa3-4022-9955-36850126bc6f; guidU=c5133ccc-ceac-4afe-baf9-44be143f6ca2; fbm_1d456c307b93f0841eadff3d186cb302=base_domain=.nike.com; glt_2_IIKHKceEmeqTxf-7vybsrw5g-6NnFG3ykf2bPYS5PwX7T09Mk-ykJZaAZMMIVzTN=LT3_-ixWGgIHT-CL5wNkV3WfzRhE1Zqy3kAh2AqSu2S1Isk%7CUUID%3Df115fc35239f4f5d9544d6c03f89e1d5; _gig_llp=facebook; _gig_llu=Viacheslav; AnalysisUserId=63.233.61.198.1420141365174358; RES_TRACKINGID=558532310184091; ResonanceSegment=1; smart_commerce_product=%7B%22name%22%3A%22Nike%20Air%20Max%202014%20Men's%20Running%20Shoe%22%2C%22url%22%3A%22http%3A%2F%2Fstore.nike.com%2Fus%2Fen_us%2Fpd%2Fair-max-2014-running-shoe%2Fpid-774362%2Fpgid-1508277%3Fsitesrc%3Dus1p%22%2C%22id%22%3A%22774362%22%2C%22style%22%3A%22621077%22%2C%22niketype%22%3A%22FOOTWEAR%22%2C%22primarysport%22%3A%22Footwear%22%2C%22image%22%3A%22http%3A%2F%2Fimages.nike.com%2Fis%2Fimage%2FDotCom%2F621077_001_A%3F%24AFI%24%26fmt%3Dpng-alpha%26resMode%3Dsharp%26hei%3D100%26wid%3D138%22%2C%22price%22%3A%22%24180.00%22%2C%22copy%22%3A%7B%22title%22%3A%22%20KEEP%20IT%3Cbr%2F%3EFRESH.%22%2C%22sub_text%22%3A%22%20Be%20right%20in%20the%20Nike%20Air%20Max%202014%20Men's%20Running%20Shoe.%22%2C%22button%22%3A%22%20CHECK%20IT%20OUT%20%3Cspan%20class%3D'icon_font'%3EB%3C%2Fspan%3E%22%7D%2C%22locale%22%3A%22en_US%22%2C%22gender%22%3A%22m%22%2C%22type%22%3A%22running%22%7D; fbm_84697719333=base_domain=.nike.com; AKNIKE=3Tf0xfCOS9V6AkP0nPkz960Peg4_9Pv5SJwpL54pnhCh5uskAhQYfzA; places_tour=true; hide_goal_bar=false; slCheck=23LQtDyXm7o/iw8usyUIYBgmRjUiggtpHZnyms48/oCK1PyHZqQ+NR266FenCKMF92tMUGVb27GDUwDixU8YoQ==; sls=3; mt.v=2.742657581.1420141351302; RT=sl=0&ss=1420217737367&tt=0&obo=0&bcn=%2F%2F36c3fef2.mpstat.us%2F&dm=nike.com&si=132a03ae-d696-44e7-a586-5921eec84e81&r=https%3A%2F%2Fsecure-nikeplus.nike.com%2Fplus%2Flogin%3Fd30561a2175724c5a33c308546c6fd82&ul=1420222702707&hd=1420222702765; utag_main=_st:1420377317545$v_id:014aa707e87400a16f5b3378134007068001b060009dc$_sn:7$_ss:0$_pn:23%3Bexp-session$ses_id:1420371654752%3Bexp-session; s_pers=%20s_fid%3D4392DFF4E07BA90F-2C0FDC2D0EC454E1%7C1483533917587%3B%20c5%3Ddeveloper%253Ereference%7C1420377317596%3B%20c6%3Dno%2520value%7C1420377317602%3B; s_sess=%20nike_referrer%3Dhttps%253A%252F%252Fwww.google.de%252F%3B%20s_ppv%3D74%257C0%3B%20s_cc%3Dtrue%3B%20c51%3Dhorizontal%3B%20v41%3Dbrowse%3B%20s_sq%3D%3B; s_vi=[CS]v1|2A52D19685011544-4000013860000A98[CE]\n");

            FormValues kv = new FormValues();
            kv.put("username", username);
            kv.put("password", password);

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                String response;
                {
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            conn.getInputStream()));
                    StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        buf.append(line);
                    }
                    response = buf.toString().replaceAll("<User>.*</User>", "\"\"");
                    System.err.println("buf: " + buf.toString());
                    System.err.println("res: " + response);
                }
                JSONObject obj = parse(new ByteArrayInputStream(response.getBytes()));
                conn.disconnect();

                dev_access_token = obj.getString("access_token");
                String expires = obj.getString("expires_in");
                dev_expires_timeout = now() + Long.parseLong(expires);
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

        NikeXML nikeXML = new NikeXML(db);
        GPX nikeGPX = new GPX(db);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            StringWriter xml = new StringWriter();
            nikeXML.export(mID, xml);

            StringWriter gpx = new StringWriter();
            nikeGPX.export(mID, gpx);

            String url = String.format(SYNC_URL, access_token);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);
            Part<StringWritable> part1 = new Part<StringWritable>("runXML",
                    new StringWritable(xml.toString()));
            part1.filename = "runXML.xml";
            part1.contentType = "text/plain; charset=US-ASCII";
            part1.contentTransferEncoding = "8bit";

            Part<StringWritable> part2 = new Part<StringWritable>("gpxXML",
                    new StringWritable(gpx.toString()));
            part2.filename = "gpxXML.xml";
            part2.contentType = "text/plain; charset=US-ASCII";
            part2.contentTransferEncoding = "8bit";

            Part<?> parts[] = {
                    part1, part2
            };
            postMulti(conn, parts);
            int responseCode = conn.getResponseCode();
            String amsg = conn.getResponseMessage();
            conn.connect();

            if (responseCode != 200) {
                throw new Exception(amsg);
            }

            url = String.format(SYNC_COMPLETE_URL, access_token);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("user-agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);

            responseCode = conn.getResponseCode();
            amsg = conn.getResponseMessage();
            conn.disconnect();
            if (responseCode == 200) {
                return Status.OK;
            }

            ex = new Exception(amsg);
        } catch (Exception e) {
            ex = e;
        }

        s = Uploader.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    @Override
    public boolean checkSupport(Uploader.Feature f) {
        switch (f) {
            case FEED:
            case UPLOAD:
            case DOWNLOAD:
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
    public void logout() {
        super.logout();
    }

    @Override
    public Status getFeed(FeedUpdater feedUpdater) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                    Locale.getDefault());
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            List<ContentValues> result = new ArrayList<ContentValues>();
            getOwnFeed(df, result);
            getFriendsFeed(df, result);
            FeedList.sort(result);
            feedUpdater.addAll(result);
            return Status.OK;
        } finally {

        }
    }

    JSONObject makeGetRequest(String url) throws MalformedURLException, IOException, JSONException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.addRequestProperty("Accept", "application/json");
            conn.addRequestProperty("User-Agent", USER_AGENT);
            conn.addRequestProperty("appid", APP_ID);
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = parse(in);
            final int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 200)
                return reply;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return new JSONObject();
    }

    private boolean parsePayload(ContentValues c, JSONObject p) throws NumberFormatException,
            JSONException {
        long duration = Long.parseLong(p.getString("duration")) / 1000;
        double distance = 1000 * Double.parseDouble(p.getString("distance"));
        if (duration < 0 || distance < 0) {
            return false;
        }

        if (duration > 0)
            c.put(FEED.DURATION, duration);
        if (distance > 0)
            c.put(FEED.DISTANCE, distance);

        return true;
    }

    private void getOwnFeed(SimpleDateFormat df, List<ContentValues> result) {
        try {
            JSONObject profile = makeGetRequest(String.format(PROFILE_URL, access_token));
            String first = profile.getString("firstName");
            String last = profile.getString("lastName");
            String userUrl = profile.getString("avatarFullUrl");
            JSONObject feed = makeGetRequest(String.format(MY_FEED_URL, access_token, 1, 25));
            JSONArray arr = feed.getJSONArray("events");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                try {
                    String type = e.getString("eventType");
                    if (!"APPLICATION.SYNC.RUN".contentEquals(type))
                        continue;

                    ContentValues c = new ContentValues();
                    c.put(FEED.ACCOUNT_ID, getId());
                    c.put(FEED.EXTERNAL_ID, e.getString("entityId"));
                    c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                    c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_RUNNING); // TODO
                    c.put(FEED.START_TIME, df.parse(e.getString("entityDate")).getTime());
                    if (e.has("payload")) {
                        JSONObject p = e.getJSONObject("payload");
                        if (!parsePayload(c, p))
                            continue; // skip this
                    }
                    c.put(FEED.USER_FIRST_NAME, first);
                    c.put(FEED.USER_LAST_NAME, last);
                    c.put(FEED.USER_IMAGE_URL, userUrl);
                    result.add(c);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getFriendsFeed(SimpleDateFormat df, List<ContentValues> result) {
        try {
            JSONObject feed = makeGetRequest(String.format(FRIEND_FEED_URL, access_token, 1, 25));
            JSONArray arr = feed.getJSONArray("friends");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i).getJSONObject("event");
                try {
                    String type = e.getString("eventType");
                    if (!"APPLICATION.SYNC.RUN".contentEquals(type))
                        continue;

                    ContentValues c = new ContentValues();
                    c.put(FEED.ACCOUNT_ID, getId());
                    c.put(FEED.EXTERNAL_ID, e.getString("entityId"));
                    c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                    c.put(FEED.FEED_SUBTYPE, DB.ACTIVITY.SPORT_RUNNING); // TODO
                    c.put(FEED.START_TIME, df.parse(e.getString("entityDate")).getTime());
                    if (e.has("payload")) {
                        JSONObject p = e.getJSONObject("payload");
                        if (!parsePayload(c, p))
                            continue; // skip this
                    }
                    c.put(FEED.USER_IMAGE_URL, e.getString("avatar"));
                    result.add(c);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void downloadActivity(File dst, String key) throws Exception{
        Status s;
        if ((s = devConnect()) != Status.OK) {
            return ;
        }

        HttpURLConnection conn = null;
        Exception ex = null;
        JSONObject obj = makeGetRequest(String.format(DOWNLOAD_ACTIVITY_URL, key, dev_access_token));

        if(obj.getBoolean("isGpsActivity")) {
            JSONObject gps = makeGetRequest(String.format(DOWNLOAD_ACTIVITY_GPS_URL, key, dev_access_token));
            obj.put("gps", gps);
        }
        ExternalActivitySerializer.writeJsonToFile(obj, dst);
    }

    @Override
    public Status listActivities(List<Pair<String, String>> list) {
        Status s;
        if ((s = devConnect()) != Status.OK) {
            return s;
        }

        HttpURLConnection conn = null;
        Exception ex = null;
        String nextURL = String.format(LIST_ACTIVITIES_URL, dev_access_token);
        try {
            while(nextURL!=null) {
                JSONObject obj = makeGetRequest(nextURL);

                //obj = obj.getJSONObject("com.garmin.connect.workout.dto.BaseUserWorkoutListDto");
                JSONArray arr = obj.getJSONArray("data");
                JSONObject pagination = obj.getJSONObject("paging");
                for (int i = 0; ; i++) {
                    obj = arr.optJSONObject(i);
                    if (obj == null)
                        break;
                    list.add(new Pair<String, String>(obj.getString("activityId"), obj
                            .getString("startTime")));
                    /*
                    JSONObject metrics = obj.getJSONObject("metricSummary");
                    System.out.println("--------------------------------------------------------");
                    System.out.println(DB.ACTIVITY.START_TIME + " = "+ obj.getString("startTime"));
                    System.out.println(DB.ACTIVITY.DISTANCE + " = "+ metrics.getString("distance"));
                    System.out.println(DB.ACTIVITY.TIME + " = "+ metrics.getString("duration"));
                    System.out.println(DB.ACTIVITY.NAME + " = "+ obj.getString("activityId"));
                    System.out.println(DB.ACTIVITY.COMMENT + " = "+ "0");
                    System.out.println(DB.ACTIVITY.SPORT + " = "+ obj.getString("activityType"));
                    System.out.println(DB.ACTIVITY.AVG_HR + " = "+ "0");
                    System.out.println(DB.ACTIVITY.MAX_HR + " = "+ "0");
                    System.out.println(DB.ACTIVITY.AVG_CADENCE + " = "+ "0");
                    */
                }

                nextURL = (pagination==null || pagination.isNull("next") )? null : BASE_URL+pagination.getString("next");
            }
            return Status.OK;

        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

        s = Uploader.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    @Override
    public ExternalActivitySerializer getActivitySerializer(){
        return serializer;
    }
}
