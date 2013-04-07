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
package org.runnerup.workout;

import java.util.ArrayList;
import java.util.Arrays;

import org.runnerup.view.AudioCueSettingsActivity;
import org.runnerup.workout.feedback.AudioCountdownFeedback;
import org.runnerup.workout.feedback.AudioFeedback;
import org.runnerup.workout.feedback.CountdownFeedback;

import android.content.Context;
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
				t.scope = Scope.STEP;
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
				t.scope = Scope.STEP;
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
		if (Dimension.SPEED_CUE_ENABLED && prefs.getBoolean("cueinfo_total_speed", false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.SPEED));
		}
		if (prefs.getBoolean("cueinfo_total_pace", false)) {
			feedback.add(new AudioFeedback(Scope.WORKOUT, Dimension.PACE));
		}
		if (prefs.getBoolean("cueinfo_lap_distance", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.DISTANCE));
		}
		if (prefs.getBoolean("cueinfo_lap_time", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.TIME));
		}
		if (Dimension.SPEED_CUE_ENABLED && prefs.getBoolean("cueinfo_lap_speed", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.SPEED));
		}
		if (prefs.getBoolean("cueinfo_lap_pace", false)) {
			feedback.add(new AudioFeedback(Scope.LAP, Dimension.PACE));
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
			SharedPreferences prefs,
			SharedPreferences audioPrefs) {
		Workout w = new Workout();
		Step countdownStep = null;
		if (prefs.getBoolean("pref_countdown_active", false))
		{
			long val = 0;
			String vals = prefs.getString("pref_countdown_time", "0");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			if (val > 0) {
				Step step = Step.createPauseStep(Dimension.TIME, val);
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = Dimension.TIME;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.STEP;
				trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, Dimension.TIME));
				step.triggers.add(trigger);
				w.steps.add(step);
				countdownStep = step;
			}
		}

		Step step = new Step();
		if (prefs.getBoolean("pref_autolap_active", false)) {
			long val = 0;
			String vals = prefs.getString("pref_autolap", "1000");
			try {
				val = Long.parseLong(vals);
			} catch (NumberFormatException e) {
			}
			step.setAutolap(val);
		}

		step.triggers = createDefaultTriggers(audioPrefs);
		if (step.triggers.size() > 0) {
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.LAP;
			ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			step.triggers.add(ev);

			if (countdownStep != null) {
				createAudioCountdown(countdownStep);
			}
		}

		w.steps.add(step);
		
		/**
		 *
		 */
		return w;
	}
	
	public static Workout createDefaultIntervalWorkout(TextView debugView,
			SharedPreferences prefs,
			SharedPreferences audioPrefs) {
		Workout w = new Workout();
		boolean warmup = true;
		boolean cooldown = true;

		if (warmup) {
			Step step = new Step();
			step.intensity = Intensity.WARMUP;
			step.durationType = null;
			w.steps.add(step);
		}
		
		ArrayList<Trigger> triggers = createDefaultTriggers(audioPrefs);
		if (triggers.size() > 0) {
			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STARTED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
				triggers.add(ev);
			}
			
			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.COMPLETED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.COMPLETED));
				triggers.add(ev);
			}
		}
		
		int repetitions = (int) parseDouble(prefs.getString("intervalRepetitions", "1"), 1);
		
		int intervalType = prefs.getInt("intervalType", 0);
		long intervalTime = parseSeconds(prefs.getString("intervalTime", "00:04:00"), 4 * 60);
		double intevalDistance = parseDouble(prefs.getString("intervalDistance", "1000"), 1000);
		int intervalRestType = prefs.getInt("intervalRestType", 0);
		long intervalRestTime = parseSeconds(prefs.getString("intervalRestTime", "00:01:00"), 60);
		double intevalRestDistance = parseDouble(prefs.getString("intervalRestDistance", "200"), 200);
		for (int i = 0; i < repetitions; i++) {
			Step step = new Step();
			switch (intervalType) {
			case 0: // Time
				step.durationType = Dimension.TIME;
				step.durationValue = intervalTime;
				break;
			case 1: // Distance
				step.durationType = Dimension.DISTANCE;
				step.durationValue = intevalDistance;
				break;
			}
			step.triggers = triggers;
			w.steps.add(step);

			if (i + 1 != repetitions) {
				Step rest = null;
				switch (intervalRestType) {
				case 0: // Time
					rest = Step.createPauseStep(Dimension.TIME, intervalRestTime);
					break;
				case 1: // Distance
					rest = Step.createPauseStep(Dimension.DISTANCE, intevalRestDistance);
					break;
				}
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = rest.durationType;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.STEP;
				trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, rest.durationType));
				rest.triggers.add(trigger);

				if (triggers.size() > 0) {
					createAudioCountdown(rest);
				}
				w.steps.add(rest);
			}
		}

		if (cooldown) {
			Step step = new Step();
			step.intensity = Intensity.COOLDOWN;
			step.durationType = null;
			w.steps.add(step);
		}
		
		return w;
	}

	private static void createAudioCountdown(Step step) {
		double first = 0;
		ArrayList<Double> list = new ArrayList<Double>();
		switch (step.getDurationType()) {
		case TIME:
			first = 60; // 1 minute
			Double tmp0[] = { 60d, 30d, 10d, 5d, 3d, 2d, 1d };
			list.addAll(Arrays.asList(tmp0));
			break;
		case DISTANCE:
			first = 100; // 100 meters
			Double tmp1[] ={ 100d, 50d, 20d, 10d }; 
			list.addAll(Arrays.asList(tmp1));
			break;
		default:
			return;
		}

		if (step.getDurationValue() > first) {
			/**
			 * If longer than limit...create a Interval trigger for ">" part
			 */
			IntervalTrigger trigger = new IntervalTrigger();
			trigger.dimension = step.getDurationType();
			trigger.scope = Scope.STEP;
			trigger.first = first;
			trigger.interval = first;
			trigger.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(trigger);
		}

		/**
		 * Then create a list trigger for reminder...
		 */
		ArrayList<Double> triggerTimes = new ArrayList<Double>();
		for (Double d : list) {
			if (d >= step.getDurationValue())
				continue;
			double val = step.getDurationValue() - d;
			if ((val % first) == 0) {
				continue; // handled by interval trigger
			}
			triggerTimes.add(val);
		}
		
		{
			ListTrigger trigger = new ListTrigger();
			trigger.dimension = step.getDurationType();
			trigger.scope = Scope.STEP;
			trigger.triggerTimes = triggerTimes;
			trigger.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(trigger);
		}

		if (true) {
			/**
			 * Add add information just when pause step starts...
			 */
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.STEP;
			ev.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
			step.triggers.add(ev);
		}
	}
	
	public static double parseDouble(String string, double defaultValue) {
		//TODO move this somewhere
		double distance = defaultValue;
		try {
			distance = Double.parseDouble(string);
		} catch (NumberFormatException ex) {
		}
		return distance;
	}

	public static long parseSeconds(String string, long defaultValue) {
		//TODO move this somewhere
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
		//TODO move this somewhere
		long seconds = parseSeconds(newValue, -1);
		long seconds2 = parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
		if (seconds == seconds2)
			return true;
		return false;
	}

	public static SharedPreferences getAudioCuePreferences(Context ctx, SharedPreferences pref, String key) {
		return getSubPreferences(ctx, pref, key, AudioCueSettingsActivity.DEFAULT, AudioCueSettingsActivity.SUFFIX);
	}

	public static SharedPreferences getSubPreferences(Context ctx, SharedPreferences pref,
			String key, String defaultVal, String suffix) {
		String name = pref.getString(key, null);
		if (name == null || name.contentEquals(defaultVal)) {
			return pref;
		}
		return ctx.getSharedPreferences(name + suffix, Context.MODE_PRIVATE);
	}
}
