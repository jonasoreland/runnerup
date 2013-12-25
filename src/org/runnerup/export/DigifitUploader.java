/* Copyright (c) 2013, Sean Rees <sean@rees.us>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *    
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.runnerup.export;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.TCX;
import org.runnerup.util.Constants.DB;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

public class DigifitUploader extends FormCrawler implements Uploader {
	public static final String NAME = "Digifit";

	public static String DIGIFIT_URL = "http://my.digifit.com";

	public static void main(String args[]) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: DigifitUploader username password");
			System.exit(1);
		}

		String username = args[0];
		String password = args[1];

		DigifitUploader du = new DigifitUploader(null);
		du.init(username, password);

		System.err.println(du.connect());
	}

	private String _password;
	private String _username;
	private boolean _configured;
	private long _id;

	DigifitUploader(UploadManager unused) {
	}

	@Override
	public boolean checkSupport(Feature f) {
		switch (f) {
		case WORKOUT_LIST:
		case GET_WORKOUT:
		case UPLOAD:
			return true;
		case FEED:
		case LIVE:
			return false;
		}

		return false;
	}

	@Override
	public Status connect() {
		if (isConfigured()) {
			return Uploader.Status.OK;
		}

		JSONObject credentials = new JSONObject();
		try {
			credentials.put("login", _username);
			credentials.put("password", _password);
		} catch (JSONException e) {
			e.printStackTrace();
			return Uploader.Status.INCORRECT_USAGE;
		}

		Status errorStatus = Status.ERROR;
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(DIGIFIT_URL
					+ "/site/authenticate").openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");

			OutputStream out = conn.getOutputStream();
			out.write(credentials.toString().getBytes());
			out.flush();
			out.close();

			/*
			 * A success message looks like:
			 * <response><result>success</result></response>
			 * 
			 * A failure message looks like: <response><error code="1102"
			 * message="Login or Password is not correct" /></response>
			 * 
			 * For flexibility (and ease), we won't do full XML parsing here.
			 * We'll simply look for a few key tokens and hope that's good
			 * enough.
			 */
			BufferedReader in = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			String line = in.readLine();

			if (conn.getResponseCode() == 200) {
				if (line.contains("<result>success</result>")) {
					// Store the authentication token.
					getCookies(conn);
					_configured = true;
					return Status.OK;
				} else {
					Status s = Status.NEED_AUTH;
					s.authMethod = Uploader.AuthMethod.USER_PASS;

					System.err.println("Error: " + line);
					return s;
				}
			}
			conn.disconnect();
		} catch (Exception ex) {
			errorStatus.ex = ex;
			ex.printStackTrace();
		}
		return errorStatus;
	}

	@Override
	public void downloadWorkout(File dst, String key) throws Exception {
		Map<String, String> exportParameters = new HashMap<String, String>();
		exportParameters.put("id", key);
		exportParameters.put("format", "tcx");

		long fileId = 0;
		int fileSize = 0;

		try {
			JSONObject exportRequest = buildRequest("workout", exportParameters);
			JSONObject exportResponse = callDigifitEndpoint(DIGIFIT_URL
					+ "/rpc/json/workout/export_web", exportRequest);
			System.err.println(exportResponse);

			// I have observed Digifit taking >15 seconds to generate a file.
			for (int i = 0; i < 60; i++) {
				JSONObject workoutFile = getWorkoutFileId(key);
				if (workoutFile != null) {
					fileId = workoutFile.getLong("file_id");
					fileSize = workoutFile.getInt("file_size");
					break;
				}

				Thread.sleep(500);
			}

			if (fileId == 0) {
				System.err.println("export file not ready on Digifit within deadline");
				return;
			}

			String downloadUrl = DIGIFIT_URL + "/workout/download/" + fileId;
			System.err.println("downloadUrl = " + downloadUrl);

			HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
			conn.setRequestMethod("GET");
			addCookies(conn);

			System.err.println("Response code = " + conn.getResponseCode());
			System.err.println("Request method = " + conn.getRequestMethod());
			
			InputStream in = new BufferedInputStream(conn.getInputStream());
			OutputStream out = new FileOutputStream(dst);
			int cnt = 0, readLen = 0;
			byte buf[] = new byte[1024];
			while ((readLen = in.read(buf)) != -1) {
				out.write(buf, 0, readLen);
				cnt += readLen;
			}
			System.err.println("Expected " + fileSize + " bytes, got " + cnt
					+ " bytes: " + (fileSize == cnt ? "OK" : "ERROR"));

			in.close();
			out.close();
			conn.disconnect();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			// If we error out above, try to ensure we clean up our mess.
			if (fileId == 0) {
				deleteFile(fileId, "export");
			}
		}
	}

	private void deleteFile(long fileId, String fileType) {
		try {
			String deleteUrl = DIGIFIT_URL
					+ "/rpc/json/userfile/delete_workout?file_id=" + fileId
					+ "&file_type=" + fileType;
			HttpURLConnection conn = (HttpURLConnection) new URL(deleteUrl)
					.openConnection();
			conn.setRequestMethod("GET");
			conn.setDoOutput(true);
			conn.addRequestProperty("Referer", DIGIFIT_URL
					+ "/site/workoutimport");
			addCookies(conn);

			System.err.println("deleteUrl = " + deleteUrl);
			System.err.println("Delete of " + fileId + " got "
					+ conn.getResponseCode());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private JSONObject getWorkoutFileId(String key) throws IOException,
			MalformedURLException, ProtocolException, JSONException {
		JSONObject exportListResponse = callDigifitEndpoint(DIGIFIT_URL
				+ "/rpc/json/workout/export_workouts_list", new JSONObject());
		System.err.println(exportListResponse);

		JSONArray exportList = exportListResponse.getJSONObject("response")
				.getJSONArray("export_list");

		for (int idx = 0;; idx++) {
			JSONObject export = exportList.optJSONObject(idx);
			if (export == null) {
				break;
			}
			long workoutId = export.getLong("workoutid");
			if (("" + workoutId).equals(key)) {
				return export;
			}
		}
		return null;
	}

	@Override
	public String getAuthConfig() {
		JSONObject json = new JSONObject();

		try {
			json.put("username", _username);
			json.put("password", _password);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return json.toString();
	}

	@Override
	public long getId() {
		return _id;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void init(ContentValues config) {
		_id = config.getAsLong("_id");

		String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
		if (auth != null) {
			try {
				JSONObject json = new JSONObject(auth);
				String username = json.optString("username", null);
				String password = json.optString("password", null);

				init(username, password);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	protected void init(String username, String password) {
		_username = username;
		_password = password;
	}

	@Override
	public boolean isConfigured() {
		return _configured;
	}

	@Override
	public Status listWorkouts(List<Pair<String, String>> list) {
		Status errorStatus = Status.ERROR;
		Map<String, String> requestParameters = new HashMap<String, String>();
		DateFormat rfc3339fmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss",
				Locale.US);
		Date now = new Date();

		/*
		 * For speed of loading (Digifit can be pokey), this month and last
		 * month.
		 */
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);
		cal.add(Calendar.DATE, -30);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		Date from = cal.getTime();

		requestParameters.put("sortOrder", "1"); // Reverse chronological.
		requestParameters.put("dateTo", rfc3339fmt.format(now));
		requestParameters.put("dateFrom", rfc3339fmt.format(from));

		try {
			JSONObject request = buildRequest("workout", requestParameters);
			JSONObject response = callDigifitEndpoint(DIGIFIT_URL
					+ "/rpc/json/workout/list", request);

			if (response == null)
				return errorStatus;

			JSONArray workouts = response.getJSONObject("response")
					.getJSONArray("workouts");
			for (int idx = 0;; idx++) {
				JSONObject workout = workouts.optJSONObject(idx);
				if (workout == null) {
					break;
				}
				StringBuffer title = new StringBuffer(workout.getJSONObject(
						"description").getString("title"));
				String id = "" + workout.getLong("id");
				String startTime = workout.getJSONObject("summary").getString(
						"startTime");

				// startTime is rfc3339, instead of parsing it, just strip
				// everything but the date.
				title.append(" (")
						.append(startTime.substring(0, startTime.indexOf("T")))
						.append(")");

				list.add(new Pair<String, String>(id, title.toString()));
			}

			return Status.OK;
		} catch (Exception ex) {
			System.err.println(ex);
			errorStatus.ex = ex;
		}
		return errorStatus;
	}

	private JSONObject buildRequest(String root,
			Map<String, String> requestParameters) throws JSONException {
		JSONObject json = new JSONObject();
		JSONObject request = new JSONObject(requestParameters);
		json.put(root, request);
		return json;
	}

	private JSONObject callDigifitEndpoint(String url, JSONObject request)
			throws IOException, MalformedURLException, ProtocolException,
			JSONException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url)
				.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.addRequestProperty("Content-Type",
				"application/x-www-form-urlencoded");
		addCookies(conn);

		OutputStream out = conn.getOutputStream();
		out.write(request.toString().getBytes());
		out.flush();
		out.close();

		JSONObject response = null;
		if (conn.getResponseCode() == 200) {
			try {
				response = parse(conn.getInputStream());
			} finally {
				conn.disconnect();
			}
		}

		return response;
	}

	@Override
	public void reset() {
		init(null, null);
		_id = 0L;
		_configured = false;
	}

	@Override
	public Status upload(SQLiteDatabase db, long mID) {
		Status s;
		if ((s = connect()) != Status.OK) {
			return s;
		}

		Status errorStatus = Status.ERROR;
		TCX tcx = new TCX(db);

		try {
			// I wonder why there's an API for getting a special upload path.
			// This seems obtuse.
			String uploadUrl = getUploadUrl();
			System.err.println("Digifit returned uploadUrl = " + uploadUrl);

			StringWriter wr = new StringWriter();
			tcx.export(mID, wr);

			uploadFileToDigifit(wr.toString(), uploadUrl);

			// We're using the form endpoint for the browser rather than what
			// the API does so we don't have reliable error information. The
			// site returns 200 on both success and failure.
			//
			// TODO: capture traffic from the app in order to use a better API
			// endpoint.
			return Status.OK;
		} catch (Exception ex) {
			errorStatus.ex = ex;
		}

		return errorStatus;
	}

	private String getUploadUrl() throws IOException, MalformedURLException,
			ProtocolException, JSONException {
		String getUploadUrl = DIGIFIT_URL
				+ "/rpc/json/workout/import_workouts_url";
		JSONObject response = callDigifitEndpoint(getUploadUrl,
				new JSONObject());

		String uploadUrl = response.getJSONObject("response")
				.getJSONObject("upload_url").getString("URL");
		System.err.println("uploadUrl = " + uploadUrl);
		return uploadUrl;
	}

	private void uploadFileToDigifit(String payload, String uploadUrl)
			throws Exception {
		System.err.println("Uploading to " + uploadUrl);
		
		uploadUrl = "http://192.168.10.109:12345";
		
		HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl)
				.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		addCookies(conn);

		String filename = "RunnerUp.tcx";

		Part<StringWritable> themePart = new Part<StringWritable>("theme",
				new StringWritable(FormCrawler.URLEncode("site")));
		Part<StringWritable> payloadPart = new Part<StringWritable>("userFiles",
				new StringWritable(payload));
		payloadPart.filename = filename;
		payloadPart.contentType = "application/octet-stream";
		Part<?> parts[] = { themePart, payloadPart };
		postMulti(conn, parts);

		if (conn.getResponseCode() != 200) {
			throw new Exception("got a non-200 response code from upload");
		}
		System.err.println("method = " + conn.getRequestMethod());

		System.err.println("payloadPart.filename = " + payloadPart.filename);
		
		System.err.println("Got" + conn.getResponseMessage() + " from Digifit");

		for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
			System.err.println(entry.getKey() + " = " + entry.getValue());
		}
		
		try {
			// Digifit takes a little while to process an import; about ~1
			// second is about all we should
			// wait. If it doesn't show up in the import list by then, we'll
			// clean it up in the next pass.
			Thread.sleep(1000);

			JSONObject response = callDigifitEndpoint(DIGIFIT_URL
					+ "/rpc/json/workout/import_workouts_list",
					new JSONObject());
			JSONArray uploadList = response.getJSONObject("response")
					.getJSONArray("upload_list");
			System.err.println("uploadList = " + uploadList);
			for (int idx = 0;; idx++) {
				JSONObject upload = uploadList.optJSONObject(idx);
				if (upload == null) {
					break;
				}
				// Only delete files we created.
				if (upload.getString("file_name").equals(filename))
					deleteFile(upload.getLong("file_id"), "import");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
