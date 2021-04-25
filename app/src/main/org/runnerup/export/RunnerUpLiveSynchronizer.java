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

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Scope;
import org.runnerup.workout.WorkoutInfo;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RunnerUpLiveSynchronizer extends DefaultSynchronizer implements WorkoutObserver {

    public static final String NAME = "RunnerUp LIVE";
    private static final String PUBLIC_URL = "https://weide.devsparkles.se/Demo/Map";
    private static final String POST_URL = "https://weide.devsparkles.se/api/Resource/";
    private final Context context;

    private long id = 0;
    private String username = null;
    private String password = null;
    private final String postUrl;
    private final Formatter formatter;
    private long mTimeLastLog;

    RunnerUpLiveSynchronizer(Context context) {
        this.context = context;

        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        postUrl = prefs.getString(res.getString(R.string.pref_runneruplive_serveradress), POST_URL);
        formatter = new Formatter(context);
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

    @ColorRes
    @Override
    public int getColorId() {return R.color.serviceRunnerUpLive;}

    @Override
    public String getPublicUrl() {
        return PUBLIC_URL;
    }

    @Override
    public void init(ContentValues config) {
        id = config.getAsLong("_id");
        String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (auth != null) {
            try {
                JSONObject tmp = new JSONObject(auth);
                //noinspection ConstantConditions
                username = tmp.optString("username", null);
                //noinspection ConstantConditions
                password = tmp.optString("password", null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        return username != null && password != null;
    }

    @NonNull
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
    }

    @NonNull
    @Override
    public Status connect() {
        if (isConfigured()) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;

        return s;
    }

    // translate between constants used in RunnerUp and those that weide choose
    // to use for his live server
    private int translateType(int type) {
        switch (type) {
            case DB.LOCATION.TYPE_START:
            case DB.LOCATION.TYPE_RESUME:
                return 0;
            case DB.LOCATION.TYPE_GPS:
                return 1;
            case DB.LOCATION.TYPE_PAUSE:
                return 2;
            case DB.LOCATION.TYPE_END:
                return 3;
            case DB.LOCATION.TYPE_DISCARD:
                return 6;
        }
        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        return 0;
    }

    @Override
    public void workoutEvent(WorkoutInfo workoutInfo, int type) {

        if (type == DB.LOCATION.TYPE_GPS) {
            double mMinLiveLogDelayMillis = 5000;
            if (System.currentTimeMillis()-mTimeLastLog < mMinLiveLogDelayMillis) {
                return;
            }
        }
        
        mTimeLastLog = System.currentTimeMillis();
        int externalType = translateType(type);
        long elapsedDistanceMeter = Math.round(workoutInfo.getDistance(Scope.ACTIVITY));
        long elapsedTimeMillis = Math.round(workoutInfo.getTime(Scope.ACTIVITY));

        Location location = workoutInfo.getLastKnownLocation();

        Intent msgIntent = new Intent(context, LiveService.class)
                .putExtra(LiveService.PARAM_IN_LAT, location.getLatitude())
                .putExtra(LiveService.PARAM_IN_LONG, location.getLongitude())
                .putExtra(LiveService.PARAM_IN_ALTITUDE, location.getAltitude())
                .putExtra(LiveService.PARAM_IN_TYPE, externalType)
                .putExtra(LiveService.PARAM_IN_ELAPSED_DISTANCE, formatter
                .formatDistance(Formatter.Format.TXT_LONG, elapsedDistanceMeter))
                .putExtra(
                LiveService.PARAM_IN_ELAPSED_TIME,
                formatter.formatElapsedTime(Formatter.Format.TXT_LONG,
                        Math.round(elapsedTimeMillis / 1000.0)))
                .putExtra(
                LiveService.PARAM_IN_PACE,
                formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, elapsedTimeMillis == 0 ? 0 :
                        elapsedDistanceMeter * 1000.0 / elapsedTimeMillis))
                .putExtra(LiveService.PARAM_IN_USERNAME, username)
                .putExtra(LiveService.PARAM_IN_PASSWORD, password)
                .putExtra(LiveService.PARAM_IN_SERVERADRESS, postUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.startForegroundService(msgIntent);
        } else {
            context.startService(msgIntent);
        }
    }

    public static class LiveService extends IntentService {

        public static final String PARAM_IN_ELAPSED_DISTANCE = "dist";
        public static final String PARAM_IN_ELAPSED_TIME = "time";
        public static final String PARAM_IN_PACE = "pace";
        public static final String PARAM_IN_USERNAME = "username";
        public static final String PARAM_IN_PASSWORD = "password";
        public static final String PARAM_IN_SERVERADRESS = "serveradress";
        public static final String PARAM_IN_LAT = "lat";
        public static final String PARAM_IN_LONG = "long";
        public static final String PARAM_IN_ALTITUDE = "altitude";
        public static final String PARAM_IN_TYPE = "type";

        public LiveService() {
            super("LiveService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {

            String mElapsedDistance = intent
                    .getStringExtra(PARAM_IN_ELAPSED_DISTANCE);
            String mElapsedTime = intent.getStringExtra(PARAM_IN_ELAPSED_TIME);
            String pace = intent.getStringExtra(PARAM_IN_PACE);
            String username = intent.getStringExtra(PARAM_IN_USERNAME);
            String password = intent.getStringExtra(PARAM_IN_PASSWORD);
            double lat = intent.getDoubleExtra(PARAM_IN_LAT, 0);
            double lon = intent.getDoubleExtra(PARAM_IN_LONG, 0);
            double alt = intent.getDoubleExtra(PARAM_IN_ALTITUDE, 0);
            int type = intent.getIntExtra(PARAM_IN_TYPE, 0);
            String serverAdress = intent.getStringExtra(PARAM_IN_SERVERADRESS);

            URL url;
            HttpURLConnection connect = null;
            try {
                url = new URL(serverAdress);
                connect = (HttpURLConnection) url.openConnection();

                connect.setDoOutput(true);
                connect.addRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connect.setRequestMethod(RequestMethod.POST.name());

                JSONObject data = new JSONObject();

                data.put("userName", username);
                data.put("password", password);
                data.put("lat", lat);
                data.put("long", lon);
                data.put("altitude", alt);
                data.put("runningEventType", type);
                data.put("TotalDistance", mElapsedDistance);
                data.put("TotalTime", mElapsedTime);
                data.put("Pace", pace);
                final OutputStream out = new BufferedOutputStream(connect.getOutputStream());
                out.write(data.toString().getBytes());
                out.flush();
                out.close();

                final int code = connect.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    //Probably too verbose at errors
                    Log.v(getClass().getSimpleName(), "Failed to push data: "+code);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            finally {
                if (connect != null) {
                    connect.disconnect();
                }
            }
        }
    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case LIVE:
                return true;
            case UPLOAD:
            case FEED:
            case GET_WORKOUT:
            case WORKOUT_LIST:
            case SKIP_MAP:
                break;
        }
        return false;
    }
}
