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

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.runnerup.R;
import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.TickListener;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Speed;
import org.runnerup.workout.Workout;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class RunActivity extends Activity implements TickListener {
	Workout workout = null;
	GpsTracker mGpsTracker = null;
	Handler handler = new Handler();
	long activityId = 0;

	Button pauseButton = null;
	Button stopButton = null;
	Button newLapButton = null;
	TextView activityTime = null;
	TextView activityDistance = null;
	TextView activityPace = null;
	TextView lapTime = null;
	TextView lapDistance = null;
	TextView lapPace = null;
	TextView debugView = null;
	TextView countdownView = null;
	
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.run);
		bindGpsTracker();
		mSpeech = new TextToSpeech(getApplicationContext(), mTTSOnInitListener);

		stopButton = (Button) findViewById(R.id.stopButton);
		stopButton.setOnClickListener(stopButtonClick);
		pauseButton = (Button) findViewById(R.id.pauseButton);
		pauseButton.setOnClickListener(pauseButtonClick);
		newLapButton = (Button) findViewById(R.id.newLapButton);
		newLapButton.setOnClickListener(newLapButtonClick);
		activityTime = (TextView) findViewById(R.id.activityTime);
		activityDistance = (TextView) findViewById(R.id.activityDistance);
		activityPace = (TextView) findViewById(R.id.activityPace);
		lapTime = (TextView) findViewById(R.id.lapTime);
		lapDistance = (TextView) findViewById(R.id.lapDistance);
		lapPace = (TextView) findViewById(R.id.lapPace);
		debugView = (TextView) findViewById(R.id.textView2);
		countdownView = (TextView) findViewById(R.id.countdownTextView);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindGpsTracker();
		if (mSpeech != null) {
			mSpeech.shutdown();
			mSpeech = null;
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
		activityId = mGpsTracker.createActivity();
		mGpsTracker.setForeground(RunActivity.class);
		mGpsTracker.start();
		workout = mGpsTracker.getWorkout();
		HashMap<String, Object> bindValues = new HashMap<String, Object>();
		bindValues.put(Workout.KEY_TTS, mSpeech);
		bindValues.put(Workout.KEY_COUNTER_VIEW, countdownView);
		workout.onInit(workout, bindValues);
		workout.setLog(debugView);
		workout.onStart(Scope.WORKOUT, this.workout);
		startTimer();
	}

	Timer timer = null;

	void startTimer() {
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				RunActivity.this.handler.post(new Runnable() {
					public void run() {
						RunActivity.this.onTick();
					}
				});
			}
		}, 0, 500);
	}

	Location l = null;

	public void onTick() {
		if (workout != null) {
			workout.onTick();
			updateView();

			if (mGpsTracker != null) {
				Location l2 = mGpsTracker.getLastKnownLocation();
				if (!l2.equals(l)) {
					l = l2;
				}
			}
		}
	}

	void stopTimer() {
		timer.cancel();
		timer.purge();
		timer = null;
	}

	OnClickListener stopButtonClick = new OnClickListener() {
		public void onClick(View v) {
			log("stopButtonClick");
			workout.onStop(workout);
			stopTimer();
			Intent intent = new Intent(RunActivity.this, DetailActivity.class);
			/**
			 * The same activity is used to show details and to save activity
			 * they show almost the same information
			 */
			intent.putExtra("mode", "save");
			intent.putExtra("ID", activityId);
			RunActivity.this.startActivityForResult(intent, 0);
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			/**
			 * they saved
			 */
			workout.onComplete(Scope.WORKOUT, workout);
			workout.onSave();
			mGpsTracker = null;
			finish();
			return;
		} else if (resultCode == Activity.RESULT_CANCELED) {
			/**
			 * they discarded
			 */
			workout.onComplete(Scope.WORKOUT, workout);
			workout.onDiscard();
			mGpsTracker = null;
			finish();
			return;
		} else if (resultCode == Activity.RESULT_FIRST_USER) {
			workout.onResume(workout);
			startTimer();
		} else {
			assert (false);
		}
	}

	OnClickListener pauseButtonClick = new OnClickListener() {
		public void onClick(View v) {
			log("pauseButtonClick");
			if (workout.isPaused()) {
				workout.onResume(workout);
				pauseButton.setText("Pause");
			} else {
				workout.onPause(workout);
				pauseButton.setText("Resume");
			}
		}
	};

	OnClickListener newLapButtonClick = new OnClickListener() {
		public void onClick(View v) {
			log("newLapButtonClick");
			workout.onNewLap();
		}
	};

	private void updateView() {
		double ad = workout.getDistance(Scope.WORKOUT);
		long at = workout.getTime(Scope.WORKOUT);
		double as = workout.getSpeed(Scope.WORKOUT);
		long ap = (long) Speed.convert(as, Speed.PACE_SPK); // seconds per
															// kilometer...suitable
															// for
															// formatElapsedTime
		activityTime.setText(DateUtils.formatElapsedTime(at));
		activityDistance.setText("" + ad);
		activityPace.setText(DateUtils.formatElapsedTime(ap));
		double ld = workout.getDistance(Scope.LAP);
		long lt = workout.getTime(Scope.LAP);
		double ls = workout.getSpeed(Scope.LAP);
		long lp = (long) Speed.convert(ls, Speed.PACE_SPK);
		lapTime.setText(DateUtils.formatElapsedTime(lt));
		lapDistance.setText("" + ld);
		lapPace.setText(DateUtils.formatElapsedTime(lp));
	}

	private boolean mIsBound = false;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			if (mGpsTracker == null) {
				mGpsTracker = ((GpsTracker.LocalBinder) service).getService();
				// Tell the user about this for our demo.
				RunActivity.this.onGpsTrackerBound();
			}
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

	TextToSpeech mSpeech = null;
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
}
