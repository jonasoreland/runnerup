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
package org.runnerup.view;

import org.runnerup.R;
import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.TickListener;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TabHost.TabSpec;

public class StartActivity extends Activity implements TickListener {

	GpsTracker mGpsTracker = null;
	org.runnerup.gpstracker.GpsStatus mGpsStatus = null;
	TextToSpeech mSpeech = null;

	Button startButton = null;
	TextView gpsInfoView1 = null;
	TextView gpsInfoView2 = null;
	TextView debugView = null;

	TitleSpinner simpleType = null;
	EditText simpleDuration = null;
	
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.layout.settings, false);
		
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

		mGpsStatus = new org.runnerup.gpstracker.GpsStatus(StartActivity.this);
		
		TabHost th = (TabHost)findViewById(R.id.tabhostStart);
		th.setup();
		TabSpec tabSpec = th.newTabSpec("basic");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Basic"));
		tabSpec.setContent(R.id.tabBasic);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("interval");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Interval"));
		tabSpec.setContent(R.id.tabInterval);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("advanced");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Advanced"));
		tabSpec.setContent(R.id.tabManual);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("manual");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Manual"));
		tabSpec.setContent(R.id.tabManual);
		th.addTab(tabSpec);

		CheckBox goal = (CheckBox) findViewById(R.id.tabBasicGoal);
		goal.setOnCheckedChangeListener(simpleGoalOnCheckClick);
		simpleType = (TitleSpinner)findViewById(R.id.simpleType);
		simpleDuration = (EditText) findViewById(R.id.simpleDuration);
		simpleGoalOnCheckClick.onCheckedChanged(goal, goal.isChecked());
	}

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
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx); 
		if (pref.getBoolean("pref_startgps", false) == true) {
			log("autostart gps");
			mGpsStatus.start(this);
			mGpsTracker.startLogging();
		} else {
			log("don't autostart gps");
		}
		updateView();
	}

	OnClickListener startButtonClick = new OnClickListener() {
		public void onClick(View v) {
			log ("here mGpsTracker.isLogging(): " + mGpsTracker.isLogging());
			if (mGpsStatus.isEnabled() == false) {
				log("run intent");
				startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
			} else if (mGpsTracker.isLogging() == false) {
				log("startLoging");
				mGpsTracker.startLogging();
				mGpsStatus.start(StartActivity.this);
			} else if (mGpsStatus.isFixed()) {
				mGpsStatus.stop(StartActivity.this);
				Context ctx = getApplicationContext();
				Workout w = WorkoutBuilder.createDefaultWorkout(debugView,
						PreferenceManager.getDefaultSharedPreferences(ctx));
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
		{
			int cnt0 = mGpsStatus.getSatellitesFixed();
			int cnt1 = mGpsStatus.getSatellitesAvailable();
			gpsInfoView1.setText("" + cnt0 + "(" + cnt1 + ")");
		}

		if (mGpsTracker != null) {
			Location l = mGpsTracker.getLastKnownLocation();

			if (l != null) {
				gpsInfoView2.setText(" " + l.getAccuracy() + "m");
			}
		}
		if (mGpsStatus.isEnabled() == false) {
			startButton.setEnabled(true);
			startButton.setText("Enable GPS");
		} else if (mGpsStatus.isLogging() == false) {
			startButton.setEnabled(true);
			startButton.setText("Start GPS");
		} else if (mGpsStatus.isFixed() == false) {
			startButton.setEnabled(false);
			startButton.setText("Waiting for GPS");
		} else {
			startButton.setEnabled(true);
			startButton.setText("Start activity");
		}
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

	TextToSpeech.OnInitListener mTTSOnInitListener = new TextToSpeech.OnInitListener() {

		@Override
		public void onInit(int status) {
			if (status != TextToSpeech.SUCCESS) {
				log("tts fail: " + status);
				mSpeech = null;
			} else {
				log("tts ok");
			}
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

	OnCheckedChangeListener simpleGoalOnCheckClick = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,	boolean isChecked) {
			simpleType.setEnabled(isChecked);
			simpleDuration.setEnabled(isChecked);
		}
		
	};

}
