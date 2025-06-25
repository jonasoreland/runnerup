package org.runnerup.view;

import static android.content.Context.MODE_PRIVATE;
import static org.runnerup.view.AudioCueSettingsActivity.SUFFIX;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.feedback.RUTextToSpeech;

public class AudioCueSettingsFragment extends PreferenceFragmentCompat {
  private boolean started = false;
  private String settingsName = null;
  private AudioSchemeListAdapter adapter = null;
  private SQLiteDatabase mDB = null;
  private MenuItem newSettings;

  private String DEFAULT = "Default";
  private static final String PREFS_DIR = "shared_prefs";

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mDB = DBHelper.getWritableDatabase(requireContext());
    DEFAULT = getString(org.runnerup.common.R.string.Default);
    setHasOptionsMenu(true); // this fragment has menu items
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    settingsName = requireArguments().getString("name");

    if (settingsName != null) {
      PreferenceManager prefMgr = getPreferenceManager();
      prefMgr.setSharedPreferencesName(settingsName + SUFFIX);
      prefMgr.setSharedPreferencesMode(MODE_PRIVATE);
    }

    setPreferencesFromResource(R.xml.audio_cue_settings, rootKey);

    {
      Preference btn = findPreference("test_cueinfo");
      btn.setOnPreferenceClickListener(onTestCueinfoClick);
    }

    HRZones hrZones = new HRZones(requireContext());
    boolean hasHR = SettingsActivity.hasHR(requireContext());
    boolean hasHRZones = hrZones.isConfigured();

    if (!hasHR || !hasHRZones) {
      final int[] remove = {
        R.string.cueinfo_total_hrz,
        R.string.cueinfo_step_hrz,
        R.string.cueinfo_lap_hrz,
        R.string.cueinfo_current_hrz
      };
      removePrefs(remove);
    }

    if (!hasHR) {
      final int[] remove = {
        R.string.cueinfo_total_hr,
        R.string.cueinfo_step_hr,
        R.string.cueinfo_lap_hr,
        R.string.cueinfo_current_hr
      };
      removePrefs(remove);
    }

