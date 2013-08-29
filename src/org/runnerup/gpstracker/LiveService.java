/*
 * Copyright (C) 2013 jonas.oreland@gmail.com, weides@gmail.com
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
package org.runnerup.gpstracker;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Intent;

public class LiveService extends IntentService {
	public static final String PARAM_IN_ELAPSED_DISTANCE = "dist";
	public static final String PARAM_IN_ELAPSED_TIME = "time";
	public static final String PARAM_IN_PACE = "pace";
	public static final String PARAM_IN_USERNAME = "username";
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
		double lat = intent.getDoubleExtra(PARAM_IN_LAT, 0);
		double lon = intent.getDoubleExtra(PARAM_IN_LONG, 0);
		int type = intent.getIntExtra(PARAM_IN_TYPE, 0);
		String serverAdress = intent.getStringExtra(PARAM_IN_SERVERADRESS);
		if (username == "")
			return;
		HttpClient httpClient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(serverAdress);

		httpPost.setHeader("content-type", "application/json");
		JSONObject data = new JSONObject();
		try {
			data.put("userName", username);
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

}
