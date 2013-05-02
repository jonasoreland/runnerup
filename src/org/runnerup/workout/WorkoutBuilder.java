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

import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.view.AudioCueSettingsActivity;
import org.runnerup.workout.feedback.AudioCountdownFeedback;
import org.runnerup.workout.feedback.AudioFeedback;
import org.runnerup.workout.feedback.CoachFeedback;
import org.runnerup.workout.feedback.CountdownFeedback;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

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
	
	public static void addPauseStopResumeTriggers(ArrayList<Trigger> list, SharedPreferences prefs) {
		if (prefs.getBoolean("cueinfo_skip_startstop", false) == false) {
			{
				EventTrigger p = new EventTrigger();
				p.event = Event.PAUSED;
				p.scope = Scope.STEP;
				p.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.PAUSED));
				list.add(p);
			}

			{
				EventTrigger r = new EventTrigger();
				r.event = Event.RESUMED;
				r.scope = Scope.STEP;
				r.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.RESUMED));
				list.add(r);
			}

			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STOPPED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.STOPPED));
				list.add(ev);
			}
		}
	}
	
	/**
	 * @return workout based on SharedPreferences
	 */
	public static Workout createDefaultWorkout(SharedPreferences prefs,
			SharedPreferences audioPrefs,
			boolean targetPace) {
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
		final boolean skip_startstop_cue = prefs.getBoolean("cueinfo_skip_startstop", false);

		if (step.triggers.size() > 0) {
			EventTrigger ev = new EventTrigger();
			ev.event = Event.STARTED;
			ev.scope = Scope.LAP;
			ev.triggerAction.add(new AudioFeedback(Scope.LAP, Event.STARTED));
			if (skip_startstop_cue == false) {
				ev.skipCounter = 1;
			}
			step.triggers.add(ev);
		}

		if (skip_startstop_cue == false)
		{
			if (countdownStep != null) {
				createAudioCountdown(countdownStep);
			}

			// activity started/paused/resumed
			{
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STARTED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Scope.WORKOUT, Event.STARTED));
				step.triggers.add(ev);
			}
			
			addPauseStopResumeTriggers(step.triggers, audioPrefs);
		}
		
		w.steps.add(step);

		if (targetPace)
		{
			double unitMeters = Formatter.getUnitMeters(prefs);
			double seconds_per_unit = (double)SafeParse.parseSeconds(prefs.getString("basic_target_pace_max", "00:05:00"), 5*60);
			int targetPaceRange = prefs.getInt("basic_target_pace_min_range", 15);
			double targetPaceMax = seconds_per_unit / unitMeters;
			double targetPaceMin = (targetPaceMax * unitMeters - targetPaceRange) / unitMeters;
			Range range = new Range(targetPaceMin, targetPaceMax);
			int averageSeconds = SafeParse.parseInt(prefs.getString("target_pace_moving_average_seconds", "20"), 20);
			int graceSeconds = SafeParse.parseInt(prefs.getString("target_pace_grace_seconds", "30"), 30);
			TargetTrigger tr = new TargetTrigger(averageSeconds, graceSeconds);
			tr.scope = Scope.STEP;
			tr.dimension = Dimension.PACE;
			tr.range = range;
			tr.triggerAction.add(new CoachFeedback(Scope.WORKOUT, Dimension.PACE, range, tr));
			step.triggers.add(tr);

			/**
			 * Set it on step, so that it will be recorded in DB
			 */
			step.targetType = Dimension.PACE;
			step.targetValue = targetPaceMax;
		}
		
		/**
		 *
		 */
		return w;
	}
	
	public static Workout createDefaultIntervalWorkout(SharedPreferences prefs,
			SharedPreferences audioPrefs) {
		Workout w = new Workout();
		final boolean warmup = true;
		final boolean cooldown = true;
		final boolean skip_startstop_cue = prefs.getBoolean("cueinfo_skip_startstop", false);

		if (warmup) {
			Step step = new Step();
			step.intensity = Intensity.WARMUP;
			step.durationType = null;
			if (skip_startstop_cue == false) {
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STARTED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Intensity.WARMUP, Event.STARTED));
				step.triggers.add(ev);
			}
			w.steps.add(step);
		}

		boolean countdown_before_interval = true; //TODO configurable ??
		
		if (countdown_before_interval)
		{
			long val = 15; // default 15s
			String vals = prefs.getString("pref_interval_countdown_time", "15");
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
			}
		}
		
		ArrayList<Trigger> triggers = createDefaultTriggers(audioPrefs);
		addPauseStopResumeTriggers(triggers, audioPrefs);
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
		
		int repetitions = (int) SafeParse.parseDouble(prefs.getString("intervalRepetitions", "1"), 1);
		
		int intervalType = prefs.getInt("intervalType", 0);
		long intervalTime = SafeParse.parseSeconds(prefs.getString("intervalTime", "00:04:00"), 4 * 60);
		double intevalDistance = SafeParse.parseDouble(prefs.getString("intervalDistance", "1000"), 1000);
		int intervalRestType = prefs.getInt("intervalRestType", 0);
		long intervalRestTime = SafeParse.parseSeconds(prefs.getString("intervalRestTime", "00:01:00"), 60);
		double intevalRestDistance = SafeParse.parseDouble(prefs.getString("intervalRestDistance", "200"), 200);
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

			if (true) {
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
				addPauseStopResumeTriggers(rest.triggers, audioPrefs);
				
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
			addPauseStopResumeTriggers(step.triggers, audioPrefs);
			if (skip_startstop_cue == false) {
				EventTrigger ev = new EventTrigger();
				ev.event = Event.STARTED;
				ev.scope = Scope.STEP;
				ev.triggerAction.add(new AudioFeedback(Intensity.COOLDOWN, Event.STARTED));
				step.triggers.add(ev);
			}
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
	
	public static boolean validateSeconds(String newValue) {
		//TODO move this somewhere
		long seconds = SafeParse.parseSeconds(newValue, -1);
		long seconds2 = SafeParse.parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
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

	public static Workout createAdvancedWorkout(SharedPreferences prefs,
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
		
		for (int i = 0; i < 2; i++)
		{
			int runtimes[] = { 6 * 60, 4 * 60, 2 * 60 };
			int resttimes[] = { 120, 90, 60 };
			for (int j = 0; j < 3; j++)
			{
				Step step0 = new Step();
				step0.durationType = Dimension.TIME;
				step0.durationValue = runtimes[j];
				step0.triggers = triggers;
				w.steps.add(step0);
				Step rest0 = Step.createPauseStep(Dimension.TIME, resttimes[j]);
				IntervalTrigger trigger = new IntervalTrigger();
				trigger.dimension = rest0.durationType;
				trigger.first = 1;
				trigger.interval = 1;
				trigger.scope = Scope.STEP;
				trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, rest0.durationType));
				rest0.triggers.add(trigger);
				createAudioCountdown(rest0);
				w.steps.add(rest0);
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
}
