/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
package org.runnerup.workout;

import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class TargetTrigger extends Trigger {

	boolean inited = false;
	boolean paused = false;
	
	int graceCount    = 30; //
	int initialGrace  = 20;
	int minGraceCount = 30; //
	
	Scope scope = Scope.STEP;
	Dimension dimension = Dimension.PACE;

	Range range = null;
	
	int cntMeasures = 0;
	double measure[] = null;
	int skip_values = 1;
	double sort_measure[] = null;
	double lastTimestamp = 0;
	MovingAverage movingAverage = null;

	boolean useGpsForPaceTrigger = false;
	
	/**
	 * cache computing of median
	 */
	double lastVal = 0;
	int lastValCnt = 0;

	public TargetTrigger(Dimension dim, int movingAverageSeconds, int graceSeconds) {
		dimension = dim;
		measure = new double[movingAverageSeconds];
		sort_measure = new double[movingAverageSeconds];

		movingAverage = new MovingAverage(movingAverageSeconds);
		if (dimension == Dimension.HRZ)
			dimension = Dimension.HR;

		minGraceCount = graceSeconds;
		skip_values = (5 * movingAverageSeconds) / 100; // ignore 5% lowest and 5% higest values

		reset();
	}

	@Override
	public boolean onTick(Workout w) {
		if (paused) {
			return false;
		}

		if (!w.isEnabled(dimension, Scope.STEP)) {
			inited = false;
			return false;
		}
		
		double time_now = w.get(Scope.STEP, Dimension.TIME);

		if (time_now < lastTimestamp) {
			System.out.println("time_now < lastTimestamp");
			reset();
			return false;
		}

		if (inited == false) {
			System.out.println("inited == false");
			reset();
			lastTimestamp = time_now;
			movingAverage.init(w, time_now);
			inited = true;
			return false;
		}
		
		if ((time_now - lastTimestamp) < 1.0) {
			return false;
		}
		
		final int elapsed_seconds = (int)(time_now - lastTimestamp);
		lastTimestamp = time_now;

		try {
			double val_now = getMeasurement(w, time_now);
			for (int i = 0; i < elapsed_seconds; i++) {
				addObservation(val_now);
			}
			// System.err.println("val_now: " + val_now + " elapsed: " + elapsed_seconds);
		
			if (graceCount > 0) { // only emit coaching ever so often
				// System.err.println("graceCount: " + graceCount);
				graceCount -= elapsed_seconds;
			} else {
				double avg = getValue();
				double cmp = range.compare(avg);
				// System.err.println(" => avg: " + avg + " => cmp: " + cmp + "(" + range.minValue + ", " + range.maxValue + ")");
				if (cmp == 0) {
					return false;
				}
				fire(w);
				graceCount = minGraceCount;
			}
		} catch (ArithmeticException ex) {
			return false;
		}
		return false;
	}

	private void addObservation (double val_now) {
		int pos = cntMeasures % measure.length;
		measure[pos] = val_now;
		cntMeasures ++;
	}

	public double getValue() {
		if (cntMeasures == lastValCnt)
			return lastVal;
		
		int len = cntMeasures >= measure.length ? measure.length : cntMeasures;
		int zeros = measure.length - len;
		System.arraycopy(measure,  0, sort_measure, 0, len);
		if (zeros > 0) {
			// fill end of array with values that will sort to the left of array
			double minVal = sort_measure[0];
			for (int i = 1; i < len; i++)
				minVal = Math.min(minVal,  sort_measure[i]);
			for (int i = 0; i < zeros; i++)
				sort_measure[len+i] = minVal - 1;
		}
		java.util.Arrays.sort(sort_measure);
		double cnt = 0;
		double val = 0;
		for (int i = zeros + skip_values; i < sort_measure.length - skip_values; i++) {
			val += sort_measure[i];
			cnt++;
		}
		if (cnt > 0) {
			lastVal = val / cnt; 
		} else {
			int mid = zeros + (sort_measure.length - zeros) / 2;
			lastVal = sort_measure[mid];
		}
		lastValCnt = cntMeasures;
		return lastVal;
	}
	
	private void reset() {
		for (int i = 0; i < measure.length; i++) {
			measure[i] = 0;
		}
		inited = false;
		cntMeasures = 0;
		graceCount = initialGrace;
		lastTimestamp = 0;
		
		lastVal = 0;
		lastValCnt = 1;
		movingAverage.reset();
	}

	private double getMeasurement(Workout w, double time_now) {
		switch(dimension) {
		case PACE:
		case SPEED:
			if (useGpsForPaceTrigger && w.isEnabled(Dimension.SPEED, Scope.CURRENT)) {
				double val = w.getSpeed(Scope.CURRENT);
				if (dimension == Dimension.PACE && val != 0)
					return 1/val;
				else if (dimension == Dimension.SPEED)
					return val;
			}
			movingAverage.addMeasurement(w, time_now);
			return movingAverage.getValue();
		case DISTANCE:
			break;
		case TIME:
			break;
		case HR:
			break;
		case HRZ:
			break;
		}

		return w.get(Scope.CURRENT, dimension);
	}

	@Override
	public void onRepeat(int current, int limit) {
	}

	@Override
	public void onStart(Scope what, Workout s) {
		if (this.scope == what) {
			reset();
			for (Feedback f : triggerAction) {
				f.onStart(s);
			}
		}
	}

	@Override
	public void onPause(Workout s) {
		paused = true;
	}

	@Override
	public void onStop(Workout s) {
		paused = true;
	}

	@Override
	public void onResume(Workout s) {
		paused = false;
		reset();
	}

	@Override
	public void onComplete(Scope what, Workout s) {
	}

	class MovingAverage {
		int cnt;
		double measure_time[] = null;
		double measure_value[] = null;
		MovingAverage(int movingAverageSeconds) {
			measure_time = new double[movingAverageSeconds];
			measure_value = new double[movingAverageSeconds];
		}
		public void reset() {
			cnt = 0;
		}
		public void init(Workout w, double time_now) {
			reset();
			addMeasurement(w, time_now);
		}
		public void addMeasurement(Workout w, double time_now) {
			int pos = cnt % measure_time.length;
			switch(dimension) {
			case PACE:
			case SPEED:
				double distance_now = w.get(scope,  Dimension.DISTANCE);
				measure_time[pos] = time_now;
				measure_value[pos] = distance_now;
				cnt++;
				break;
			case DISTANCE:
				break;
			case TIME:
				break;
			case HR:
				break;
			case HRZ:
				break;
			}
		}
		public double getValue() {
			assert(cnt > 1);
			int lo = 0;
			int hi = cnt - 1;
			if (cnt >= measure_time.length) {
				lo = (cnt - 2) % measure_time.length;
				hi = (cnt - 1) % measure_time.length;
			}
			double delta_val = measure_value[hi] - measure_value[lo];
			double delta_time = measure_time[hi] - measure_time[lo];

			switch(dimension) {
			case PACE:
				if (delta_val == 0)
					return 0;
				return delta_time / delta_val;
			case SPEED:
				if (delta_time == 0)
					return 0;
				return delta_val / delta_time;
			case DISTANCE:
				break;
			case TIME:
				break;
			case HR:
				break;
			case HRZ:
				break;
			}
			return 0;
		}
	};
}
