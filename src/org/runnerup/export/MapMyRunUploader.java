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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.TCX;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class MapMyRunUploader extends FormCrawler implements Uploader {

	public static final String NAME = "MapMyRun";
	private static final String CONSUMER_KEY = "294c54fae628d72f9ad1631d8b6ef251";
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

	static public String getString(JSONObject obj, String key) {
		try {
			return obj.getString(key);
		} catch (JSONException e) {
		}
		return null;
	}
	
	@Override
	public void init(ContentValues config) {
		id = config.getAsLong("_id");
		String authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
		if (authToken != null) {
			JSONObject tmp;
			try {
				tmp = new JSONObject(authToken);
				username = getString(tmp, "username");
				password = getString(tmp, "password");
				md5pass = getString(tmp, "md5pass");
				user_id = getString(tmp, "user_id");
				user_key = getString(tmp, "user_key");
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
	public Intent configure(Activity activity) {
		return null;
	}

	@Override
	public void reset() {
		username = null;
		password = null;
		md5pass = null;
		user_id = null;
		user_key = null;
	}

	@Override
	public Status login(ContentValues config) {
		if (isConfigured()) {
			return Status.OK;
		}
		if (username == null) {
			return Status.INCORRECT_USAGE;
		}
		Exception ex = null;
		HttpURLConnection conn = null;
		logout();
		try {
			if (md5pass == null && password == null) {
				return Status.INCORRECT_USAGE;
			}

			if (md5pass == null) {
				md5pass = md5(password);
			}
			
			/**
			 * get user id/key
			 */
			conn = (HttpURLConnection) new URL(GET_USER_URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			FormValues kv = new FormValues();
			kv.put("consumer_key", CONSUMER_KEY);
			kv.put("u", username);
			kv.put("p", md5pass);

			{
				OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();

				InputStream in = new BufferedInputStream(conn.getInputStream());
				JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
				conn.disconnect();

				JSONObject user = obj.getJSONObject("result").getJSONObject("output").getJSONObject("user");
				user_id = user.getString("user_id");
				user_key = user.getString("user_key");
				if (isConfigured()) {
					JSONObject save = new JSONObject();
					save.put("username", username);
					save.put("md5pass", md5pass);
					save.put("user_id", user_id);
					save.put("user_key", user_key);
					config.remove(DB.ACCOUNT.AUTH_CONFIG);
					config.put(DB.ACCOUNT.AUTH_CONFIG, save.toString());
					
					return Uploader.Status.OK;
				}
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

		Uploader.Status s = Uploader.Status.ERROR;
		s.ex = ex;
		if (ex != null) {
			ex.printStackTrace();
		}
		return s;
	}

	public static String md5(String s) {
	    try {
	        // Create MD5 Hash
	        MessageDigest digest = java.security.MessageDigest
	                .getInstance("MD5");
	        digest.update(s.getBytes());
	        byte messageDigest[] = digest.digest();
	 
	        // Create Hex String
	        StringBuffer hexString = new StringBuffer();
	        for (int i = 0; i < messageDigest.length; i++) {
	            String h = Integer.toHexString(0xFF & messageDigest[i]);
	            while (h.length() < 2)
	                h = "0" + h;
	            hexString.append(h);
	        }
	        return hexString.toString();
	 
	    } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	    }
	    return "";
	}

	@Override
	public Status upload(SQLiteDatabase db, long mID) {
		TCX tcx = new TCX(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			StringWriter writer = new StringWriter();
			tcx.export(mID, writer);

			conn = (HttpURLConnection) new URL(IMPORT_URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
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
				JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
				conn.disconnect();

				JSONObject result = obj.getJSONObject("result").getJSONObject("output").getJSONObject("result");
				final String workout_id = result.getString("workout_id");
				final String workout_key = result.getString("workout_key");
				final JSONObject workout = result.getJSONObject("workout");
				final String raw_workout_date = workout.getString("raw_workout_date");
				final String workout_type_id = workout.getString("workout_type_id");
				
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
				conn.setRequestMethod("POST");
				conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				wr = new BufferedOutputStream(conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();

				in = new BufferedInputStream(conn.getInputStream());
				obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
				conn.disconnect();
				
				return Uploader.Status.OK;
			}
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
