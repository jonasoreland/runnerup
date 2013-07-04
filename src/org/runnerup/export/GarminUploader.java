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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.TCX;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class GarminUploader extends FormCrawler implements Uploader {

	public static final String NAME = "Garmin";
	public static String START_URL = "https://connect.garmin.com/signin";
	public static String LOGIN_URL = "https://connect.garmin.com/signin";
	public static String CHECK_URL = "http://connect.garmin.com/user/username";
	public static String UPLOAD_URL = "http://connect.garmin.com/proxy/upload-service-1.1/json/upload/.tcx";
	public static String LIST_WORKOUTS_URL = "http://connect.garmin.com/proxy/workout-service-1.0/json/workoutlist";
	public static String GET_WORKOUT_URL = "http://connect.garmin.com/proxy/workout-service-1.0/json/workout/";
	
	long id = 0;
	private String username = null;
	private String password = null;

	GarminUploader(UploadManager uploadManager) {
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getName() {
		return "Garmin";
	}

	@Override
	public AuthMethod getAuthMethod() {
		return Uploader.AuthMethod.POST;
	}

	@Override
	public void init(ContentValues config) {
		id = config.getAsLong("_id");
		String authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
		if (authToken != null) {
			JSONObject tmp;
			try {
				tmp = new JSONObject(authToken);
				username = tmp.getString("username");
				password = tmp.getString("password");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean isConfigured() {
		if (username != null && password != null)
			return true;
		return false;
	}

	@Override
	public Intent configure(Activity activity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void reset() {
		username = null;
		password = null;
	}

	@Override
	public Status login(ContentValues config) {
		Exception ex = null;
		HttpURLConnection conn = null;
		logout();
		try {

			/**
			 * connect to START_URL to get cookies
			 */
			conn = (HttpURLConnection) new URL(START_URL).openConnection();
			{
				int responseCode = conn.getResponseCode();
				String amsg = conn.getResponseMessage();
				getCookies(conn);
			}
			conn.disconnect();

			/**
			 * Then login using a post
			 */
			String login = LOGIN_URL;
			FormValues kv = new FormValues();
			kv.put("login", "login");
			kv.put("login:loginUsernameField", username);
			kv.put("login:password", password);
			kv.put("login:signInButton", "Sign In");
			kv.put("javax.faces.ViewState", "j_id1");
			
			conn = (HttpURLConnection) new URL(login).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			addCookies(conn);

			{
				kv.write(System.err);
				OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();
				int responseCode = conn.getResponseCode();
				String amsg = conn.getResponseMessage();
				System.err.println("code: " + responseCode + ", msg=" + amsg);
				getCookies(conn);
			}
			conn.disconnect();

			/**
			 * An finally check that all is OK
			 */
			conn = (HttpURLConnection) new URL(CHECK_URL).openConnection();
			addCookies(conn);
			{
				conn.connect();
				getCookies(conn);
				InputStream in = new BufferedInputStream(conn.getInputStream());
				JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
				conn.disconnect();
				int responseCode = conn.getResponseCode();
				String amsg = conn.getResponseMessage();
				System.err.println("obj: " + obj.toString());
				// Returns username(which is actually Displayname from profile) if logged in
				if (obj.getString("username").length() > 0) {
					return Uploader.Status.OK;
				} else {
					return Uploader.Status.CANCEL;
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

	@Override
	public Status upload(SQLiteDatabase db, long mID) {
		TCX tcx = new TCX(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			StringWriter writer = new StringWriter();
			tcx.export(mID, writer);
			conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			addCookies(conn);
			Part<StringWritable> part1 = new Part<StringWritable>("responseContentType",
					new StringWritable(URLEncoder.encode("text/html")));
			Part<StringWritable> part2 = new Part<StringWritable>("data",
					new StringWritable(writer.toString()));
			part2.filename = "RunnerUp.tcx";
			part2.contentType = "application/octet-stream";
			Part<?> parts[] = { part1, part2 };
			postMulti(conn, parts);
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			if (responseCode == 200) {
				conn.disconnect();
				return Status.OK;
			}
			ex = new Exception(amsg);
		} catch (IOException e) {
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
		switch(f) {
		case WORKOUT_LIST:
			return true;
		case GET_WORKOUT:
			return true;
		}
		return false;
	}

	@Override
	public Status listWorkouts(List<Pair<String, String>> list) {
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			conn = (HttpURLConnection) new URL(LIST_WORKOUTS_URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			addCookies(conn);
			conn.connect();
			getCookies(conn);
			InputStream in = new BufferedInputStream(conn.getInputStream());
			JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
			conn.disconnect();
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			if (responseCode == 200) {
				obj = obj.getJSONObject("com.garmin.connect.workout.dto.BaseUserWorkoutListDto");
				JSONArray arr = obj.getJSONArray("baseUserWorkouts");
				for (int i = 0; ; i++) {
					obj = arr.optJSONObject(i);
					if (obj == null)
						break;
					list.add(new Pair<String,String>(obj.getString("workoutId"), obj.getString("workoutName") + ".json"));
				}
				return Status.OK;
			}
			ex = new Exception(amsg);
		} catch (IOException e) {
			ex = e;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
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
	public void downloadWorkout(File dst, String key) throws Exception {
		HttpURLConnection conn = null;
		Exception ex = null;
		FileOutputStream out = null;
		try {
			conn = (HttpURLConnection) new URL(GET_WORKOUT_URL + key).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			addCookies(conn);
			conn.connect();
			getCookies(conn);
			InputStream in = new BufferedInputStream(conn.getInputStream());
			out = new FileOutputStream(dst);
			int cnt = 0;
			byte buf[] = new byte[1024];
			while (in.read(buf) > 0) {
				cnt += buf.length;
				out.write(buf);
			}
			System.err.println("downloaded workout key: " + key + " " + cnt + " bytes from " + getName());
			in.close();
			out.close();
			conn.disconnect();
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			if (responseCode == 200) {
				return;
			}
			ex = new Exception(amsg);
		} catch (Exception e) {
			ex = e;
		}

		if (conn != null) {
			try {
				conn.disconnect();
			} catch (Exception e) {
			}
		}
		
		if (out != null) {
			try {
				out.close();
			} catch (Exception e) {
			}
		}
		ex.printStackTrace();
		throw ex;
	}
	
	@Override
	public void logout() {
		super.logout();
	}
};
