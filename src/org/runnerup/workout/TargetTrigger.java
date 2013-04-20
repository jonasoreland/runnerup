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
	double lastTimestamp = 0;
	
	int graceCount    = 25; //
	int initialGrace  = 10;
	int minGraceCount = 25; //
	
	Scope scope = Scope.STEP;
	Dimension dimension = Dimension.PACE;

	Range range = null;

	int cntMeasures = 0;
	double measures[] = null;
	double sumMeasures = 0;
	
	public TargetTrigger(int movingAverageSeconds, int graceSeconds) {
		measures = new double[movingAverageSeconds];
		minGraceCount = graceSeconds;
		reset();
	}
	
	@Override
	public boolean onTick(Workout w) {
		if (paused)
		{
			return false;
		}

		double now = w.get(Scope.WORKOUT, Dimension.TIME);

		if (now < lastTimestamp) {
			// time move backwards...skip
			lastTimestamp = now;
		}
		if (lastTimestamp == 0) {
			// first time
			lastTimestamp = now;
		}
		if ((now - lastTimestamp) < 1.0) {
			return false;
		}
		final int elapsed = (int) (now - lastTimestamp);
		lastTimestamp = now;

		final double val = w.get(scope, dimension);
		for (int i = 0; i < elapsed; i++) {
			addObservation(val);
		}
		double avg = getValue();
		
		if (graceCount > 0) { // only emit coaching ever so often
			graceCount -= elapsed;
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
	
	private void reset() {
		for (int i = 0; i < measures.length; i++)
			measures[i] = 0;
		cntMeasures = 0;
		sumMeasures = 0;
		
		lastTimestamp = 0;
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
