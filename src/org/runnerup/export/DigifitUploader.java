/* Copyright (c) 2013, Sean Rees <sean@rees.us>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following condition is met: 
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
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
import java.io.FileInputStream;
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

	public static String AUTH_URL = "http://my.digifit.com/site/authenticate";
	public static String LIST_WORKOUTS_URL = "http://my.digifit.com/rpc/json/workout/list";
	public static String UPLOAD_URL = "https://my.digifit.com/site/workoutimport";
	public static String EXPORT_WORKOUT_URL = "http://my.digifit.com/rpc/json/workout/export_web";
	public static String GET_WORKOUT_BASE_URL = "http://my.digifit.com/workout/download/";
	
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

	    //List<Pair<String, String>> workouts = new ArrayList<Pair<String, String>>();
		//du.listWorkouts(null);
		//System.out.println(workouts);
		
		/*File tmp = File.createTempFile("test", "tcx");
		du.downloadWorkout(tmp, "5841554954518528");
		System.out.println("tmp = " + tmp);
		Thread.sleep(20000);
		*/
		
		File tcx = new File(args[2]);
		InputStream s = new FileInputStream(tcx);
		StringBuffer str = new StringBuffer();
		byte buf[] = new byte[1024];
		int len = 0;
		while ((len = s.read(buf)) != -1) {
			str.append(new String(buf, 0, len));
		}
		String url = du.getUploadUrl();
		du.uploadFileToDigifit(str.toString(), url);
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
			HttpURLConnection conn = (HttpURLConnection) new URL(AUTH_URL).openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			OutputStream out = conn.getOutputStream();
			out.write(credentials.toString().getBytes());
			out.flush();
			out.close();
			
			/* A success message looks like:
			 * <response><result>success</result></response>
			 * 
			 * A failure message looks like:
			 * <response><error code="1102" message="Login or Password is not correct" /></response>
			 * 
			 * For flexibility (and ease), we won't do full XML parsing here. We'll simply look for a
			 * few key tokens and hope that's good enough.
			 */
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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
			JSONObject exportResponse = callDigifitEndpoint(
					DIGIFIT_URL + "/rpc/json/workout/export_web", 
					exportRequest);
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
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			addCookies(conn);
			
			InputStream in = new BufferedInputStream(conn.getInputStream());
			OutputStream out = new FileOutputStream(dst);
			int cnt = 0, readLen = 0;
			byte buf[] = new byte[1024];
			while ((readLen = in.read(buf)) != -1) {
				out.write(buf, 0, readLen);
				cnt += readLen;
			}
			System.err.println("Expected " + fileSize + " bytes, got " + cnt + " bytes: " +
					(fileSize == cnt ? "OK" : "ERROR"));
			
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
			String deleteUrl = DIGIFIT_URL + "/rpc/json/userfile/delete_workout?file_id=" + fileId + "&file_type=" + fileType;
			HttpURLConnection conn = (HttpURLConnection) new URL(deleteUrl).openConnection();
			conn.setRequestMethod("GET");
			conn.setDoOutput(true);
			conn.addRequestProperty("Referer", DIGIFIT_URL + "/site/workoutimport");
			addCookies(conn);

			System.err.println("deleteUrl = " + deleteUrl);
			System.err.println("Delete of " + fileId + " got " + conn.getResponseCode());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private JSONObject getWorkoutFileId(String key) throws IOException,
			MalformedURLException, ProtocolException, JSONException {
		JSONObject exportListResponse = callDigifitEndpoint(
				DIGIFIT_URL + "/rpc/json/workout/export_workouts_list",
				new JSONObject());
		System.err.println(exportListResponse);

		JSONArray exportList = exportListResponse.getJSONObject("response").getJSONArray("export_list");

		for (int idx = 0; ; idx++) {
			JSONObject export = exportList.optJSONObject(idx);
			if (export == null) {
				break;
			}
			long workoutId = export.getLong("workoutid");
			deleteFile(export.getLong("file_id"), "export");
			if ((""+workoutId).equals(key)) {
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
		_id = config.getAsLong("id");
	
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
		DateFormat rfc3339fmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss", Locale.US);
		Date now = new Date();
		
		/* For speed of loading (Digifit can be pokey), this month and last month. */
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);
		cal.add(Calendar.DATE, -30);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND,  0);
		Date from = cal.getTime();
		
		requestParameters.put("sortOrder", "1");	// Reverse chronological.
		requestParameters.put("dateTo", rfc3339fmt.format(now));
		requestParameters.put("dateFrom", rfc3339fmt.format(from));
	
		try {
			JSONObject request = buildRequest("workout", requestParameters);
			JSONObject response = callDigifitEndpoint(LIST_WORKOUTS_URL, request);

			if (response == null)
				return errorStatus;
			
			/* We need to specify some date ranges, as in:
			 * {"workout":{"sortOrder":"1","dateFrom":"2013-11-01T00:00:00","dateTo":"2013-12-23T23:59:59"}}:
			 * 
			 * Response is:
			 * {"response":{"total":16,"workouts":[{"distance":2.9700000000000002,"search":["carmichael","cold","shorter","intervals","2","to","4","minutes","on","off"],"description":{"rating":4,"mood":0,"title":"Carmichael","comments":"Cold! Shorter intervals, 2 to 4 minutes on / 2 to 4 minutes off.","intensity":4,"weather":5},"tags":["outdoors"],"elapsedSeconds":2487.0,"ecomodule":128,"totalBeats":146300,"calories":748,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"38.6421919","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"70.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"85.77053","altitudecurrent":"82.3385","gps_maxspeed":"6.4962907","altitudeavg":"0.0","gps_distance":"2.968893","maxBPM":"196.0","elapsedSeconds":"2487","startTime":"2013-12-23T07:53:06","altitudeend":"82.3385","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"146300.0","calories":"748.30383","primarySourceSpeedDistance":"gps","readCountBPM":"830.0","altitudemax":"100.647705","startLon":"-121.31189193","altitudemin":"16.086699"},"readCountBPM":830,"steps":0,"shareId":"7d11dbcc6bf011e39d3583c909b90655","manual":false,"workout_date":"2013-12-23T07:53:06","id":5841554954518528,"hasImages":false},{"distance":2.2599999999999998,"search":["mountain","view","morning","run","brief","hrm","failure","in","the","middle","12","minutes","jog","3","minute","walk","5","2"],"description":{"rating":4,"mood":4,"title":"Mountain View","comments":"Morning run; brief HRM failure in the middle. 12 minutes jog, 3 minute walk, 5 minute jog, 3 minute walk, 5 minute jog, 2 minute walk.","intensity":4,"weather":5},"tags":["outdoors"],"elapsedSeconds":1800.0,"ecomodule":128,"totalBeats":97100,"calories":475,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"37.38740606","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"56.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"13.12336","cadenceSensor":"NO","gps_maxspeed":"6.225308","altitudeavg":"0.0","gps_distance":"2.258817","maxBPM":"202.0","elapsedSeconds":"1800","startTime":"2013-12-19T06:27:28","altitudeend":"-33.1259","speedSensor":"NO","altitudecurrent":"-33.1259","speedAndCadenceSensor":"NO","totalBeats":"97100.0","calories":"475.33768","primarySourceSpeedDistance":"gps","readCountBPM":"600.0","altitudemax":"13.430939","startLon":"-122.08461545","altitudemin":"-65.944885"},"readCountBPM":600,"steps":0,"shareId":"3853fe6368be11e39882abbcece83522","manual":false,"workout_date":"2013-12-19T06:27:28","id":5907182054801408,"hasImages":false},{"distance":1.8200000000000001,"search":["mountain","view","jetlag","reduction","run","wow","did","i","wear","out","fast"],"description":{"rating":3,"mood":3,"title":"Mountain View","comments":"Jetlag reduction run. Wow did I wear out fast.","intensity":4,"weather":5},"tags":["jetlag","outdoors"],"elapsedSeconds":1380.0,"ecomodule":128,"totalBeats":80768,"calories":412,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"37.38751844","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"111.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"17.169727","altitudecurrent":"-16.185476","gps_maxspeed":"6.737088","altitudeavg":"0.0","gps_distance":"1.8230989","maxBPM":"207.0","elapsedSeconds":"1380","startTime":"2013-12-15T12:52:53","altitudeend":"-16.185476","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"80768.0","calories":"412.34445","primarySourceSpeedDistance":"gps","readCountBPM":"460.0","altitudemax":"43.379993","startLon":"-122.0845477","altitudemin":"-61.898514"},"readCountBPM":460,"steps":0,"shareId":"87e4609c65ce11e386cf67b5cab506ea","manual":false,"workout_date":"2013-12-15T12:52:53","id":5874574663090176,"hasImages":false},{"distance":3.6699999999999999,"search":["merrion","square","with","jonaso","much","longer","runs","shorter","breaks","still","very","slow"],"description":{"rating":4,"mood":4,"title":"Merrion Square","comments":"With jonaso@. Much longer runs with shorter breaks. Still very slow.","intensity":5,"weather":4},"tags":["outdoors"],"elapsedSeconds":3046.0,"ecomodule":128,"totalBeats":180481,"calories":927,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"53.33787698","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"117.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"253.25967","altitudecurrent":"205.28685","gps_maxspeed":"6.3324637","altitudeavg":"0.0","gps_distance":"3.674485","maxBPM":"197.0","elapsedSeconds":"3046","startTime":"2013-12-10T14:11:16","altitudeend":"205.28685","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"180481.0","calories":"927.00684","primarySourceSpeedDistance":"gps","readCountBPM":"1015.0","altitudemax":"333.18753","startLon":"-6.24022291","altitudemin":"169.4395"},"readCountBPM":1015,"steps":0,"shareId":"53b9b91461ac11e38b412535e50b88a2","manual":false,"workout_date":"2013-12-10T14:11:16","id":5890878358945792,"hasImages":false},{"distance":2.0,"search":["treadmill","5","minute","walk","30","jog","success","at","3","6","mph","incline","1","0","cool","down","with","stribb"],"description":{"rating":5,"mood":4,"title":"Treadmill","comments":"5 minute walk, 30 minute jog (success!) at 3.6 mph / incline 1.0, 3 minute cool down. With Stribb.","intensity":4,"weather":0,"maxHR":"197","distance":"2.00"},"tags":["indoors","nohrm"],"elapsedSeconds":2315.0,"ecomodule":128,"totalBeats":0,"calories":0,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"NO","isLocationIndoors":"YES","minBPM":"0.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","altitudecurrent":"-999999.0","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"0.0","elapsedSeconds":"2315","startTime":"2013-12-07T15:44:03","altitudeend":"-999999.0","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"0.0","calories":"0.221875","primarySourceSpeedDistance":"gps","readCountBPM":"2.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":2,"steps":0,"shareId":"31bcbab35f6811e3b191f370753f53d2","manual":false,"workout_date":"2013-12-07T15:44:03","id":5857961729589248,"hasImages":false},{"distance":1.4199999999999999,"search":["run","merrion","sq","with","stribb","there","are","12","mins","of","before","i","actually","started","the","workout","on","icardio","accomplishment","2","full","laps","around","at","a","jog"],"description":{"rating":3,"mood":4,"title":"Run","comments":"Merrion Sq with Stribb. There are ~12 mins of run before I actually started the workout on iCardio. Accomplishment: 2 full laps around Merrion Sq at a jog!","intensity":4,"weather":3},"tags":["outdoors"],"elapsedSeconds":1418.0,"ecomodule":128,"totalBeats":75287,"calories":365,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"53.3391729","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"143.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"205.89755","altitudecurrent":"177.56303","gps_maxspeed":"8.140095","altitudeavg":"0.0","gps_distance":"1.4243484","maxBPM":"197.0","elapsedSeconds":"1418","startTime":"2013-12-03T14:34:48","altitudeend":"177.56303","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"75287.0","calories":"364.7435","primarySourceSpeedDistance":"gps","readCountBPM":"474.0","altitudemax":"285.43307","startLon":"-6.24802254","altitudemin":"170.80252"},"readCountBPM":474,"steps":0,"shareId":"6d9465685c2b11e39b2fc3fb730c3d1e","manual":false,"workout_date":"2013-12-03T14:34:48","id":5898128263741440,"hasImages":false},{"distance":0.0,"search":["run","treadmill","3","7","mph","incline","1","0","15","minutes","solid","jog","followed","by","2","5","minute","cooldown","this","is","after","the","10min","fitness","assessment"],"description":{"rating":4,"mood":4,"title":"Run","comments":"Treadmill, 3.7 mph, incline 1.0. 15 minutes solid jog followed by 2.5 minute cooldown. This is after the 10min fitness assessment.","intensity":4,"weather":0},"tags":["indoors"],"elapsedSeconds":1050.0,"ecomodule":128,"totalBeats":64238,"calories":334,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"YES","minBPM":"142.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","altitudecurrent":"-999999.0","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"194.0","elapsedSeconds":"1050","startTime":"2013-12-01T15:43:07","altitudeend":"-999999.0","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"64238.0","calories":"334.39987","primarySourceSpeedDistance":"gps","readCountBPM":"351.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":351,"steps":0,"shareId":"c4ba9de85aa111e386402fa6ad9aedd1","manual":false,"workout_date":"2013-12-01T15:43:07","id":5318810189955072,"hasImages":false},{"distance":0.0,"search":["advanced","cardio"],"routine":{"routineDef":{"shouldcalcrestinghr":"true","requiresspeeddata":"false","name":"Advanced Cardio","requireshrdata":"true","v02maxcalcmethod":"1","shouldcalcvo2max":"false","createheartzonesfromlactatethreshold":"false","createheartzonesfrommaxhr":"true","assessment":"true","suppressstandardvoicealerts":"true","workoutType":"1"}},"description":{"rating":0,"mood":0,"title":"Advanced Cardio","comments":"","intensity":0,"weather":0},"tags":["advancedcardio"],"elapsedSeconds":606.0,"ecomodule":128,"totalBeats":30590,"calories":137,"summary":{"isCompletedAssessment":"YES","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"113.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","altitudecurrent":"-999999.0","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"200.0","elapsedSeconds":"606","startTime":"2013-12-01T15:29:36","altitudeend":"-999999.0","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"30590.0","calories":"136.82294","primarySourceSpeedDistance":"gps","readCountBPM":"203.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":203,"steps":0,"shareId":"33e4cf855a9f11e3866f7b4312013758","manual":false,"workout_date":"2013-12-01T15:29:36","assessmentResults":{"hrzPermID":"hrzPID24125664535555","pzPermID":"pzPID24125664535555","fitRank":42,"fitnessLevel":4,"successful":"true","restingHR":73.0,"v02Max":41.778079986572266,"lthr":169.43333435058594,"maxHR":199.33332824707031},"id":5789882202980352,"hasImages":false},{"distance":0.0,"search":["cp30","running","test","at","a","very","slow","jog","3","5","mph","to","sustain","as","long","i","could","interrupted","by","page"],"description":{"rating":3,"mood":4,"title":"CP30 Running Test","comments":"Running at a very slow jog -- 3.5 mph to sustain as long as I could. Interrupted by a page.","intensity":3,"weather":0},"tags":["cp30interrupted"],"elapsedSeconds":423.0,"ecomodule":128,"totalBeats":23427,"calories":111,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"129.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","altitudecurrent":"-999999.0","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"184.0","elapsedSeconds":"423","startTime":"2013-12-01T14:56:56","altitudeend":"-999999.0","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"23427.0","calories":"111.08183","primarySourceSpeedDistance":"gps","readCountBPM":"142.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":142,"steps":0,"shareId":"33176f575a9f11e38b422fa6ad9aedd1","manual":false,"workout_date":"2013-12-01T14:56:56","id":5809329814896640,"hasImages":false},{"distance":2.8599999999999999,"search":["run","with","stribb","more","running","and","shorter","breaks"],"description":{"rating":4,"mood":4,"title":"Run","comments":"With Stribb, with more running and shorter breaks.","intensity":4,"weather":3},"tags":["outdoors"],"elapsedSeconds":2342.0,"ecomodule":128,"totalBeats":140156,"calories":697,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"53.33861231","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"70.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"321.5223","altitudecurrent":"243.83202","gps_maxspeed":"37.678875","altitudeavg":"0.0","gps_distance":"2.8600373","maxBPM":"205.0","elapsedSeconds":"2342","startTime":"2013-11-29T14:22:35","altitudeend":"243.83202","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"140156.0","calories":"697.0888","primarySourceSpeedDistance":"gps","readCountBPM":"780.0","altitudemax":"321.5223","startLon":"-6.23864458","altitudemin":"165.73534"},"readCountBPM":780,"steps":0,"shareId":"34dea021590711e3b094f10dbfd48aac","manual":false,"workout_date":"2013-11-29T14:22:35","id":5869854494031872,"hasImages":false},{"distance":2.8599999999999999,"search":["run","with","stribb","not","as","much","energy","in","me","this","time","and","a","little","dull","pain","the","left","right","shins","near","end"],"description":{"rating":4,"mood":4,"title":"Run","comments":"With Stribb. Not as much energy in me this time and a little dull pain in the left and right shins near the end.","intensity":4,"weather":4},"tags":["outdoors"],"elapsedSeconds":2732.0,"ecomodule":128,"totalBeats":147977,"calories":695,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"53.33870156","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"70.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"154.66817","altitudecurrent":"215.59805","gps_maxspeed":"7.7056985","altitudeavg":"0.0","gps_distance":"2.8573668","maxBPM":"205.0","elapsedSeconds":"2732","startTime":"2013-11-26T14:12:31","altitudeend":"215.59805","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"147977.0","calories":"695.0763","primarySourceSpeedDistance":"gps","readCountBPM":"911.0","altitudemax":"319.09207","startLon":"-6.238707","altitudemin":"154.66817"},"readCountBPM":911,"steps":0,"shareId":"9063949756ad11e3bd1a2594ae421fe5","manual":false,"workout_date":"2013-11-26T14:12:31","id":5886725125570560,"hasImages":false},{"distance":2.27,"search":["run","with","stribb","down","the","grand","canal"],"description":{"rating":4,"mood":5,"title":"Run","comments":"With Stribb, down the Grand Canal.","intensity":5,"weather":5},"tags":["outdoors"],"elapsedSeconds":2101.0,"ecomodule":128,"totalBeats":121230,"calories":591,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":"53.3386442","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"109.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"217.00414","cadenceSensor":"NO","gps_maxspeed":"16.814295","altitudeavg":"0.0","gps_distance":"2.2748978","maxBPM":"206.0","elapsedSeconds":"2101","startTime":"2013-11-22T15:15:43","altitudeend":"191.1636","speedSensor":"NO","altitudecurrent":"191.1636","speedAndCadenceSensor":"NO","totalBeats":"121230.0","calories":"591.11127","primarySourceSpeedDistance":"gps","readCountBPM":"700.0","altitudemax":"256.858","startLon":"-6.23838437","altitudemin":"165.1356"},"readCountBPM":700,"steps":0,"shareId":"e72b4563538d11e3bea499b3ea35dde7","manual":false,"workout_date":"2013-11-22T15:15:43","id":5853396179353600,"hasImages":false},{"distance":0.0,"search":["run","7","min","4","2x2min","the","hrm","was","a","bit","flaky","at","beginning","overreading","by","30","50bpm"],"description":{"rating":4,"mood":4,"title":"Run","comments":"7 min, 4 min, 2x2min. The HRM was a bit flaky at the beginning (overreading by 30-50bpm).","intensity":4,"weather":0},"tags":["indoors"],"elapsedSeconds":1805.0,"ecomodule":128,"totalBeats":96803,"calories":452,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"YES","minBPM":"59.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","cadenceSensor":"NO","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"238.0","elapsedSeconds":"1805","startTime":"2013-11-18T14:34:31","altitudeend":"-999999.0","speedSensor":"NO","altitudecurrent":"-999999.0","speedAndCadenceSensor":"NO","totalBeats":"96803.0","calories":"451.87503","primarySourceSpeedDistance":"gps","readCountBPM":"602.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":602,"steps":0,"shareId":"d119dc00506211e3b863df2582d923bd","manual":false,"workout_date":"2013-11-18T14:34:31","id":5893640022917120,"hasImages":false},{"distance":0.0,"search":["run","5","minutes","2x3","2x2","the","hrm","failed","mid","way"],"description":{"rating":4,"mood":4,"title":"Run","comments":"5 minutes, 2x3 minutes, 2x2 minutes. The HRM failed mid way.","intensity":4,"weather":0},"tags":["indoors"],"elapsedSeconds":2100.0,"ecomodule":128,"totalBeats":96842,"calories":407,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","startLat":-1,"createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"YES","minBPM":"44.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"-999999.0","cadenceSensor":"NO","gps_maxspeed":"0.0","altitudeavg":"0.0","gps_distance":"0.0","maxBPM":"204.0","elapsedSeconds":"2100","startTime":"2013-11-16T12:34:40","altitudeend":"-999999.0","speedSensor":"NO","altitudecurrent":"-999999.0","speedAndCadenceSensor":"NO","totalBeats":"96842.0","calories":"407.3781","primarySourceSpeedDistance":"gps","readCountBPM":"700.0","altitudemax":"-999999.0","startLon":-1,"altitudemin":"-999999.0"},"readCountBPM":700,"steps":0,"shareId":"6c577d214ec011e3bcd7959ed3fa89c8","manual":false,"workout_date":"2013-11-16T12:34:40","id":5860723393560576,"hasImages":false},{"distance":2.71,"search":["run","by","myself","with","cartalk"],"description":{"rating":4,"mood":4,"title":"Run","comments":"By myself with CarTalk","intensity":4,"weather":5},"tags":["outdoors"],"elapsedSeconds":2286.0,"ecomodule":128,"totalBeats":127786,"calories":612,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"60.0","ecomodule":"128","footPodSensor":"NO","altitudestart":"229.6588","altitudecurrent":"207.25858","gps_maxspeed":"7.304739","altitudeavg":"0.0","gps_distance":"2.7136629","maxBPM":"205.0","elapsedSeconds":"2286","startTime":"2013-11-10T10:45:58","altitudeend":"207.25858","speedSensor":"NO","cadenceSensor":"NO","speedAndCadenceSensor":"NO","totalBeats":"127786.0","calories":"611.7865","primarySourceSpeedDistance":"gps","readCountBPM":"762.0","altitudemax":"337.72147","altitudemin":"163.01674"},"readCountBPM":762,"steps":0,"shareId":"be35fc8f49fa11e393415708c0685d18","manual":false,"workout_date":"2013-11-10T10:45:58","id":5876975549808640,"hasImages":false},{"distance":2.6600000000000001,"search":["run","with","stribb","in","merrion","sq"],"description":{"rating":4,"mood":4,"title":"Run","comments":"With Stribb in Merrion Sq","intensity":4,"weather":4},"tags":["outdoors"],"elapsedSeconds":2376.0,"ecomodule":128,"totalBeats":129358,"calories":610,"summary":{"isCompletedAssessment":"NO","totalDurationSeconds":"0","powerSensor":"NO","createAndroidVersion":"Android_19","heartSensor":"YES","isLocationIndoors":"NO","minBPM":"111.0","ecomodule":128,"footPodSensor":"NO","altitudestart":"209.06241","cadenceSensor":"NO","gps_maxspeed":"7.737917","altitudeavg":"0.0","gps_distance":"2.6567373","maxBPM":"204.0","elapsedSeconds":"2376","startTime":"2013-11-07T14:15:38","altitudeend":"220.25371","speedSensor":"NO","altitudecurrent":"220.25371","speedAndCadenceSensor":"NO","totalBeats":"129358.0","calories":"609.6653","primarySourceSpeedDistance":"gps","readCountBPM":"792.0","altitudemax":"332.52277","altitudemin":"148.29396"},"readCountBPM":792,"steps":0,"shareId":"ae51a22347bc11e395155175fb9dbb64","manual":false,"workout_date":"2013-11-07T14:15:38","id":5910046797987840,"hasImages":false}]}}
			 */
			JSONArray workouts = response.getJSONObject("response").getJSONArray("workouts");
			for (int idx = 0; ; idx++) {
				JSONObject workout = workouts.optJSONObject(idx);
				if (workout == null) {
					break;
				}
				StringBuffer title = new StringBuffer(workout.getJSONObject("description").getString("title"));
				String id = "" + workout.getLong("id");
				String startTime = workout.getJSONObject("summary").getString("startTime");
				
				// startTime is rfc3339, instead of parsing it, just strip everything but the datestamp.
				title.append(" (").append(startTime.substring(0, startTime.indexOf("T"))).append(")");
				
				list.add(new Pair<String, String>(title.toString(), id));
			}
			
			return Status.OK;
		} catch (Exception ex) {
			System.err.println(ex);
			errorStatus.ex = ex;
		}
		return errorStatus;
	}
	
	private JSONObject buildRequest(String root, Map<String, String> requestParameters) throws JSONException {
		JSONObject request = new JSONObject();
		request.put(root, requestParameters);
		return request;
	}
	
	private JSONObject callDigifitEndpoint(String url, JSONObject request)
			throws IOException, MalformedURLException, ProtocolException, JSONException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
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
			// I wonder why there's an API for getting a special upload path. This seems obtuse.
			String uploadUrl = getUploadUrl();
			System.err.println("Digifit returned uploadUrl = " + uploadUrl);
			
			StringWriter wr = new StringWriter();
			tcx.export(mID, wr);

			uploadFileToDigifit(wr.toString(), uploadUrl);
			
			// We're using the form endpoint for the browser rather than what the API does so
			// we don't have reliable error information. The site returns 200 on both success
			// and failure.
			//
			// TODO: capture traffic from the app in order to use a better API endpoint.
			return Status.OK;
		} catch (Exception ex) {
			errorStatus.ex = ex;
		}
		
		return errorStatus;
	}
	
	private String getUploadUrl() throws IOException, MalformedURLException,
			ProtocolException, JSONException {
		String getUploadUrl = DIGIFIT_URL + "/rpc/json/workout/import_workouts_url";
		JSONObject response = callDigifitEndpoint(getUploadUrl, new JSONObject());
		
		String uploadUrl = response.getJSONObject("response").getJSONObject("upload_url").getString("URL");
		System.err.println("uploadUrl = " + uploadUrl);
		return uploadUrl;
	}
	
	private void uploadFileToDigifit(String payload, String uploadUrl) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		addCookies(conn);
		
		String filename = "RunnerUp.tcx";
		
		Part<StringWritable> part1 = new Part<StringWritable>("theme",
				new StringWritable(FormCrawler.URLEncode("site")));
		Part<StringWritable> part2 = new Part<StringWritable>("userFiles",
				new StringWritable(payload));
		part2.filename = filename;
		part2.contentType = "application/octet-stream";
		Part<?> parts[] = { part1, part2 };
		postMulti(conn, parts);
		
		conn.getOutputStream().flush();
		conn.getOutputStream().close();
		
		if (conn.getResponseCode() != 200) {
			throw new Exception("got a non-200 response code from upload");
		}
		
		try {
			// Digifit takes a little while to process an import; about ~1 second is about all we should
			// wait. If it doesn't show up in the import list by then, we'll clean it up in the next pass.
			Thread.sleep(1000);
			
			JSONObject response = callDigifitEndpoint(DIGIFIT_URL + "/rpc/json/workout/import_workouts_list", new JSONObject());
			JSONArray uploadList = response.getJSONObject("response").getJSONArray("upload_list");
			System.out.println("uploadlist = " + uploadList);
			for (int idx = 0; ; idx++) {
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
	
	private void debugResponse(HttpURLConnection conn, boolean printOutput) throws IOException {
		System.out.println("Response code: " + conn.getResponseCode());
		if (printOutput) {
			BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = r.readLine()) != null) {
				System.out.println(line);
			}
		}
	}
}
