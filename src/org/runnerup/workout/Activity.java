package org.runnerup.workout;

import org.runnerup.util.Constants.DB;

import android.content.ContentValues;

public class Activity implements TickComponent {

	String name = null;

	/**
	 * Intensity
	 */
	int intensity = INTENSITY_ACTIVE;
	public static final int INTENSITY_ACTIVE = 0;
	public static final int INTENSITY_RESTING = 1;

	/**
	 * Duration
	 */
	Dimension durationType = null;
	double durationValue = 0;

	/**
	 * Target
	 */
	int targetType = TARGET_TYPE_NONE;
	double targetValue = 0;
	public static final int TARGET_TYPE_NONE = 0;
	public static final int TARGET_TYPE_SPEED = 1; // m/s

	/**
	 * Autolap (m)
	 */
	long autolap = 0;

	/**
	 * Triggers
	 */
	Trigger triggers[] = new Trigger[0];

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the intensity
	 */
	public int getIntensity() {
		return intensity;
	}

	/**
	 * @param intensity
	 *            the intensity to set
	 */
	public void setIntensity(int intensity) {
		this.intensity = intensity;
	}

	/**
	 * @return the durationType
	 */
	public Dimension getDurationType() {
		return durationType;
	}

	/**
	 * @param durationType
	 *            the durationType to set
	 */
	public void setDurationType(Dimension durationType) {
		this.durationType = durationType;
	}

	/**
	 * @return the durationValue
	 */
	public double getDurationValue() {
		return durationValue;
	}

	/**
	 * @param durationValue
	 *            the durationValue to set
	 */
	public void setDurationValue(double durationValue) {
		this.durationValue = durationValue;
	}

	/**
	 * @return the targetType
	 */
	public int getTargetType() {
		return targetType;
	}

	/**
	 * @param targetType
	 *            the targetType to set
	 */
	public void setTargetType(int targetType) {
		this.targetType = targetType;
	}

	/**
	 * @return the targetValue
	 */
	public double getTargetValue() {
		return targetValue;
	}

	/**
	 * @param targetValue
	 *            the targetValue to set
	 */
	public void setTargetValue(double targetValue) {
		this.targetValue = targetValue;
	}

	/**
	 * @return the autolap
	 */
	public long getAutolap() {
		return autolap;
	}

	/**
	 * @param autolap
	 *            the autolap to set
	 */
	public void setAutolap(long autolap) {
		this.autolap = autolap;
	}

	@Override
	public void onInit(Workout s) {
		for (Trigger t : triggers) {
			t.onInit(s);
		}
	}

	@Override
	public void onEnd(Workout s) {
		for (Trigger t : triggers) {
			t.onEnd(s);
		}
	}

	long activityStartTime = 0;
	long activityStartDistance = 0;
	long lapStartTime = 0;
	long lapStartDistance = 0;

	@Override
	public void onStart(Scope what, Workout s) {
		long time = s.getTime(Scope.WORKOUT);
		long dist = s.getDistance(Scope.WORKOUT);
		if (what == Scope.ACTIVITY) {
			activityStartTime = time;
			activityStartDistance = dist;
		} else if (what == Scope.LAP) {
			lapStartTime = time;
			lapStartDistance = dist;
			ContentValues tmp = new ContentValues();
			tmp.put(DB.LAP.TYPE, intensity);
			if (durationType != null) {
				switch (durationType) {
				case TIME:
					tmp.put(DB.LAP.PLANNED_TIME, (long) durationValue);
					break;
				case DISTANCE:
					tmp.put(DB.LAP.PLANNED_DISTANCE, (long) durationValue);
					break;
				}
			}
			switch (targetType) {
			case TARGET_TYPE_SPEED:
				tmp.put(DB.LAP.PLANNED_PACE, targetValue);
			}
			s.newLap(tmp);
		}

		for (Trigger t : triggers) {
			t.onStart(what, s);
		}
	}

	@Override
	public void onStop(Workout s) {
		for (Trigger t : triggers) {
			t.onStop(s);
		}

		/**
		 * Save current lap so that it shows in DetailActivity
		 */
		long distance = s.getDistance(Scope.LAP);
		long time = s.getTime(Scope.LAP);
		if (distance > 0 || time > 0) {
			ContentValues tmp = new ContentValues();
			tmp.put(DB.LAP.DISTANCE, distance);
			tmp.put(DB.LAP.TIME, time);
			s.saveLap(tmp, /** next lap */
			false);
		}
	}

	@Override
	public void onPause(Workout s) {
		for (Trigger t : triggers) {
			t.onPause(s);
		}
	}

	/**
	 * 
	 * @return true if finished
	 */
	public boolean onTick(Workout s) {
		if (checkFinished(s)) {
			return true;
		}

		for (Trigger t : triggers) {
			t.onTick(s);
		}

		if (this.autolap > 0 && s.getDistance(Scope.LAP) >= this.autolap) {
			s.onNewLap();
		}
		return false;
	}

	private boolean checkFinished(Workout s) {
		if (durationType == null)
			return false;

		return s.get(Scope.ACTIVITY, durationType) >= this.durationValue;
	}

	@Override
	public void onResume(Workout s) {
		for (Trigger t : triggers) {
			t.onResume(s);
		}
	}

	@Override
	public void onComplete(Scope scope, Workout s) {
		if (scope == Scope.LAP) {
			long distance = s.getDistance(scope);
			long time = s.getTime(scope);
			if (distance > 0 || time > 0) {
				ContentValues tmp = new ContentValues();
				tmp.put(DB.LAP.DISTANCE, distance);
				tmp.put(DB.LAP.TIME, time);
				s.saveLap(tmp, /** next lap */
				true);
			}
		}
		for (Trigger t : triggers) {
			t.onComplete(scope, s);
		}
	}

	public long getDistance(Workout w, Scope s) {
		long d = w.getDistance(Scope.WORKOUT);
		if (s == Scope.ACTIVITY) {
			return d - activityStartDistance;
		} else if (s == Scope.LAP) {
			return d - lapStartDistance;
		}
		assert (false);
		return 0;
	}

	public long getTime(Workout w, Scope s) {
		long t = w.getTime(Scope.WORKOUT);
		if (s == Scope.ACTIVITY) {
			return t - activityStartTime;
		} else if (s == Scope.LAP) {
			return t - lapStartTime;
		}
		assert (false);
		return 0;
	}

	public double getSpeed(Workout w, Scope s) {
		long t = getTime(w, s);
		long d = getDistance(w, s);
		if (t != 0) {
			return ((double) d) / ((double) t);
		}
		return 0;
	}
};
