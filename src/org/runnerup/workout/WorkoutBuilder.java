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
import android.text.format.DateUtils;
import android.widget.TextView;

public class WorkoutBuilder {

	static ArrayList<Trigger> createDefaultTriggers(SharedPreferences prefs) {
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

		return triggers;
	}
	
	/**
	 * @return workout based on SharedPreferences
	 */
	public static Workout createDefaultWorkout(TextView log,
			SharedPreferences prefs) {
		Workout w = new Workout();
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
				trigger.triggerAction.add(new CountdownFeedback(Scope.ACTIVITY, Dimension.TIME));
				activity.triggers.add(trigger);
				w.activities.add(activity);
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

		activity.triggers = createDefaultTriggers(prefs);
		if (activity.triggers.size() > 0) {
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.LAP;
			ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			activity.triggers.add(ev);
		}

		w.activities.add(activity);
		
		/**
		 *
		 */
		return w;
	}
	
	
	
	public static Workout createDefaultIntervalWorkout(TextView debugView, SharedPreferences prefs) {
		Workout w = new Workout();
		boolean warmup = false;
		boolean cooldown = true;

		if (warmup) {
			Activity activity = new Activity();
			activity.intensity = Intensity.WARMUP;
			activity.durationType = null;
			w.activities.add(activity);
		}
		
		ArrayList<Trigger> triggers = createDefaultTriggers(prefs);
		if (triggers.size() > 0) {
			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STARTED;
				ev.scope = Scope.ACTIVITY;
				ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
				triggers.add(ev);
			}
			
			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.COMPLETED;
				ev.scope = Scope.ACTIVITY;
				ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.COMPLETED));
				triggers.add(ev);
			}
		}
		
		int repetitions = (int) parseDouble(prefs.getString("intervalRepetitions", "1"), 1);
		
		int intervalType = prefs.getInt("intervalType", 0);
		long intervalTime = parseSeconds(prefs.getString("intervalTime", "00:04:00"), 4 * 60);
		double intevalDistance = 1000 * parseDouble(prefs.getString("intervalDistance", "1.0"), 1.0);
		int intervalRestType = prefs.getInt("intervalRestType", 0);
		long intervalRestTime = parseSeconds(prefs.getString("intervalRestTime", "00:01:00"), 60);
		double intevalRestDistance = 1000 * parseDouble(prefs.getString("intervalRestDistance", "0.2"), 0.2);
		for (int i = 0; i < repetitions; i++) {
			Activity activity = new Activity();
			switch (intervalType) {
			case 0: // Time
				activity.durationType = Dimension.TIME;
				activity.durationValue = intervalTime;
				break;
			case 1: // Distance
				activity.durationType = Dimension.DISTANCE;
				activity.durationValue = intevalDistance;
				break;
			}
			activity.triggers = triggers;
			w.activities.add(activity);

			if (i + 1 != repetitions) {
				Activity rest = new Activity();
				rest.intensity = Intensity.RESTING;
				switch (intervalRestType) {
				case 0: // Time
					rest.durationType = Dimension.TIME;
					rest.durationValue = intervalRestTime;
					break;
				case 1: // Distance
					rest.durationType = Dimension.DISTANCE;
					rest.durationValue = intevalRestDistance;
					break;
				}
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = rest.durationType;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.ACTIVITY;
				trigger.triggerAction.add(new CountdownFeedback(Scope.ACTIVITY, rest.durationType));
				rest.triggers.add(trigger);

				w.activities.add(rest);
			}
		}

		if (cooldown) {
			Activity activity = new Activity();
			activity.intensity = Intensity.COOLDOWN;
			activity.durationType = null;
			w.activities.add(activity);
		}
		
		return w;
	}

	private static double parseDouble(String string, double defaultValue) {
		double distance = defaultValue;
		try {
			distance = Double.parseDouble(string);
		} catch (NumberFormatException ex) {
		}
		return distance;
	}

	private static long parseSeconds(String string, long defaultValue) {
		String[] split = string.split(":");
		long mul = 1;
		long sum = 0;
		for (int i = split.length - 1; i >= 0; i--) {
			sum += Long.parseLong(split[i]) * mul;
			mul *= 60;
		}
		return sum;
	}

	public static boolean validateSeconds(String newValue) {
		long seconds = parseSeconds(newValue, -1);
		long seconds2 = parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
		if (seconds == seconds2)
			return true;
		return false;
	}
}
