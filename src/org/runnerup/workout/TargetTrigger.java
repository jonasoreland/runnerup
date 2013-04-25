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

	boolean paused = false;
	
	int graceCount    = 25; //
	int initialGrace  = 10;
	int minGraceCount = 25; //
	
	Scope scope = Scope.STEP;
	Dimension dimension = Dimension.PACE;

	Range range = null;
	
	int cntMeasures = 0;
	double measure_time[] = null;
	double measure_distance[] = null;
	
	public TargetTrigger(int movingAverageSeconds, int graceSeconds) {
		measure_time = new double[movingAverageSeconds];
		measure_distance = new double[movingAverageSeconds];
		minGraceCount = graceSeconds;
		reset();
	}

	private double getLastTimestamp() {
		int pos = (cntMeasures - 1) % measure_time.length;
		return measure_time[pos];
	}

	private double getOldestTimestamp() {
		int pos = 0;
		if (cntMeasures >= measure_time.length) {
			pos = (cntMeasures - measure_time.length) % measure_time.length;
		}

		return measure_time[pos];
	}

	private double getLastDistance() {
		int pos = (cntMeasures - 1) % measure_time.length;
		return measure_distance[pos];
	}

	private double getOldestDistance() {
		int pos = 0;
		if (cntMeasures >= measure_time.length) {
			pos = (cntMeasures - measure_time.length) % measure_time.length;
		}

		return measure_distance[pos];
	}
	
	@Override
	public boolean onTick(Workout w) {
		if (paused)
		{
			return false;
		}

		double time_now = w.get(Scope.WORKOUT, Dimension.TIME);
		double distance_now = w.get(Scope.WORKOUT, Dimension.DISTANCE);
		double lastTimestamp = getLastTimestamp();
		
		if (time_now < lastTimestamp) {
			reset();
			return false;
		}
		
		if ((time_now - lastTimestamp) < 1.0) {
			return false;
		}

		double elapsed_time = time_now - lastTimestamp;
		final int elapsed_seconds = (int)elapsed_time;
		for (int i = 0; i < elapsed_seconds; i++) {
			addObservation(time_now, distance_now);
		}
		
		if (graceCount > 0) { // only emit coaching ever so often
			graceCount -= elapsed_seconds;
		}
		else {
			double avg = getValue();
			double cmp = range.compare(avg);
			if (cmp == 0)
			{
				return false;
			}
			fire(w);
			graceCount = minGraceCount;
		}
		return false;
	}

	private void addObservation (double time_now, double distance_now) {
		int pos = cntMeasures % measure_time.length;
		measure_time[pos] = time_now;
		measure_distance[pos] = distance_now;
		cntMeasures ++;
	}

	public double getValue() {
		double elapsed_time = getLastTimestamp() - getOldestTimestamp();
		double elapsed_distance = getLastDistance() - getOldestDistance();
		
		switch (dimension) {
		case PACE:
			if (elapsed_distance == 0)
				return 0;
			return elapsed_time / elapsed_distance;
		case SPEED:
			if (elapsed_time == 0)
				return 0;
			return elapsed_distance / elapsed_time;
		}
		return 0;
	}
	
	private void reset() {
		for (int i = 0; i < measure_time.length; i++) {
			measure_time[i] = 0;
			measure_distance[i] = 0;
		}
		cntMeasures = 1;
		graceCount = initialGrace;
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
