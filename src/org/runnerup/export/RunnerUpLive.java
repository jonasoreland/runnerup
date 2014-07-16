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

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.Formatter;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunnerUpLive extends FormCrawler implements Uploader {

	public static final String NAME = "RunnerUp LIVE";
	public static String POST_URL = "http://weide.devsparkles.se/api/Resource/";
	
	long id = 0;
    private String username = null;
    private String password = null;
	private String postUrl = POST_URL;
	private Formatter formatter;
	
	RunnerUpLive(UploadManager uploadManager) {
		Resources res = uploadManager.getResources();
		SharedPreferences prefs = uploadManager.getPreferences(null);
		postUrl = prefs.getString(res.getString(R.string.pref_runneruplive_serveradress), POST_URL);
		formatter = new Formatter(uploadManager.getContext());
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
	}

	@Override
	public Status connect() {
		if (isConfigured()) {
			return Status.OK;
		}
		
		Status s = Status.NEED_AUTH;
		s.authMethod = Uploader.AuthMethod.USER_PASS;
        if (username == null || password == null) {
			return s;
		}
		
		return s;
	}

	@Override
	public void liveLog(Context context, Location location, int type, double mElapsedDistanceMeter, double mElapsedTimeMillis) {
		long elapsedDistanceMeter = Math.round(mElapsedDistanceMeter);
		Intent msgIntent = new Intent(context, LiveService.class);
		msgIntent.putExtra(LiveService.PARAM_IN_LAT, location.getLatitude());
		msgIntent.putExtra(LiveService.PARAM_IN_LONG, location.getLongitude());
		msgIntent.putExtra(LiveService.PARAM_IN_TYPE, type);
		msgIntent.putExtra(LiveService.PARAM_IN_ELAPSED_DISTANCE, formatter
				.formatDistance(Formatter.TXT_LONG, elapsedDistanceMeter));
		msgIntent.putExtra(
				LiveService.PARAM_IN_ELAPSED_TIME,
				formatter.formatElapsedTime(Formatter.TXT_LONG,
						Math.round(mElapsedTimeMillis / 1000)));
		msgIntent.putExtra(
				LiveService.PARAM_IN_PACE,
				formatter.formatPace(Formatter.TXT_SHORT, mElapsedTimeMillis
						/ (1000 * mElapsedDistanceMeter)));
		msgIntent.putExtra(LiveService.PARAM_IN_USERNAME, username);
        msgIntent.putExtra(LiveService.PARAM_IN_PASSWORD, password);
		msgIntent.putExtra(LiveService.PARAM_IN_SERVERADRESS, postUrl);
		context.startService(msgIntent);
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
			int type = intent.getIntExtra(PARAM_IN_TYPE, 0);
			String serverAdress = intent.getStringExtra(PARAM_IN_SERVERADRESS);

			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(serverAdress);

			httpPost.setHeader("content-type", "application/json");
			JSONObject data = new JSONObject();
			try {
				data.put("userName", username);
                data.put("password", password);
				data.put("lat", lat);
				data.put("long", lon);
				data.put("runningEventType", type);
				data.put("TotalDistance", mElapsedDistance);
				data.put("TotalTime", mElapsedTime);
				data.put("Pace", pace);
				StringEntity entity = new StringEntity(data.toString(), HTTP.UTF_8);
				httpPost.setEntity(entity);

				httpClient.execute(httpPost);
				/* HttpResponse response = */
				// String test = response.toString();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	@Override
	public boolean checkSupport(Uploader.Feature f) {
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
};
