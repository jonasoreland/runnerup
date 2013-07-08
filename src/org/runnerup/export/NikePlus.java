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
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.NikeXML;
import org.runnerup.util.Constants.DB;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class NikePlus extends FormCrawler implements Uploader {

	public static final String NAME = "Nike+";
	private static String CLIENT_ID = null;
	private static String CLIENT_SECRET = null;
	private static String APP_ID = null;
	
	private static final String LOGIN_URL = "https://api.nike.com/nsl/v2.0/user/login?client_id=%s&client_secret=%s&app=%s";
	private static final String SYNC_URL = "https://api.nike.com/v2.0/me/sync?access_token=%s";
	private static final String SYNC_COMPLETE_URL = "https://api.nike.com/v2.0/me/sync/complete?access_token=%s";

	private static final String USER_AGENT = "NPConnect";
	
	long id = 0;
	private String username = null;
	private String password = null;
	private String access_token = null;
	private long expires_timeout = 0;
	
	NikePlus(UploadManager uploadManager) {
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
	public Intent configure(Activity activity) {
		return null;
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
	public Status login(ContentValues config) {
		if (access_token != null) {
			if (now() > expires_timeout)
				access_token = null;
			return Status.OK;
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

			FormValues kv = new FormValues();
			kv.put("email", username);
			kv.put("password", password);

			{
				OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();

				InputStream in = new BufferedInputStream(conn.getInputStream());
				JSONObject obj = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
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

		Uploader.Status s = Uploader.Status.ERROR;
		s.ex = ex;
		if (ex != null) {
			ex.printStackTrace();
		}
		return s;
	}

	@Override
	public Status upload(SQLiteDatabase db, long mID) {
		NikeXML nikeXML = new NikeXML(db);
		GPX nikeGPX = new GPX(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			StringWriter xml = new StringWriter();
			nikeXML.export(mID, xml);
			
			StringWriter gpx = new StringWriter();
			nikeGPX.export(mID,  gpx);
			
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
			part2.contentTransferEncoding ="8bit";
			
			Part<?> parts[] = { part1, part2 };
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
