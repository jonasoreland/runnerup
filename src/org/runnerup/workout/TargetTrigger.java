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
	int graceCount = 3;    // 1.5s start delay
	int minGraceCount = 6; // min delay between "coaching" (3s)
	
	Scope scope = Scope.STEP;
	Dimension dimension = Dimension.PACE;

	Range range = null;

	int cntMeasures = 0;
	double measures[] = null;
	double sumMeasures = 0;
	
	public TargetTrigger(int movingAverageSeconds, int graceSeconds) {
		measures = new double[movingAverageSeconds];
		graceCount = graceSeconds;
	}
	
	@Override
	public boolean onTick(Workout w) {
		if (paused)
		{
			return false;
		}
		double val = w.get(scope, dimension);
		double avg = addObservation(val); // returns moving average

		if (graceCount > 0) { // only emit coaching ever so often
			graceCount--;
		}
		else {
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

	private double addObservation (double val) {
		sumMeasures += val;
		sumMeasures -= measures[cntMeasures % measures.length];
		measures[cntMeasures % measures.length] = val;
		cntMeasures++;
		
		return getValue();
	}

	public double getValue() {
		if (cntMeasures == 0) {
			return 0;
		} else if (cntMeasures < measures.length) {
			return sumMeasures / cntMeasures;
		} else {
			return sumMeasures / measures.length;
		}
	}
	
	private void clear() {
		for (int i = 0; i < measures.length; i++)
			measures[i] = 0;
		cntMeasures = 0;
		sumMeasures = 0;
	}
	
	@Override
	public void onStart(Scope what, Workout s) {
		if (this.scope == what) {
			clear();
			graceCount = minGraceCount;
			for (Feedback f : triggerAction) {
				f.onStart(s);
			}
		}
	}

	@Override
	public void onPause(Workout s) {
		paused = true;
		graceCount = minGraceCount;
		clear();
	}

	@Override
	public void onStop(Workout s) {
		paused = true;
	}

	@Override
	public void onResume(Workout s) {
		paused = false;
		graceCount = minGraceCount;
		clear();
	}

	@Override
	public void onComplete(Scope what, Workout s) {
	}
}
