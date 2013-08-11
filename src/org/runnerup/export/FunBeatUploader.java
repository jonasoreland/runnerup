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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.TCX;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.Encryption;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

/**
 * TODO:
 * 1) serious cleanup needed
 * 2) maybe reverse engineer 1.0.0.api.funbeat.se that I found...
 */
public class FunBeatUploader extends FormCrawler implements Uploader {

	public static final String NAME = "FunBeat";
	public static final String BASE_URL = "http://www.funbeat.se";
	public static final String START_URL = BASE_URL + "/index.aspx";
	public static final String LOGIN_URL = BASE_URL + "/index.aspx";
	public static final String UPLOAD_URL = BASE_URL
			+ "/importexport/upload.aspx";

	public static final String API_URL = "http://1.0.0.android.api.funbeat.se/json/Default.asmx/";
	
	private static String APP_ID = null;
	private static String APP_SECRET = null;

	long id = 0;
	private String username = null;
	private String password = null;
	private String loginID = null;
	private String loginSecretHashed = null;
	
	FunBeatUploader(UploadManager uploadManager) {
		if (APP_ID == null || APP_SECRET == null) {
			try {
				final JSONObject tmp = new JSONObject(uploadManager.loadData(this));
				APP_ID = tmp.getString("APP_ID");
				APP_SECRET = tmp.getString("APP_SECRET");
			} catch (final Exception ex) {
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
				loginID = tmp.optString("loginId", null);
				loginSecretHashed = tmp.optString("loginSecretHashed", null);
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
		return null;
	}

	@Override
	public void reset() {
		username = null;
		password = null;
		loginID = null;
		loginSecretHashed = null;
	}

	@Override
	public Status login(ContentValues _config) {
		Exception ex = null;
		HttpURLConnection conn = null;
		cookies.clear();
		formValues.clear();

		if (_config != null) {
			if (loginID == null || loginSecretHashed == null) {
				if (validateAndCreateSecrets(username, password)) {
					String authToken = _config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
					JSONObject tmp = null;
					if (authToken != null) {
						try {
							tmp = new JSONObject(authToken);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
					if (tmp == null)
						tmp = new JSONObject();
					try {
						tmp.put("username",  username);
						tmp.put("password",  password);
						tmp.put("loginId", loginID);
						tmp.put("loginSecretHashed", loginSecretHashed);
						_config.remove(DB.ACCOUNT.AUTH_CONFIG);
						_config.put(DB.ACCOUNT.AUTH_CONFIG, tmp.toString());
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		try {

			/**
			 * connect to START_URL to get cookies/formValues
			 */
			conn = (HttpURLConnection) new URL(START_URL).openConnection();
			conn.setInstanceFollowRedirects(false);
			{
				int responseCode = conn.getResponseCode();
				String amsg = conn.getResponseMessage();
				getCookies(conn);
				getFormValues(conn);
			}
			conn.disconnect();

			/**
			 * Then login using a post
			 */
			FormValues kv = new FormValues();
			String viewKey = findName(formValues.keySet(), "VIEWSTATE");
			String eventKey = findName(formValues.keySet(), "EVENTVALIDATION");
			String userKey = findName(formValues.keySet(), "Username");
			String passKey = findName(formValues.keySet(), "Password");
			String loginKey = findName(formValues.keySet(), "LoginButton");
			kv.put(viewKey, formValues.get(viewKey));
			kv.put(eventKey, formValues.get(eventKey));
			kv.put(userKey, username);
			kv.put(passKey, password);
			kv.put(loginKey, "Logga in");

			conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
			addCookies(conn);

			boolean ok = false;
			{
				OutputStream wr = new BufferedOutputStream(
						conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();
				int responseCode = conn.getResponseCode();
				String amsg = conn.getResponseMessage();
				getCookies(conn);
				if (responseCode == 302) {
					String redirect = conn.getHeaderField("Location");
					conn.disconnect();
					conn = (HttpURLConnection) new URL(BASE_URL + redirect)
							.openConnection();
					conn.setInstanceFollowRedirects(false);
					conn.setRequestMethod("GET");
					addCookies(conn);
					responseCode = conn.getResponseCode();
					amsg = conn.getResponseMessage();
					getCookies(conn);
				}
				String html = getFormValues(conn);
				ok = html.indexOf("Logga ut") > 0;

				conn.disconnect();
			}

			if (ok) {
				return Uploader.Status.OK;
			} else {
				return Uploader.Status.CANCEL;
			}
		} catch (MalformedURLException e) {
			ex = e;
		} catch (IOException e) {
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

	private boolean validateAndCreateSecrets(String username, String password) {
		try {
			JSONObject req = new JSONObject();
			req.put("username", username);
			req.put("passwordHashed", Encryption.toHex(Encryption.SHA1(password)));
			JSONObject reply = makeRequest("ValidateAndCreateSecrets", req).getJSONObject("d");
			loginID = reply.getString("LoginID");
			String loginSecret = reply.getString("LoginSecret");
			loginSecretHashed = Encryption.calculateRFC2104HMAC(loginSecret, APP_SECRET);
			return true;
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		return false;
	}

	private JSONObject makeRequest(String function, JSONObject arg) {
		HttpURLConnection conn = null;
		try {
			arg.put("applicationID", APP_ID);
			if (loginID != null && loginSecretHashed != null) {
				arg.put("loginID", loginID);
				arg.put("loginSecret", loginSecretHashed);
			}
			conn = (HttpURLConnection) new URL(API_URL+function).openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		
			OutputStream out = new BufferedOutputStream(conn.getOutputStream());
			out.write(arg.toString().getBytes("UTF-8"));
			out.flush();
			out.close();
			
			InputStream in = new BufferedInputStream(conn.getInputStream());
			JSONObject ret = new JSONObject(new Scanner(in).useDelimiter("\\A").next());
			conn.disconnect();
			return ret;
		} catch (JSONException ex) {
			ex.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (conn != null)
			conn.disconnect();
		return null;
	}
	
	@Override
	public Status upload(SQLiteDatabase db, long mID) {
		TCX tcx = new TCX(db);
		HttpURLConnection conn = null;
		Exception ex = null;
		try {
			StringWriter writer = new StringWriter();
			String id = tcx.export(mID, writer);
			conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
			conn.setInstanceFollowRedirects(false);
			addCookies(conn);
			getFormValues(conn); // execute the GET
			conn.disconnect();
			
			String viewKey = findName(formValues.keySet(), "VIEWSTATE");
			String eventKey = findName(formValues.keySet(), "EVENTVALIDATION");
			String fileKey = findName(formValues.keySet(), "FileUpload");
			String uploadKey = findName(formValues.keySet(), "UploadButton");

			Part<StringWritable> part1 = new Part<StringWritable>(viewKey,
					new StringWritable(formValues.get(viewKey)));

			Part<StringWritable> part2 = new Part<StringWritable>(eventKey,
					new StringWritable(formValues.get(eventKey)));

			Part<StringWritable> part3 = new Part<StringWritable>(fileKey,
					new StringWritable(writer.toString()));
			part3.contentType = "application/octet-stream";
			part3.filename = "jonas.tcx";

			Part<StringWritable> part4 = new Part<StringWritable>(uploadKey,
					new StringWritable(formValues.get(uploadKey)));
			Part<?> parts[] = { part1, part2, part3, part4 };

			conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			addCookies(conn);
			postMulti(conn, parts);
			int responseCode = conn.getResponseCode();
			String amsg = conn.getResponseMessage();
			getCookies(conn);
			String redirect = null;
			if (responseCode == 302) {
				redirect = conn.getHeaderField("Location");
				conn.disconnect();
				conn = (HttpURLConnection) new URL(BASE_URL + redirect)
						.openConnection();
				conn.setInstanceFollowRedirects(false);
				conn.setRequestMethod("GET");
				addCookies(conn);
				responseCode = conn.getResponseCode();
				amsg = conn.getResponseMessage();
				getCookies(conn);
			}
			getFormValues(conn);
			conn.disconnect();

			viewKey = findName(formValues.keySet(), "VIEWSTATE");
			eventKey = findName(formValues.keySet(), "EVENTVALIDATION");
			String nextKey = findName(formValues.keySet(), "NextButton");
			String hidden = findName(formValues.keySet(), "ChoicesHiddenField");

			FormValues kv = new FormValues();
			kv.put(viewKey, formValues.get(viewKey));
			kv.put(eventKey, formValues.get(eventKey));
			kv.put(nextKey, "Nasta >>");
			kv.put(hidden, "[ \"import///" + id + "///tcx\" ]");
			
			String surl = BASE_URL + redirect;
			conn = (HttpURLConnection) new URL(surl).openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
			addCookies(conn);
			{
				OutputStream wr = new BufferedOutputStream(
						conn.getOutputStream());
				kv.write(wr);
				wr.flush();
				wr.close();
				responseCode = conn.getResponseCode();
				amsg = conn.getResponseMessage();
				getCookies(conn);
				if (responseCode == 302) {
					redirect = conn.getHeaderField("Location");
					conn.disconnect();
					conn = (HttpURLConnection) new URL(BASE_URL + redirect)
							.openConnection();
					conn.setInstanceFollowRedirects(false);
					conn.setRequestMethod("GET");
					addCookies(conn);
					responseCode = conn.getResponseCode();
					amsg = conn.getResponseMessage();
					getCookies(conn);
				}
				String html = getFormValues(conn);
				boolean ok = html.indexOf("r klar") > 0;
				System.err.println("ok: " + ok);

				conn.disconnect();
				if (ok) {
					return Uploader.Status.OK;
				} else {
					return Uploader.Status.CANCEL;
				}
			}
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
		cookies.clear();
		formValues.clear();
	}
};
