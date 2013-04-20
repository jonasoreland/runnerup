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
package org.runnerup.workout.feedback;

import org.runnerup.R;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Range;
import org.runnerup.workout.Scope;
import org.runnerup.workout.TargetTrigger;
import org.runnerup.workout.Workout;

import android.content.Context;
import android.speech.tts.TextToSpeech;

public class CoachFeedback extends AudioFeedback {

	Range range = null;
	TargetTrigger trigger = null;
	
	public CoachFeedback(Scope scope, Dimension dimension, Range range, TargetTrigger trigger) {
		super(scope, dimension);
		this.range = range;
		this.trigger = trigger;
	}

	@Override
	public boolean equals(Feedback _other) {
		if (! (_other instanceof CoachFeedback))
			return false;
		
		CoachFeedback other = (CoachFeedback)_other;
		if (! range.contentEquals(other.range))
			return false;
		
		if (! dimension.equal(other.dimension))
			return false;

		if (! scope.equal(other.scope))
			return false;

		return true;
	}

	@Override
	public void emit(Workout s, Context ctx) {
		double val;
		if (trigger != null)
			val = trigger.getValue();
		else
			val = s.get(scope, dimension);

		int cmp = range.compare(val);
		String msg = "";
		if (cmp < 0) {
			msg = " " + ctx.getResources().getString(R.string.cue_speedup);
		} else if (cmp > 0) {
			msg = " " + ctx.getResources().getString(R.string.cue_slowdown);
		}
		if (! "".contentEquals(msg)) {
			textToSpeech.speak(super.getCue(s, ctx) + msg, TextToSpeech.QUEUE_ADD, null);
		}
	}
}
