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
package org.runnerup.view;

import java.util.ArrayList;
import java.util.HashMap;

import org.runnerup.R;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.feedback.AudioFeedback;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

public class AudioCueSettingsActivity extends PreferenceActivity {

	String settingsName = null;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		settingsName = intent.getStringExtra("name");
		if (settingsName != null) {
	         PreferenceManager prefMgr = getPreferenceManager();
	         prefMgr.setSharedPreferencesName(settingsName);
	         prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
	    }

		addPreferencesFromResource(R.layout.audio_cue_settings);
		setContentView(R.layout.settings_wrapper);

		{
			Preference btn = (Preference)findPreference("test_cueinfo");
			btn.setOnPreferenceClickListener(onTestCueinfoClick);
		}
	}
	
	OnPreferenceClickListener onTestCueinfoClick = new OnPreferenceClickListener() {

		TextToSpeech tts = null;
		ArrayList<Feedback> feedback = new ArrayList<Feedback>();
		
		private OnInitListener mTTSOnInitListener = new OnInitListener() {

			@Override
			public void onInit(int arg0) {
				Workout w = Workout.fakeWorkoutForTestingAudioCue();
				HashMap<String, Object> bindValues = new HashMap<String, Object>();
				bindValues.put(Workout.KEY_TTS, tts);
				for (Feedback f : feedback) {
					f.onInit(w, bindValues);
					f.emit(w,  AudioCueSettingsActivity.this.getApplicationContext());
				}
			}
			
		};

		@Override
		public boolean onPreferenceClick(Preference arg0) {
			Context ctx = getApplicationContext();

			feedback.clear();
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx); 
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
			
			if (tts != null) {
				mTTSOnInitListener.onInit(0);
			} else {
				tts = new TextToSpeech(ctx, mTTSOnInitListener);
			}
			
			return false;
		}
	};
}
