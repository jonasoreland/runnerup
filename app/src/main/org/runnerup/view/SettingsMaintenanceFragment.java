package org.runnerup.view;

import android.app.ProgressDialog;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.db.DBHelper;

public class SettingsMaintenanceFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_maintenance, rootKey);
    Resources res = getResources();
    {
      Preference btn = findPreference(res.getString(R.string.pref_exportdb));
      btn.setOnPreferenceClickListener(onExportClick);
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_importdb));
      btn.setOnPreferenceClickListener(onImportClick);
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_prunedb));
      btn.setOnPreferenceClickListener(onPruneClick);
    }

    String path = DBHelper.getDefaultBackupPath(requireContext());
    findPreference(res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary))
        .setSummary(
            String.format(
                Locale.getDefault(),
                res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary),
                path));
  }

  private final Preference.OnPreferenceClickListener onExportClick =
      preference -> {
        // TODO Use picker with ACTION_CREATE_DOCUMENT
        DBHelper.exportDatabase(requireContext(), null);
        return false;
      };

  private final Preference.OnPreferenceClickListener onImportClick =
      preference -> {
        // TODO Use picker with ACTION_OPEN_DOCUMENT
        DBHelper.importDatabase(requireContext(), null);
        return false;
      };

  private final Preference.OnPreferenceClickListener onPruneClick =
      preference -> {
        final ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setTitle(org.runnerup.common.R.string.Pruning_deleted_activities_from_database);
        dialog.show();
        DBHelper.purgeDeletedActivities(requireContext(), dialog, dialog::dismiss);
        return false;
      };
}
