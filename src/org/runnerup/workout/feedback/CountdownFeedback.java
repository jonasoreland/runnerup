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
package org.runnerup.workout.feedback;

import java.util.HashMap;

import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

public class CountdownFeedback extends Feedback {

	Scope scope = Scope.STEP;
	Dimension dimension = Dimension.TIME;
	TextView textView = null;

	public CountdownFeedback(Scope s, Dimension d) {
		this.scope = s;
		this.dimension = d;
	}
	
	@Override
	public void onInit(Workout s, HashMap<String, Object> bindValues) {
		textView = (TextView) bindValues.get(Workout.KEY_COUNTER_VIEW);
	}
	
	@Override
	public void onStart(Workout s) {
		textView.setVisibility(View.VISIBLE);
	}
	
	@Override
	public void onEnd(Workout s) {
		textView.setVisibility(View.GONE);
	}

	@Override
	public boolean equals(Feedback _other) {
		if (!(_other instanceof CountdownFeedback))
			return false;

		return true;
	}

	@Override
	public void emit(Workout w, Context ctx) {
		double curr = w.get(scope, dimension); // SI
		double duration = w.getDuration(scope, dimension);
		if (duration > curr) {
			textView.setVisibility(View.VISIBLE);
			textView.setText(dimension.getRemainingText(ctx, duration - curr));
		}
		else {
			textView.setVisibility(View.INVISIBLE);
		}
	}

}
