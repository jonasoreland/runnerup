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
package org.runnerup.view;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.runnerup.R;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.feedback.AudioFeedback;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

public class SettingsActivity extends PreferenceActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.settings);
		setContentView(R.layout.settings_wrapper);

		{
			Preference btn = (Preference)findPreference("test_cueinfo");
			btn.setOnPreferenceClickListener(onTestCueinfoClick);
		}
		
		{
			Preference btn = (Preference)findPreference("exportdb");
			btn.setOnPreferenceClickListener(onExportClick);
		}
		{
			Preference btn = (Preference)findPreference("importdb");
			btn.setOnPreferenceClickListener(onImportClick);
		}
	}
	
	OnPreferenceClickListener onTestCueinfoClick = new OnPreferenceClickListener() {

		TextToSpeech tts = null;
		ArrayList<Feedback> feedback = new ArrayList<Feedback>();
		
		private OnInitListener mTTSOnInitListener = new OnInitListener() {

			@Override
			public void onInit(int arg0) {
				Workout w = Workout.fakeWorkoutForTestingAudioCue();
				w.setTts(tts);
				for (Feedback f : feedback) {
					f.emit(w,  SettingsActivity.this.getApplicationContext());
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

	static int copyFile(String to, String from) throws IOException {
		FileInputStream input = null;
		FileOutputStream output = null;

		try {
			input = new FileInputStream(from);
			output = new FileOutputStream(to);
			int cnt = 0;
			byte buf[] = new byte[1024];
			while (input.read(buf) > 0) {
				cnt += buf.length;
				output.write(buf);
			}
			input.close();
			output.close();
			return cnt;
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException ex) {
				}
			}
			if (output != null) {
				try {
					output.close();
				} catch (IOException ex) {
				}
			}
		}
	}
	
	OnPreferenceClickListener onExportClick = new OnPreferenceClickListener() {

		@Override
		public boolean onPreferenceClick(Preference preference) {
			AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
			String dstdir = "/mnt/sdcard";
			builder.setTitle("Export runnerup.db to " + dstdir);
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
				
			};
			String from = "/data/data/org.runnerup/databases/runnerup.db";
			String to = dstdir + "/runnerup.db.export";
			try {
				int cnt = copyFile(to, from);
				builder.setMessage("Copied " + cnt + " bytes");
				builder.setPositiveButton("Great!", listener);
			} catch (IOException e) {
				builder.setMessage("Exception: " + e.toString());
				builder.setNegativeButton("Darn!", listener);
			}
			builder.show();
			return false;
		}
	};

	OnPreferenceClickListener onImportClick = new OnPreferenceClickListener() {

		@Override
		public boolean onPreferenceClick(Preference preference) {
			AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
			String srcdir = "/mnt/sdcard";
			builder.setTitle("Import runnerup.db from " + srcdir);
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
				
			};
			String to = "/data/data/org.runnerup/databases/runnerup.db";
			String from = srcdir + "/runnerup.db.export";
			try {
				int cnt = copyFile(to, from);
				builder.setMessage("Copied " + cnt + " bytes");
				builder.setPositiveButton("Great!", listener);
			} catch (IOException e) {
				builder.setMessage("Exception: " + e.toString());
				builder.setNegativeButton("Darn!", listener);
			}
			builder.show();
			return false;
		}
	};
}