    {
      Preference btn = findPreference("tts_settings");
      btn.setOnPreferenceClickListener(
          preference -> {
            Intent intent1 = new Intent().setAction("com.android.settings.TTS_SETTINGS");
            startActivity(intent1);
            return false;
          });
    }
  }

  @NonNull
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    // Inflate our custom layout for the audio cue settings (a TitleSpinner above the list of
    // preferences)
    View layout = inflater.inflate(R.layout.settings_wrapper, container, false);
    ViewGroup settingsContainerView = layout.findViewById(R.id.settings_container_view);

    // Add preferences to our container
    View settingsView = super.onCreateView(inflater, settingsContainerView, savedInstanceState);
    settingsContainerView.addView(settingsView);

    final boolean createNewItem = true;
    adapter = new AudioSchemeListAdapter(mDB, inflater, createNewItem);
    adapter.reload();

    return layout;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    {
      TitleSpinner spinner = view.findViewById(R.id.settings_spinner);
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
    PreferenceGroup group = findPreference("cueinfo");
    if (group == null) return;
    for (int aRemove : remove) {
      String s = res.getString(aRemove);
      Preference pref = findPreference(s);
      group.removePreference(pref);
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    newSettings = menu.add("New settings");
    MenuItem deleteMenuItem = menu.add("Delete settings");
    if (settingsName == null) deleteMenuItem.setEnabled(false);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item == newSettings) {
      createNewAudioSchemeDialog();
      return true;
    }
    new AlertDialog.Builder(requireContext())
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(
            org.runnerup.common.R.string.Yes,
            (dialog, which) -> {
              dialog.dismiss();
              deleteAudioScheme();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.No,
            (dialog, which) -> {
              // Do nothing but close the dialog
              dialog.dismiss();
            })
        .show();
    return true;
  }

  private void createNewAudioScheme(String scheme) {
    ContentValues tmp = new ContentValues();
    tmp.put(Constants.DB.AUDIO_SCHEMES.NAME, scheme);
    tmp.put(Constants.DB.AUDIO_SCHEMES.SORT_ORDER, 0);
    mDB.insert(Constants.DB.AUDIO_SCHEMES.TABLE, null, tmp);
  }

  private void deleteAudioScheme() {
    deleteAudioSchemeImpl(settingsName);
    switchTo(null);
  }

  private void deleteAudioSchemeImpl(String name) {
    /*
     * Start by deleting file...then delete from table...so we don't get
     * stray files
     */
    File a =
        new File(
            requireContext().getFilesDir().getParent()
                + File.separator
                + PREFS_DIR
                + "/"
                + name
                + SUFFIX
                + ".xml");
    //noinspection ResultOfMethodCallIgnored
    a.delete();

    String[] args = {name};
    mDB.delete(Constants.DB.AUDIO_SCHEMES.TABLE, Constants.DB.AUDIO_SCHEMES.NAME + "= ?", args);
  }

  private void updateSortOrder(String name) {
    mDB.execSQL(
        "UPDATE "
            + Constants.DB.AUDIO_SCHEMES.TABLE
            + " set "
            + Constants.DB.AUDIO_SCHEMES.SORT_ORDER
            + " = (SELECT MAX("
            + Constants.DB.AUDIO_SCHEMES.SORT_ORDER
            + ") + 1 FROM "
            + Constants.DB.AUDIO_SCHEMES.TABLE
            + ") "
            + " WHERE "
            + Constants.DB.AUDIO_SCHEMES.NAME
            + " = '"
            + name
            + "'");
  }

  private final OnSetValueListener onSetValueListener =
      new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
          return newValue;
        }

        @Override
        public int preSetValue(int newValueId) throws IllegalArgumentException {
          String newValue = (String) adapter.getItem(newValueId);
          PreferenceManager prefMgr = getPreferenceManager();
          if (newValue.contentEquals(DEFAULT)) {
            prefMgr.getSharedPreferences().edit().apply();
            switchTo(null);
          } else if (newValue.contentEquals(
              getString(org.runnerup.common.R.string.New_audio_scheme))) {
            createNewAudioSchemeDialog();
          } else {
            prefMgr.getSharedPreferences().edit().apply();
            updateSortOrder(newValue);
            switchTo(newValue);
          }
          throw new IllegalArgumentException();
        }
      };

  private void switchTo(String name) {

    if (!started) {
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

    Bundle bundle = new Bundle();
    if (name != null) {
      bundle.putString("name", name);
    }

    requireActivity()
        .getSupportFragmentManager()
        .beginTransaction()
        .setReorderingAllowed(true)
        .replace(R.id.settings_fragment_container, AudioCueSettingsFragment.class, bundle)
        .commit();
  }

  private void createNewAudioSchemeDialog() {
    final EditText editText = new EditText(requireContext());
    editText.setMinimumHeight(48);
    editText.setMinimumWidth(48);

    new AlertDialog.Builder(requireContext())
        .setTitle(org.runnerup.common.R.string.Create_new_audio_cue_scheme)
        // Get the layout inflater
        .setView(editText)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              String scheme = editText.getText().toString();
              if (!scheme.contentEquals("")) {
                createNewAudioScheme(scheme);
                updateSortOrder(scheme);
                switchTo(scheme);
              }
            })
        .setNegativeButton(org.runnerup.common.R.string.Cancel, (dialog, which) -> {})
        .show();
  }

  private void CreateNewNoTtsAvailableDialog() {
    new AlertDialog.Builder(requireContext())
        .setTitle(org.runnerup.common.R.string.tts_not_available_title)
        .setMessage(org.runnerup.common.R.string.tts_not_available)
        .setPositiveButton(org.runnerup.common.R.string.OK, null)
        .show();
  }

  private final OnPreferenceClickListener onTestCueinfoClick =
      new OnPreferenceClickListener() {

        TextToSpeech tts = null;
        final ArrayList<Feedback> feedback = new ArrayList<>();

        private final TextToSpeech.OnInitListener mTTSOnInitListener =
            status -> {
              if (status != TextToSpeech.SUCCESS) {
                CreateNewNoTtsAvailableDialog();
                return;
              }

              SharedPreferences prefs;
              if (settingsName == null || settingsName.contentEquals(DEFAULT))
                prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
              else
                prefs =
                    requireContext()
                        .getSharedPreferences(settingsName + SUFFIX, Context.MODE_PRIVATE);
              final boolean mute =
                  prefs.getBoolean(getResources().getString(R.string.pref_mute_bool), false);

              Workout w = Workout.fakeWorkoutForTestingAudioCue();
              RUTextToSpeech rutts = new RUTextToSpeech(tts, mute, requireContext());

              HashMap<String, Object> bindValues = new HashMap<>();
              bindValues.put(Workout.KEY_TTS, rutts);
              bindValues.put(Workout.KEY_FORMATTER, new Formatter(requireContext()));
              bindValues.put(Workout.KEY_HRZONES, new HRZones(requireContext()));
              w.onBind(w, bindValues);
              for (Feedback f : feedback) {
                f.onInit(w);
                f.onBind(w, bindValues);
                f.emit(w, requireContext());
                rutts.emit();
              }
            };

        @Override
        public boolean onPreferenceClick(Preference arg0) {
          Context ctx = requireContext();
          Resources res = getResources();

          feedback.clear();
          SharedPreferences prefs;
          if (settingsName == null || settingsName.contentEquals(DEFAULT))
            prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
          else prefs = ctx.getSharedPreferences(settingsName + SUFFIX, Context.MODE_PRIVATE);

          WorkoutBuilder.addFeedbackFromPreferences(prefs, res, feedback);

          tts = new TextToSpeech(ctx, mTTSOnInitListener);
          return false;
        }
      };
}
