package org.runnerup.view;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

public class SettingsUnitsFragment extends PreferenceFragmentCompat
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private Preference oppositeSpeedUnitSportsPreference;
  private String[] sportEntries;

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_units, rootKey);

    sportEntries = Sport.getStringArray(getResources());
    oppositeSpeedUnitSportsPreference =
        findPreference(getString(R.string.pref_speedunit_opposite_sports));
    if (oppositeSpeedUnitSportsPreference == null) {
      return;
    }

    oppositeSpeedUnitSportsPreference.setOnPreferenceClickListener(
        preference -> {
          showOppositeSportDialog();
          return true;
        });
    updateOppositeSportsSummary();
  }

  @Override
  public void onResume() {
    super.onResume();
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    updateOppositeSportsSummary();
  }

  @Override
  public void onPause() {
    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key != null && key.contentEquals(getString(R.string.pref_speedunit_opposite_sports))) {
      updateOppositeSportsSummary();
    }
  }

  private void showOppositeSportDialog() {
    final boolean[] checked = createCheckedSports();

    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.speed_unit_opposite_activities_title)
        .setMultiChoiceItems(
            sportEntries,
            checked,
            (dialog, which, isChecked) -> {
              if (0 <= which && which < checked.length) {
                checked[which] = isChecked;
              }
            })
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              Formatter.setOppositeSpeedUnitSports(requireContext(), getCheckedSportIds(checked));
              updateOppositeSportsSummary();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private boolean[] createCheckedSports() {
    boolean[] checked = new boolean[Math.min(sportEntries.length, DB.ACTIVITY.SPORT_MAX + 1)];
    for (int sportId : Formatter.getOppositeSpeedUnitSports(requireContext())) {
      if (0 <= sportId && sportId < checked.length) {
        checked[sportId] = true;
      }
    }
    return checked;
  }

  private int[] getCheckedSportIds(boolean[] checked) {
    int count = 0;
    for (boolean isChecked : checked) {
      if (isChecked) {
        count++;
      }
    }

    int[] selectedSports = new int[count];
    int index = 0;
    for (int sportId = 0; sportId < checked.length; sportId++) {
      if (checked[sportId]) {
        selectedSports[index++] = sportId;
      }
    }
    return selectedSports;
  }

  private void updateOppositeSportsSummary() {
    if (oppositeSpeedUnitSportsPreference == null) {
      return;
    }

    int[] selectedSports = Formatter.getOppositeSpeedUnitSports(requireContext());
    if (selectedSports.length == 0) {
      oppositeSpeedUnitSportsPreference.setSummary(
          R.string.speed_unit_opposite_activities_summary_empty);
      return;
    }

    StringBuilder summary = new StringBuilder();
    for (int sportId : selectedSports) {
      if (0 > sportId || sportId >= sportEntries.length) {
        continue;
      }
      if (summary.length() > 0) {
        summary.append(", ");
      }
      summary.append(sportEntries[sportId]);
    }
    oppositeSpeedUnitSportsPreference.setSummary(summary);
  }
}
