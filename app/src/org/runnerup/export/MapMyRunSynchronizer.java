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
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.util.Encryption;
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
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MapMyRunSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "MapMyRun";
    private static String CONSUMER_KEY;
    private static final String BASE_URL = "https://api.mapmyfitness.com/3.1";
    private static final String GET_USER_URL = BASE_URL + "/users/get_user";
    private static final String IMPORT_URL = BASE_URL + "/workouts/import_tcx";
    private static final String UPDATE_URL = BASE_URL + "/workouts/edit_workout";

    long id = 0;
    private String username = null;
    private String password = null;
    private String md5pass = null;
    private String user_id = null;
    private String user_key = null;

    static final Map<Integer, Sport> mapmyrun2sportMap = new HashMap<Integer, Sport>();
    static final Map<Sport, Integer> sport2mapmyrunMap = new HashMap<Sport, Integer>();
    static {
        // V3 API (what we use): 16 is Walking (specifically a long walk)
        // V7 API is different: 11: Biking, 16: Running, 9: Walk
        // documentation can be found at http://www.mapmyrun.com/api/3.1/?doc
        mapmyrun2sportMap.put(1, Sport.RUNNING);
        mapmyrun2sportMap.put(3, Sport.BIKING);
        for (Integer i : mapmyrun2sportMap.keySet()) {
            sport2mapmyrunMap.put(mapmyrun2sportMap.get(i), i);
        }
    }

    MapMyRunSynchronizer(SyncManager syncManager) {
        if (CONSUMER_KEY == null) {
            try {
                JSONObject tmp = new JSONObject(syncManager.loadData(this));
                CONSUMER_KEY = tmp.getString("CONSUMER_KEY");
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
                md5pass = tmp.optString("md5pass", null);
                user_id = tmp.optString("user_id", null);
                user_key = tmp.optString("user_key", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && md5pass != null &&
                user_id != null && user_key != null) {
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
            tmp.put("md5pass", md5pass);
            tmp.put("user_id", user_id);
            tmp.put("user_key", user_key);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        md5pass = null;
        user_id = null;
        user_key = null;
    }

    private String toHexString(byte messageDigest[]) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String h = Integer.toHexString(0xFF & b);
            while (h.length() < 2)
                h = "0" + h;
            hexString.append(h);
        }
        return hexString.toString();

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

        Exception ex = null;
        HttpURLConnection conn = null;
        try {
            String pass = md5pass;
            if (pass == null) {
                pass = toHexString(Encryption.md5(password));
            }

            /**
             * get user id/key
             */
            conn = (HttpURLConnection) new URL(GET_USER_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            FormValues kv = new FormValues();
            kv.put("consumer_key", CONSUMER_KEY);
            kv.put("u", username);
            kv.put("p", pass);

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                InputStream in = new BufferedInputStream(conn.getInputStream());
                JSONObject obj = SyncHelper.parse(in);
                conn.disconnect();

                try {
                    JSONObject user = obj.getJSONObject("result").getJSONObject("output")
                            .getJSONObject("user");
                    user_id = user.getString("user_id");
                    user_key = user.getString("user_key");
                    md5pass = pass;
                    return Synchronizer.Status.OK;
                } catch (JSONException e) {
                    Log.e(getName(), "obj: " + obj);
                    throw e;
                }
            }
        } catch (MalformedURLException e) {
            ex = e;
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        } catch (NoSuchAlgorithmException e) {
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

        TCX tcx = new TCX(db);
        HttpURLConnection conn = null;
        Exception ex = null;
        try {
            StringWriter writer = new StringWriter();
            Pair<String, Sport> res = tcx.exportWithSport(mID, writer);
            Sport sport = res.second;

            conn = (HttpURLConnection) new URL(IMPORT_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            FormValues kv = new FormValues();
            kv.put("consumer_key", CONSUMER_KEY);
            kv.put("u", username);
            kv.put("p", md5pass);
            kv.put("o", "json");
            kv.put("baretcx", "1");
            kv.put("tcx", writer.toString());

            {
                OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                InputStream in = new BufferedInputStream(conn.getInputStream());
                JSONObject obj = SyncHelper.parse(in);
                conn.disconnect();
                Log.e(getName(), obj.toString());
                JSONObject result = obj.getJSONObject("result").getJSONObject("output")
                        .getJSONObject("result");
                final String workout_id = result.getString("workout_id");
                final String workout_key = result.getString("workout_key");
                final JSONObject workout = result.getJSONObject("workout");
                final String raw_workout_date = workout.getString("raw_workout_date");
                String workout_type_id = workout.getString("workout_type_id");
                if (sport != null && sport2mapmyrunMap.containsKey(sport))
                    workout_type_id = sport2mapmyrunMap.get(sport).toString();

                kv.clear();
                kv.put("consumer_key", CONSUMER_KEY);
                kv.put("u", username);
                kv.put("p", md5pass);
                kv.put("o", "json");
                kv.put("workout_id", workout_id);
                kv.put("workout_key", workout_key);
                kv.put("workout_type_id", workout_type_id);
                kv.put("workout_description", "RunnerUp - " + raw_workout_date);
                kv.put("notes", tcx.getNotes());
                kv.put("privacy_setting", "1"); // friends

                conn = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod(RequestMethod.POST.name());
                conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                wr = new BufferedOutputStream(conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();

                in = new BufferedInputStream(conn.getInputStream());
                SyncHelper.parse(in);
                conn.disconnect();

                s = Status.OK;
                s.activityId = mID;
                return s;
            }
        } catch (IOException e) {
            ex = e;
        } catch (JSONException e) {
            ex = e;
        }

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        if (ex != null) {
            ex.printStackTrace();
        }
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
