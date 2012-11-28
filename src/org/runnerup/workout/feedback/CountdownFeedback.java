package org.runnerup.workout.feedback;

import org.runnerup.workout.Feedback;
import org.runnerup.workout.Workout;

import android.content.Context;

public class CountdownFeedback extends Feedback {

	@Override
	public boolean equals(Feedback _other) {
		if (!(_other instanceof CountdownFeedback))
			return false;

		return true;
	}

	@Override
	public void emit(Workout s, Context ctx) {
		// TODO Auto-generated method stub
	}

}
