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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.TCX;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.export.oauth2client.OAuth2Server;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class RunningAHEAD extends FormCrawler implements Uploader, OAuth2Server {

	public static final String NAME = "RunningAHEAD";

	/**
	 * @todo register OAuth2Server
	 */
	public static String CLIENT_ID = null;
	public static String CLIENT_SECRET = null;

	public static final String AUTH_URL = "https://www.runningahead.com/oauth2/authorize";
	public static final String TOKEN_URL = "https://api.runningahead.com/oauth2/token";
	public static final String REDIRECT_URI = "http://localhost:8080/runnerup/runningahead";

	public static final String REST_URL = "https://api.runningahead.com/rest";
	public static final String IMPORT_URL = REST_URL + "/logs/me/workouts/tcx";
	
	private long id = 0;
	private String authToken = null;

	RunningAHEAD(UploadManager uploadManager) {
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
				return Uploader.Status.OK;
		}
		
		return Uploader.Status.INCORRECT_USAGE;
	}

	@Override
	public Uploader.Status upload(SQLiteDatabase db, final long mID) {
		String URL = IMPORT_URL + "?access_token=" + authToken;
		TCX tcx = new TCX(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			StringWriter writer = new StringWriter();
			tcx.export(mID, writer);
			conn = (HttpURLConnection) new URL(URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Encoding", "gzip");
			OutputStream out = new GZIPOutputStream(new BufferedOutputStream(conn.getOutputStream()));
			out.write(writer.toString().getBytes());
			out.flush();
			out.close();
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			System.err.println("code: " + responseCode + ", amsg: " + amsg);

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
			System.err.println("obj: " + obj);
			
			if (responseCode == 200 && obj.has("ids")) {
				conn.disconnect();
				return Status.OK;
			}
			ex = new Exception(amsg);
		} catch (IOException e) {
			ex = e;
		} catch (JSONException e) {
			ex = e;
		}

		Uploader.Status s = Uploader.Status.ERROR;
		s.ex = ex;
		if (ex != null) {
			ex.printStackTrace();
		}
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
	}
};
