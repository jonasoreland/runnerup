package org.runnerup.gpstracker;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;
import org.runnerup.util.Formatter;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.IBinder;

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
		// TODO Auto-generated constructor stub
	}


	@Override
	protected void onHandleIntent(Intent intent) {
		
		
		String mElapsedDistance = intent.getStringExtra(PARAM_IN_ELAPSED_DISTANCE);
		String mElapsedTime = intent.getStringExtra(PARAM_IN_ELAPSED_TIME);
		String pace = intent.getStringExtra(PARAM_IN_PACE);
		String username = intent.getStringExtra(PARAM_IN_USERNAME);
		double lat = intent.getDoubleExtra(PARAM_IN_LAT, 0);
		double lon = intent.getDoubleExtra(PARAM_IN_LONG, 0);
		int type = intent.getIntExtra(PARAM_IN_TYPE, 0);
		String serverAdress = intent.getStringExtra(PARAM_IN_SERVERADRESS);
		if(username == "")
			return;
		HttpClient httpClient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(serverAdress);

		httpPost.setHeader("content-type", "application/json");
		JSONObject data = new JSONObject();
		try{
			data.put("userName", username);
			data.put("lat", lat);
			data.put("long", lon);
			data.put("runningEventType", type);
			data.put("TotalDistance", mElapsedDistance);
			data.put("TotalTime", mElapsedTime);
			data.put("Pace", pace);
			StringEntity entity = new StringEntity(data.toString(), HTTP.UTF_8);
			httpPost.setEntity(entity);
	
			HttpResponse response = httpClient.execute(httpPost);
			String test = response.toString();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	

}
