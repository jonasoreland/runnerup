package org.runnerup.view;

import java.text.MessageFormat;
import java.util.Date;

import org.runnerup.R;
import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.TickListener;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class StartActivity extends Activity implements TickListener {
	GpsTracker mGpsTracker = null;
	org.runnerup.gpstracker.GpsStatus mGpsStatus = null;

	TextView debugView = null;

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		log("0 mGpsTracker = " + mGpsTracker + ", mSpeech = " + mSpeech);
		bindGpsTracker();
		log("1 mGpsTracker = " + mGpsTracker);

		mSpeech = new TextToSpeech(getApplicationContext(), mTTSOnInitListener);
		setContentView(R.layout.start);
		startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(startButtonClick);
		gpsInfoView1 = (TextView) findViewById(R.id.gpsInfo1);
		gpsInfoView2 = (TextView) findViewById(R.id.gpsInfo2);
		debugView = (TextView) findViewById(R.id.textView1);

		editText = (EditText) findViewById(R.id.editText1);
		sayButton = (Button) findViewById(R.id.button1);
		sayButton.setOnClickListener(sayButtonClick);
		Button button2 = (Button) findViewById(R.id.button2);
		button2.setOnClickListener(authButtonClick);

		mGpsStatus = new org.runnerup.gpstracker.GpsStatus(StartActivity.this);
	}

	Button startButton = null;
	TextView gpsInfoView1 = null;
	TextView gpsInfoView2 = null;

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindGpsTracker();

		if (mSpeech != null) {
			mSpeech.shutdown();
			mSpeech = null;
		}
		if (mGpsStatus != null) {
			mGpsStatus.stop(this);
			mGpsStatus = null;
		}
	}

	void log(String s) {
		if (debugView != null) {
			CharSequence curr = debugView.getText();
			int len = curr.length();
			if (len > 1000) {
				curr = curr.subSequence(0, 1000);
			}
			debugView.setText(s + "\n" + curr);
		}
	}

	void onGpsTrackerBound() {
		log("2 mGpsTracker = " + mGpsTracker);
		Context ctx = getApplicationContext();
		if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(
				"pref_startgps", false) == true) {
			log("autostart gps");
			mGpsTracker.startLogging();
			mGpsStatus.start(this);
		} else {
			log("don't autostart gps");
		}
		updateView();
	}

	OnClickListener startButtonClick = new OnClickListener() {
		public void onClick(View v) {
			if (mGpsTracker.isLogging() == false) {
				mGpsTracker.startLogging();
				mGpsStatus.start(StartActivity.this);
			} else if (mGpsStatus.isFixed()) {
				log("state=" + mGpsTracker.getState());
				mGpsStatus.stop(StartActivity.this);
				Context ctx = getApplicationContext();
				Workout w = WorkoutBuilder.createDefaultWorkout(debugView,
						PreferenceManager.getDefaultSharedPreferences(ctx));
				w.setTts(mSpeech);
				mGpsTracker.setWorkout(w);
				Intent intent = new Intent(StartActivity.this,
						RunActivity.class);
				StartActivity.this.startActivityForResult(intent, 112);
				return;
			}
			updateView();
		}
	};

	private void updateView() {
		if (mGpsTracker != null)
			log("mGpsTracker.getState(): " + mGpsTracker.getState());
		else
			log("mGpsTracker = null");

		{
			int cnt0 = mGpsStatus.getSatellitesFixed();
			int cnt1 = mGpsStatus.getSatellitesAvailable();
			gpsInfoView1.setText("" + cnt0 + "(" + cnt1 + ")");
		}
		Location l = mGpsTracker.getLastKnownLocation();
		if (l != null) {
			gpsInfoView2.setText(" " + l.getAccuracy() + "m");
		}
		if (mGpsTracker.isLogging() == false) {
			startButton.setEnabled(true);
			startButton.setText("Start GPS");
			mGpsTracker.startLogging();
		} else if (mGpsStatus.isFixed() == false) {
			startButton.setEnabled(false);
			startButton.setText("Waiting for GPS");
		} else {
			startButton.setEnabled(true);
			startButton.setText("Start activity");
		}
	}

	public void onLocationChanged(Location arg0) {
		long t0 = arg0.getTime();
		long t1 = System.currentTimeMillis();
		long t2 = android.os.SystemClock.elapsedRealtime();
		long t3 = android.os.SystemClock.uptimeMillis();
		log("currentTimeMillis(): " + t1 + ", diff: "
				+ DateUtils.formatElapsedTime(Math.abs(t0 - t1) / 1000));
		log("elapsedRealtime: " + t2 + ", diff: "
				+ DateUtils.formatElapsedTime(Math.abs(t0 - t2) / 1000));
		log("uptimeMillis: " + t3 + ", diff: "
				+ DateUtils.formatElapsedTime(Math.abs(t0 - t3) / 1000));
		log("GPS: now: location changed: " + arg0.toString());
		updateView();
	}

	public void onProviderDisabled(String arg0) {
		log("GPS: provider disabled " + arg0);
		updateView();
	}

	public void onProviderEnabled(String arg0) {
		log("GPS: provider enabled " + arg0);
		updateView();
	}

	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		log("GPS: status changed to " + arg0 + " [" + arg1 + "]");
		updateView();
	}

	public void onGpsStatusChanged(int event, GpsStatus gs) {
		updateView();
	}

	private boolean mIsBound = false;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			mGpsTracker = ((GpsTracker.LocalBinder) service).getService();
			// Tell the user about this for our demo.
			StartActivity.this.onGpsTrackerBound();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			mGpsTracker = null;
		}
	};

	void bindGpsTracker() {
		// Establish a connection with the service. We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		getApplicationContext().bindService(new Intent(this, GpsTracker.class),
				mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void unbindGpsTracker() {
		if (mIsBound) {
			// Detach our existing connection.
			getApplicationContext().unbindService(mConnection);
			mIsBound = false;
		}
	}

	Button sayButton = null;
	EditText editText = null;
	TextToSpeech mSpeech = null;

	TextToSpeech.OnInitListener mTTSOnInitListener = new TextToSpeech.OnInitListener() {

		@Override
		public void onInit(int status) {
			if (status != TextToSpeech.SUCCESS) {
				log("tts fail: " + status);
				mSpeech = null;
			} else {
				log("tts ok");
				sayButton.setEnabled(true);
			}
		}
	};

	OnClickListener sayButtonClick = new OnClickListener() {
		public void onClick(View v) {
			String s = (((TextView) editText).getText()).toString();
			log("say: " + s);
			mSpeech.speak(s, TextToSpeech.QUEUE_ADD, null);

			long l = 1352188824; // 1352188824294;
			Date d = new Date(l);
			log("test: " + d.toString());

			Object[] arguments = { Integer.valueOf(7),
					DateUtils.formatElapsedTime(240), new Date(240 * 1000),
					"a disturbance in the Force" };

			String result = MessageFormat
					.format("At {1} on {2,date}, there was {3} on planet {0,number,integer}.",
							arguments);

			log("res: " + result);
		}
	};

	OnClickListener authButtonClick = new OnClickListener() {
		public void onClick(View v) {
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		log("onActivityResult(" + requestCode + ", " + resultCode + ", "
				+ (data == null ? "null" : data.toString()));
		if (data != null) {
			if (data.getStringExtra("url") != null)
				log("data.getStringExtra(\"url\") => "
						+ data.getStringExtra("url"));
			if (data.getStringExtra("ex") != null)
				log("data.getStringExtra(\"ex\") => "
						+ data.getStringExtra("ex"));
			if (data.getStringExtra("obj") != null)
				log("data.getStringExtra(\"obj\") => "
						+ data.getStringExtra("obj"));
		}
		if (requestCode == 112) {
			onGpsTrackerBound();
		} else {
			updateView();
		}
	}

	@Override
	public void onTick() {
		updateView();
	}
}
