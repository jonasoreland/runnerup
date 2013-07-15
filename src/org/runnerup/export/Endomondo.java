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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.EndomondoTrack;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

/**
 * 
 * @author jonas
 * Based on https://github.com/cpfair/tapiriik
 */

public class Endomondo extends FormCrawler implements Uploader {

	public static final String NAME = "Endomondo";
	public static String AUTH_URL = "https://api.mobile.endomondo.com/mobile/auth";
	public static String UPLOAD_URL = "http://api.mobile.endomondo.com/mobile/track";
	
	long id = 0;
	private String username = null;
	private String password = null;
	private String deviceId = null;
	private String authToken = null;
	
	Endomondo(UploadManager uploadManager) {
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
		return Uploader.AuthMethod.POST;
	}

	@Override
	public void init(ContentValues config) {
		id = config.getAsLong("_id");
		String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
		if (auth != null) {
			JSONObject tmp;
			try {
				tmp = new JSONObject(auth);
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
	public Intent configure(Activity activity) {
		return null;
	}

	@Override
	public void reset() {
		username = null;
		password = null;
		deviceId = null;
		authToken = null;
	}

	@Override
	public Status login(ContentValues _config) {
		if (_config == null) {
			if (isConfigured())
				return Status.OK;
			else
				return Status.INCORRECT_USAGE;
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
			conn.setRequestMethod("POST");
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
			if (responseCode == 200 &&
				"OK".contentEquals(res.getString("_0")) &&
				res.has("authToken")){
				authToken = res.getString("authToken");
				
				JSONObject save = new JSONObject();
				save.put("username", username);
				save.put("password", password);
				save.put("deviceId", deviceId);
				save.put("authToken", authToken);
				_config.remove(DB.ACCOUNT.AUTH_CONFIG);
				_config.put(DB.ACCOUNT.AUTH_CONFIG, save.toString());
				return Status.OK;
			}
			System.err.println("FAIL: code: " + responseCode + ", msg=" + amsg + ", res=" + res.toString());
		} catch (MalformedURLException e) {
			ex = e;
		} catch (IOException e) {
			ex = e;
		} catch (JSONException e) {
			ex = e;
		}

		if (conn != null)
			conn.disconnect();

		Uploader.Status s = Uploader.Status.ERROR;
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
		EndomondoTrack tcx = new EndomondoTrack(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			EndomondoTrack.Summary summary= new EndomondoTrack.Summary();
			StringWriter writer = new StringWriter();
			tcx.export(mID, writer, summary);

			String workoutId = deviceId + "-" + Long.toString(mID);
			System.err.println("workoutId: " + workoutId);

			StringBuffer url = new StringBuffer();
			url.append(UPLOAD_URL + "?authToken="+authToken);
			url.append("&workoutId="+workoutId);
			url.append("&sport="+summary.sport);
			url.append("&duration="+summary.duration);
			url.append("&distance="+summary.distance);
			url.append("&gzip=true");
			url.append("&extendedResponse=true");

			conn = (HttpURLConnection) new URL(url.toString()).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type", "application/octet-stream");
			OutputStream out = new GZIPOutputStream(new BufferedOutputStream(conn.getOutputStream()));
			out.write(writer.getBuffer().toString().getBytes());
			out.flush();
			out.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			JSONObject res = parseKVP(in);
			conn.disconnect();

			System.err.println("res: " + res.toString());
			
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			if (responseCode == 200 && 
				"OK".contentEquals(res.getString("_0"))) {
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
		super.logout();
	}
};
