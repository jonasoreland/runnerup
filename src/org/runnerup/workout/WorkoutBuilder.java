/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

import java.util.ArrayList;

import org.runnerup.workout.feedback.AudioFeedback;

import android.content.SharedPreferences;
import android.widget.TextView;

public class WorkoutBuilder {

	/**
	 * @return workout based on SharedPreferences
	 */
	public static Workout createDefaultWorkout(TextView log,
			SharedPreferences prefs) {
		Workout w = new Workout();
		Activity arr[] = new Activity[1];
		arr[0] = new Activity();
		if (prefs.getBoolean("pref_autolap_active", false)) {
			long val = 0;
			String vals = prefs.getString("pref_autolap", "1000");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			arr[0].setAutolap(val);
		}

		ArrayList<Feedback> feedback = new ArrayList<Feedback>();
		ArrayList<Trigger> triggers = new ArrayList<Trigger>();

		if (prefs.getBoolean("cue_time", false)) {
			long val = 0;
			String vals = prefs.getString("cue_time_intervall", "120");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				IntervalTrigger t = new IntervalTrigger();
				t.first = val;
				t.interval = val;
				t.scope = Scope.WORKOUT;
				t.dimension = Dimension.TIME;
				triggers.add(t);
			}
		}
		if (prefs.getBoolean("cue_distance", false)) {
			long val = 0;
			String vals = prefs.getString("cue_distance_intervall", "1000");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				IntervalTrigger t = new IntervalTrigger();
				t.first = val;
				t.interval = val;
				t.scope = Scope.WORKOUT;
				t.dimension = Dimension.DISTANCE;
				triggers.add(t);
			}
		}

		if (prefs.getBoolean("cueinfo_total_distance", false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.DISTANCE));
		}
		if (prefs.getBoolean("cueinfo_total_time", false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.TIME));
		}
		if (prefs.getBoolean("cueinfo_total_speed", false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.SPEED));
		}
		if (prefs.getBoolean("cueinfo_lap_distance", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.DISTANCE));
		}
		if (prefs.getBoolean("cueinfo_lap_time", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.TIME));
		}
		if (prefs.getBoolean("cueinfo_lap_speed", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.SPEED));
		}

		for (Trigger t : triggers) {
			t.triggerAction = feedback.toArray(t.triggerAction);
		}

		if (triggers.size() > 0) {
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.LAP;
			ev.triggerAction = new Feedback[1];
			ev.triggerAction[0] = new AudioFeedback(Scope.LAP, Event.STARTED);
			triggers.add(ev);
		}

		arr[0].triggers = triggers.toArray(arr[0].triggers);

		w.activities = arr;
		return w;
	}
}
