package org.runnerup.workout;

import java.util.HashSet;

import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.Constants.DB;

import android.content.ContentValues;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;

/**
 * This class is the top level object for a workout, it is being called by
 * RunActivity, and by the Workout components
 */
public class Workout implements WorkoutComponent {

	long lap = 0;
	int currentActivityNo = -1;
	Activity currentActivity = null;
	Activity activities[] = new Activity[0];
	HashSet<Feedback> pendingFeedback = new HashSet<Feedback>();

	GpsTracker gpsTracker = null;
	TextToSpeech tts = null;

	public Workout() {
	}

	public void setGpsTracker(GpsTracker gpsTracker) {
		this.gpsTracker = gpsTracker;
	}

	public void onInit(Workout w) {
		assert (w == this);
		for (Activity a : activities) {
			a.onInit(this);
		}
	}

	public void onEnd(Workout w) {
		assert (w == this);
		for (Activity a : activities) {
			a.onEnd(this);
		}
	}

	public void onStart(Scope s, Workout w) {
		assert (w == this);

		initFeedback();

		currentActivityNo = 0;
		if (activities.length > 0) {
			currentActivity = activities[currentActivityNo];
		}

		if (currentActivity != null) {
			currentActivity.onStart(Scope.WORKOUT, this);
			currentActivity.onStart(Scope.ACTIVITY, this);
			currentActivity.onStart(Scope.LAP, this);
		}

		emitFeedback();
	}

	public void onTick() {
		initFeedback();

		while (currentActivity != null) {
			boolean finished = currentActivity.onTick(this);
			if (finished == false)
				break;

			currentActivity.onComplete(Scope.LAP, this);
			currentActivity.onComplete(Scope.ACTIVITY, this);
			currentActivityNo++;
			if (currentActivityNo < activities.length) {
				currentActivity = activities[currentActivityNo];
				currentActivity.onStart(Scope.ACTIVITY, this);
				currentActivity.onStart(Scope.LAP, this);
			} else {
				currentActivity.onComplete(Scope.WORKOUT, this);
				currentActivity = null;
				gpsTracker.stop();
			}
		}
		emitFeedback();
	}

	public void onPause(Workout w) {

		gpsTracker.stop();

		initFeedback();
		if (currentActivity != null) {
			currentActivity.onPause(this);
		}
		emitFeedback();
	}

	public boolean isPaused() {
		return gpsTracker.isPaused();
	}

	public void onNewLap() {
		initFeedback();
		if (currentActivity != null) {
			currentActivity.onComplete(Scope.LAP, this);
			currentActivity.onStart(Scope.LAP, this);
		}
		emitFeedback();
	}

	public void onStop(Workout w) {

		gpsTracker.stop();

		initFeedback();
		if (currentActivity != null) {
			currentActivity.onStop(this);
		}
		emitFeedback();
	}

	public void onResume(Workout w) {
		gpsTracker.resume();

		initFeedback();
		if (currentActivity != null) {
			currentActivity.onResume(this);
		}
		emitFeedback();
	}

	public void onComplete(Scope s, Workout w) {
		if (currentActivity != null) {
			currentActivity.onComplete(Scope.LAP, this);
			currentActivity.onComplete(Scope.ACTIVITY, this);
			currentActivity.onComplete(Scope.WORKOUT, this);
		}
		currentActivity = null;
		currentActivityNo = -1;
	}

	public void onSave() {
		gpsTracker.completeActivity(true);
	}

	public void onDiscard() {
		gpsTracker.completeActivity(false);
	}

	public double get(Scope scope, Dimension d) {
		if (d == Dimension.DISTANCE)
			return getDistance(scope);
		else if (d == Dimension.TIME)
			return getTime(scope);
		else if (d == Dimension.SPEED)
			return getSpeed(scope);
		assert (false);
		return 0;
	}

	public long getDistance(Scope scope) {
		if (scope == Scope.WORKOUT)
			return (long) gpsTracker.getDistance();
		else if (currentActivity != null) {
			return currentActivity.getDistance(this, scope);
		}
		assert (false);
		return 0;
	}

	public long getTime(Scope scope) {
		if (scope == Scope.WORKOUT)
			return (long) gpsTracker.getTime();
		else if (currentActivity != null) {
			return currentActivity.getTime(this, scope);
		}
		assert (false);
		return 0;
	}

	public double getSpeed(Scope scope) {
		if (scope == Scope.WORKOUT) {
			long d = getDistance(scope);
			long t = getTime(scope);
			if (t == 0)
				return 0;
			return ((double) d) / ((double) t);
		} else if (currentActivity != null) {
			return currentActivity.getSpeed(this, scope);
		}
		assert (false);
		return 0;
	}

	private void initFeedback() {
		pendingFeedback.clear();
	}

	public void addFeedback(Feedback f) {
		pendingFeedback.add(f);
	}

	private void emitFeedback() {
		for (Feedback f : pendingFeedback) {
			f.emit(this, gpsTracker.getApplicationContext());
		}
		pendingFeedback.clear();
	}

	void newLap(ContentValues tmp) {
		tmp.put(DB.LAP.LAP, lap);
		gpsTracker.newLap(tmp);
	}

	void saveLap(ContentValues tmp, boolean next) {
		gpsTracker.saveLap(tmp);
		if (next) {
			lap++;
		}
	}

	public void setTts(TextToSpeech tts) {
		this.tts = tts;
	}

	public TextToSpeech getTts() {
		// TODO Auto-generated method stub
		return tts;
	}

	TextView tv = null;

	public void setLog(TextView tv) {
		this.tv = tv;
	}

	public void log(String s) {
		if (tv != null && s != null) {
			tv.setText(s + "\n" + tv.getText());
		}
	}

};
