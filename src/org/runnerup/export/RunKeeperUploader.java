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

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class RunKeeperUploader extends FormCrawler implements Uploader, OAuth2Server {

	public static final String NAME = "RunKeeper";

	/**
	 * @todo register OAuth2Server
	 */
	public static String CLIENT_ID = null;
	public static String CLIENT_SECRET = null;

	// TODO: I get ssl error when using https
	public static final String AUTH_URL = "http://runkeeper.com/apps/authorize";
	public static final String TOKEN_URL = "http://runkeeper.com/apps/token";
	public static final String REDIRECT_URI = "http://localhost:8080/runnerup/runkeeper";

	public static final String REST_URL = "http://api.runkeeper.com";

	private long id = 0;
	private String authToken = null;
	private String fitnessActivitiesUrl = null;

	RunKeeperUploader(UploadManager uploadManager) {
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
	public AuthMethod getAuthMethod() {
		return Uploader.AuthMethod.OAUTH2;
	}

	@Override
	public void init(ContentValues config) {
		authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
		id = config.getAsLong("_id");
	}

	@Override
	public boolean isConfigured() {
		if (authToken == null)
			return false;
		return true;
	}

	@Override
	public Intent configure(Activity activity) {
		return OAuth2Activity.getIntent(activity, this);
	}

	@Override
	public void reset() {
		authToken = null;
	}

	@Override
	public Uploader.Status login(ContentValues _config) {
		if (isConfigured()) {
			if (this.fitnessActivitiesUrl != null) {
				return Uploader.Status.OK;
			}

			/**
			 * Get the fitnessActivities end-point
			 */
			String uri = null;
			HttpURLConnection conn = null;
			Exception ex = null;
			try {
				URL newurl = new URL(REST_URL + "/user");
				conn = (HttpURLConnection) newurl.openConnection();
				conn.setRequestProperty("Authorization", "Bearer " + authToken);
				InputStream in = new BufferedInputStream(conn.getInputStream());
				uri = new JSONObject(new Scanner(in).useDelimiter("\\A").next())
						.getString("fitness_activities");
			} catch (MalformedURLException e) {
				ex = e;
			} catch (IOException e) {
				ex = e;
			} catch (JSONException e) {
				ex = e;
			}

			if (conn != null) {
				conn.disconnect();
			}

			if (ex != null)
				ex.printStackTrace();

			if (uri != null) {
				fitnessActivitiesUrl = uri;
				return Uploader.Status.OK;
			}
			Uploader.Status s = Uploader.Status.ERROR;
			s.ex = ex;
			return s;
		}
		return Uploader.Status.INCORRECT_USAGE;
	}

	@Override
	public Uploader.Status upload(SQLiteDatabase db, final long mID) {
		/**
		 * Get the fitnessActivities end-point
		 */
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			URL newurl = new URL(REST_URL + fitnessActivitiesUrl);
			System.err.println("url: " + newurl.toString());
			conn = (HttpURLConnection) newurl.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Authorization", "Bearer " + authToken);
			conn.addRequestProperty("Content-type",
					"application/vnd.com.runkeeper.NewFitnessActivity+json");
			RunKeeper rk = new RunKeeper(db);
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
					conn.getOutputStream()));
			rk.export(mID, w);
			w.flush();
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			conn.disconnect();
			conn = null;
			if (responseCode >= 200 && responseCode < 300) {
				return Uploader.Status.OK;
			}
			ex = new Exception(amsg);
		} catch (MalformedURLException e) {
			ex = e;
		} catch (IOException e) {
			ex = e;
		}

		if (ex != null)
			ex.printStackTrace();

		if (conn != null) {
			conn.disconnect();
		}
		Uploader.Status s = Uploader.Status.ERROR;
		s.ex = ex;
		return s;
	}

	@Override
	public boolean checkSupport(Uploader.Feature f) {
		return false;
	}
	
	@Override
	public Status listWorkouts(List<Pair<String, String>> list) {
		return Status.OK;
	}

	@Override
	public void downloadWorkout(File dst, String key) {
	}
	
	@Override
	public void logout() {
		this.fitnessActivitiesUrl = null;
	}
};
