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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.TitleSpinner.OnSetValueListener;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.feedback.RUTextToSpeech;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AudioCueSettingsActivity extends PreferenceActivity {

    boolean started = false;
    boolean delete = false;
    String settingsName = null;
    AudioSchemeListAdapter adapter = null;
    SQLiteDatabase mDB = null;
    MenuItem newSettings;

    private String DEFAULT = "Default";
    public static final String SUFFIX = "_audio_cues";
    static final String PREFS_DIR = "shared_prefs";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        mDB = DBHelper.getWritableDatabase(this);
        DEFAULT = getString(R.string.Default);

        Intent intent = getIntent();
        settingsName = intent.getStringExtra("name");
        if (settingsName != null) {
            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName(settingsName + SUFFIX);
            prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
        }

        addPreferencesFromResource(R.xml.audio_cue_settings);
        setContentView(R.layout.settings_wrapper);

        {
            Preference btn = findPreference("test_cueinfo");
            btn.setOnPreferenceClickListener(onTestCueinfoClick);
        }

        {
            Preference btn = findPreference("cue_silence");
            if (btn != null) {
                btn.setOnPreferenceClickListener(this.onSilenceClick);
            }
        }

        HRZones hrZones = new HRZones(this);
        boolean hasHR = SettingsActivity.hasHR(this);
        boolean hasHRZones = hrZones.isConfigured();

        if (!hasHR || !hasHRZones) {
            final int remove[] = {
                    R.string.cueinfo_total_hrz,
                    R.string.cueinfo_step_hrz,
                    R.string.cueinfo_lap_hrz,
                    R.string.cueinfo_current_hrz
            };
            removePrefs(remove);
        }

        if (!hasHR) {
            final int remove[] = {
                    R.string.cueinfo_total_hr,
                    R.string.cueinfo_step_hr,
                    R.string.cueinfo_lap_hr,
                    R.string.cueinfo_current_hr
            };
            removePrefs(remove);
        }

        {
            Preference btn = findPreference("tts_settings");
            btn.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent();
                    intent.setAction("com.android.settings.TTS_SETTINGS");
                    startActivity(intent);
                    return false;
                }

            });
        }

        final boolean createNewItem = true;
        adapter = new AudioSchemeListAdapter(mDB,
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE),
                createNewItem);
        adapter.reload();

        {
            TitleSpinner spinner = (TitleSpinner) findViewById(R.id.settings_spinner);
            spinner.setVisibility(View.VISIBLE);
            spinner.setAdapter(adapter);

            if (settingsName == null) {
                spinner.setValue(0);
            } else {
                int idx = adapter.find(settingsName);
                spinner.setValue(idx);
            }
            spinner.setOnSetValueListener(onSetValueListener);
        }
    }

    private void removePrefs(int[] remove) {
        Resources res = getResources();
        PreferenceGroup group = (PreferenceGroup) findPreference("cueinfo");
        if (group == null)
            return;
        for (int aRemove : remove) {
            String s = res.getString(aRemove);
            Preference pref = findPreference(s);
            group.removePreference(pref);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (delete) {
            deleteAudioSchemeImpl(settingsName);
        }
        DBHelper.closeDB(mDB);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        newSettings = menu.add("New settings");
        MenuItem deleteMenuItem = menu.add("Delete settings");
        if (settingsName == null)
            deleteMenuItem.setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == newSettings) {
            createNewAudioSchemeDialog();
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.Are_you_sure));
        builder.setPositiveButton(getString(R.string.Yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        deleteAudioScheme();
                    }
                });
        builder.setNegativeButton(getString(R.string.No),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.show();
        return true;
    }

    void createNewAudioScheme(String scheme) {
        ContentValues tmp = new ContentValues();
        tmp.put(DB.AUDIO_SCHEMES.NAME, scheme);
        tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0);
        mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp);
    }

    private void deleteAudioScheme() {
        delete = true;
        getPreferenceManager().getSharedPreferences().edit().clear().commit();
        /**
         * Can only delete file in "next" activity...cause on destory on this,
         * will save file...
         */

        switchTo(null);
    }

    private void deleteAudioSchemeImpl(String name) {
        /**
         * Start by deleting file...then delete from table...so we don't get
         * stray files
         */
        File a = new File(getFilesDir().getAbsoluteFile() + "/../" + PREFS_DIR + "/" + name
                + SUFFIX + ".xml");
        //noinspection ResultOfMethodCallIgnored
        a.delete();

        String args[] = {
            name
        };
        mDB.delete(DB.AUDIO_SCHEMES.TABLE, DB.AUDIO_SCHEMES.NAME + "= ?", args);
    }

    void updateSortOrder(String name) {
        mDB.execSQL("UPDATE " + DB.AUDIO_SCHEMES.TABLE + " set " + DB.AUDIO_SCHEMES.SORT_ORDER +
                " = (SELECT MAX(" + DB.AUDIO_SCHEMES.SORT_ORDER + ") + 1 FROM "
                + DB.AUDIO_SCHEMES.TABLE + ") " +
                " WHERE " + DB.AUDIO_SCHEMES.NAME + " = '" + name + "'");
    }

    final OnSetValueListener onSetValueListener = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            return newValue;
        }

        @Override
        public int preSetValue(int newValueId) throws IllegalArgumentException {
            String newValue = (String) adapter.getItem(newValueId);
            PreferenceManager prefMgr = getPreferenceManager();
            if (newValue.contentEquals(DEFAULT)) {
                prefMgr.getSharedPreferences().edit().commit();
                switchTo(null);
            } else if (newValue.contentEquals(getString(R.string.New_audio_scheme))) {
                createNewAudioSchemeDialog();
            } else {
                prefMgr.getSharedPreferences().edit().commit();
                updateSortOrder(newValue);
                switchTo(newValue);
            }
            throw new IllegalArgumentException();
        }

    };

    private void switchTo(String name) {

        if (started == false) {
            // TODO investigate "spurious" onItemSelected during start
            started = true;
            return;
        }

        if (name == null && settingsName == null) {
            return;
        }

        if (name != null && settingsName != null && name.contentEquals(settingsName)) {
            return;
        }

        Intent i = new Intent(this, AudioCueSettingsActivity.class);
        if (name != null) {
            i.putExtra("name", name);
        }
        startActivity(i);
        finish();
    }

    private void createNewAudioSchemeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Create_new_audio_cue_scheme));
        // Get the layout inflater
        final EditText editText = new EditText(this);
        builder.setView(editText);
        builder.setPositiveButton(getString(R.string.Create), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String scheme = editText.getText().toString();
                if (!scheme.contentEquals("")) {
                    createNewAudioScheme(scheme);
                    updateSortOrder(scheme);
                    switchTo(scheme);
                }
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    final OnPreferenceClickListener onSilenceClick = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            Resources res = getResources();
            int clearList[] = {
                    R.string.cueinfo_total_distance,
                    R.string.cueinfo_total_time,
                    R.string.cueinfo_total_speed,
                    R.string.cueinfo_total_pace,
                    R.string.cueinfo_lap_distance,
                    R.string.cueinfo_lap_time,
                    R.string.cueinfo_lap_speed,
                    R.string.cueinfo_lap_pace,
                    R.string.cueinfo_target_coaching
            };

            int setList[] = {
                    R.string.cueinfo_skip_startstop
            };

            for (int s : clearList) {
                Preference a = getPreferenceManager().findPreference(res.getString(s));
                if (a != null) {
                    a.getEditor().putBoolean(res.getString(s), false);
                    a.getEditor().commit();
                }
            }

            for (int s : setList) {
                Preference a = getPreferenceManager().findPreference(res.getString(s));
                if (a != null) {
                    a.getEditor().putBoolean(res.getString(s), true);
                    a.getEditor().commit();
                }
            }
            return false;
        }

    };

    final OnPreferenceClickListener onTestCueinfoClick = new OnPreferenceClickListener() {

        TextToSpeech tts = null;
        final ArrayList<Feedback> feedback = new ArrayList<Feedback>();

        private final OnInitListener mTTSOnInitListener = new OnInitListener() {

            @Override
            public void onInit(int arg0) {
                SharedPreferences prefs = null;
                if (settingsName == null || settingsName.contentEquals(DEFAULT))
                    prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                else
                    prefs = getApplicationContext().getSharedPreferences(settingsName + SUFFIX,
                            Context.MODE_PRIVATE);
                final boolean mute = prefs.getBoolean(getResources().getString(R.string.pref_mute_bool),
                        false);

                Workout w = Workout.fakeWorkoutForTestingAudioCue();
                RUTextToSpeech rutts = new RUTextToSpeech(tts, mute, getApplicationContext());
                HashMap<String, Object> bindValues = new HashMap<String, Object>();
                bindValues.put(Workout.KEY_TTS, rutts);
                bindValues.put(Workout.KEY_FORMATTER, new Formatter(AudioCueSettingsActivity.this));
                bindValues.put(Workout.KEY_HRZONES, new HRZones(AudioCueSettingsActivity.this));
                w.onBind(w, bindValues);
                for (Feedback f : feedback) {
                    f.onInit(w);
                    f.onBind(w, bindValues);
                    f.emit(w, AudioCueSettingsActivity.this.getApplicationContext());
                    rutts.emit();
                }
            }
        };

        @Override
        public boolean onPreferenceClick(Preference arg0) {
            Context ctx = getApplicationContext();
            Resources res = getResources();

            feedback.clear();
            SharedPreferences prefs = null;
            if (settingsName == null || settingsName.contentEquals(DEFAULT))
                prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            else
                prefs = ctx.getSharedPreferences(settingsName + SUFFIX, Context.MODE_PRIVATE);

            WorkoutBuilder.addFeedbackFromPreferences(prefs, res, feedback);

            tts = new TextToSpeech(ctx, mTTSOnInitListener);
            return false;
        }
    };
}
