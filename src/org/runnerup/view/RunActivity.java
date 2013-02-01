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

import java.util.ArrayList;
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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
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
	TextView countdownView = null;
	ListView workoutList = null;
	org.runnerup.workout.Activity currentActivity = null;
	
	class WorkoutRow { org.runnerup.workout.Activity activity = null; ContentValues lap = null;};
	ArrayList<WorkoutRow> workoutRows = new ArrayList<WorkoutRow>();
	ArrayList<BaseAdapter> adapters = new ArrayList<BaseAdapter>(2);

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
		countdownView = (TextView) findViewById(R.id.countdownTextView);
		workoutList = (ListView) findViewById(R.id.workoutList);
		WorkoutAdapter adapter = new WorkoutAdapter(workoutRows);
		workoutList.setAdapter(adapter);
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

	void onGpsTrackerBound() {
		activityId = mGpsTracker.createActivity();
		mGpsTracker.setForeground(RunActivity.class);
		mGpsTracker.start();
		workout = mGpsTracker.getWorkout();
		HashMap<String, Object> bindValues = new HashMap<String, Object>();
		bindValues.put(Workout.KEY_TTS, mSpeech);
		bindValues.put(Workout.KEY_COUNTER_VIEW, countdownView);
		workout.onInit(workout, bindValues);
		workout.onStart(Scope.WORKOUT, this.workout);
		startTimer();

		populateWorkoutList();
	}

	private void populateWorkoutList() {
		for (int i = 0; i < workout.getActivityCount(); i++) {
			WorkoutRow row = new WorkoutRow();
			row.activity = workout.getActivity(i);
			row.lap = null;
			workoutRows.add(row);
		}
		for (BaseAdapter a : adapters) {
			a.notifyDataSetChanged();
		}
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
		
		if (currentActivity != workout.getCurrentActivity()) {
			((WorkoutAdapter)workoutList.getAdapter()).notifyDataSetChanged();
			currentActivity = workout.getCurrentActivity();
			workoutList.setSelection(getPosition(workoutRows, currentActivity));
		}
	}

	private int getPosition(ArrayList<WorkoutRow> workoutRows,
			org.runnerup.workout.Activity currentActivity) {
		for (int i = 0; i< workoutRows.size(); i++) {
			if (workoutRows.get(i).activity == currentActivity)
				return i;
		}
		return 0;
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
				mSpeech = null;
			} else {
			}
		}
	};

	class WorkoutAdapter extends BaseAdapter {

		ArrayList<WorkoutRow> rows = null;

		WorkoutAdapter(ArrayList<WorkoutRow> workoutRows) {
			this.rows = workoutRows;
		}
		
		@Override
		public int getCount() {
			return rows.size();
		}

		@Override
		public Object getItem(int position) {
			return rows.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			WorkoutRow tmp = rows.get(position);
			if (tmp.activity != null)
			{
				return getWorkoutRow(tmp.activity, convertView, parent);
			}
			else
			{	
				return getLapRow(tmp.lap, convertView, parent);
			}
		}

		private View getWorkoutRow(org.runnerup.workout.Activity activity, View convertView, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(RunActivity.this);
			View view = inflater.inflate(R.layout.workout_row, parent, false);
			TextView intensity = (TextView) view.findViewById(R.id.step_intensity);
			TextView durationType = (TextView) view.findViewById(R.id.step_duration_type);
			TextView durationValue = (TextView) view.findViewById(R.id.step_duration_value);
			TextView targetPace = (TextView) view.findViewById(R.id.step_pace);
			intensity.setText(getResources().getText(activity.getIntensity().getTextId()));
			if (activity.getDurationType() != null) {
				durationType.setText(getResources().getText(activity.getDurationType().getTextId()));
				durationValue.setText("" + activity.getDurationValue());
			} else {
				durationType.setText("");
				durationValue.setText("");
			}
			if (workout.getCurrentActivity() == activity) {
				view.setBackgroundResource(android.R.color.background_light);
			} else {
				view.setBackgroundResource(android.R.color.black);
			}
			targetPace.setText("");
			return view;
		}

		private View getLapRow(ContentValues tmp, View convertView, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(RunActivity.this);
			View view = inflater.inflate(R.layout.laplist_row, parent, false);
			return view;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}
	};
}
