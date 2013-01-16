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
import org.runnerup.workout.feedback.CountdownFeedback;

import android.content.SharedPreferences;
import android.widget.TextView;

public class WorkoutBuilder {

	/**
	 * @return workout based on SharedPreferences
	 */
	public static Workout createDefaultWorkout(TextView log,
			SharedPreferences prefs) {
		Workout w = new Workout();
		ArrayList<Activity> activities = new ArrayList<Activity>(2);
		if (prefs.getBoolean("pref_countdown_active", false))
		{
			long val = 0;
			String vals = prefs.getString("pref_countdown_time", "0");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				System.err.println("Countdown: " + val);
				Activity activity = new Activity();
				activity.intensity = Intensity.RESTING;
				activity.durationType = Dimension.TIME;
				activity.durationValue = val;
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = Dimension.TIME;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.ACTIVITY;
				CountdownFeedback feedback = new CountdownFeedback(Scope.ACTIVITY, Dimension.TIME);
				trigger.triggerAction.add(new CountdownFeedback(Scope.ACTIVITY, Dimension.TIME));
				activity.triggers.add(trigger);
				activities.add(activity);
			}
		}

		Activity activity = new Activity();
		if (prefs.getBoolean("pref_autolap_active", false)) {
			long val = 0;
			String vals = prefs.getString("pref_autolap", "1000");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			activity.setAutolap(val);
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
				t.scope = Scope.ACTIVITY;
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
				t.scope = Scope.ACTIVITY;
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
			t.triggerAction = feedback;
		}

		if (triggers.size() > 0) {
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.LAP;
			ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			triggers.add(ev);
		}

		activity.triggers = triggers;

		activities.add(activity);
		
		/**
		 *
		 */
		w.activities = activities.toArray(w.activities);
		return w;
	}
}
