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
	
	double measure_time[] = null;
	double measure_distance[] = null;

	/**
	 * cache computing of median
	 */
	double lastVal = 0;
	int lastValCnt = 0;

	public TargetTrigger(int movingAverageSeconds, int graceSeconds) {
		measure = new double[movingAverageSeconds];
		sort_measure = new double[movingAverageSeconds];

		if (dimension == Dimension.SPEED || dimension == Dimension.PACE) {
			measure_time = new double[movingAverageSeconds];
			measure_distance = new double[movingAverageSeconds];
		}
		minGraceCount = graceSeconds;
		skip_values = (5 * movingAverageSeconds) / 100; // ignore 5% lowest and 5% higest values

		reset();
	}

	@Override
	public boolean onTick(Workout w) {
		if (paused)
		{
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
			lastTimestamp = time_now;
			initMeasurement(w, time_now);
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
//			System.err.println("val_now: " + val_now + " elapsed: " + elapsed_seconds);
		
			if (graceCount > 0) { // only emit coaching ever so often
//				System.err.println("graceCount: " + graceCount);
				graceCount -= elapsed_seconds;
			} else {
				double avg = getValue();
				double cmp = range.compare(avg);
//				System.err.println(" => avg: " + avg + " => cmp: " + cmp);
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
		int pos = cntMeasures % measure_time.length;
		measure[pos] = val_now;
		cntMeasures ++;
	}

	public double getValue() {
		if (cntMeasures == lastValCnt)
			return lastVal;
		
		System.arraycopy(measure,  0, sort_measure, 0, measure.length);
		java.util.Arrays.sort(sort_measure);
		double cnt = 0;
		double val = 0;
		for (int i = skip_values; i < sort_measure.length - skip_values; i++) {
			val += sort_measure[i];
			cnt++;
		}
		lastVal = val / cnt; 
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
		lastValCnt = 0;
	}

	private void initMeasurement(Workout w, double time_now) {
		switch(dimension) {
		case PACE:
		case SPEED:
			double distance_now = w.get(scope,  Dimension.DISTANCE);
			if (lastTimestamp == 0) {
				measure_time[0] = time_now;
				measure_distance[0] = distance_now;
			}
			break;
		case DISTANCE:
			break;
		case TIME:
			break;
		}
	}

	private double getMeasurement(Workout w, double time_now) {
		switch(dimension) {
		case PACE:
		case SPEED:
			double distance_now = w.get(scope,  Dimension.DISTANCE);

			int oldpos = 0;
			int newpos = (cntMeasures + 1) % measure_time.length;
			if (cntMeasures >= measure_time.length) {
				oldpos = newpos;
			}
			double delta_distance = distance_now - measure_distance[oldpos];
			double delta_time = time_now - measure_time[oldpos];

			measure_time[newpos] = time_now;
			measure_distance[newpos] = distance_now;
			
			if (dimension == Dimension.PACE) {
				return delta_time / delta_distance;
			} else {
				assert(dimension == Dimension.SPEED);
				return delta_distance / delta_time;
			}
		case DISTANCE:
			break;
		case TIME:
			break;
		}
		
		return 0;
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
}
