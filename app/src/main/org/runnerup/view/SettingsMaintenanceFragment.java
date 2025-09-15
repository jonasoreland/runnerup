package org.runnerup.view;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.db.DBHelper;

public class SettingsMaintenanceFragment extends PreferenceFragmentCompat {

  private static final String TAG = "SettingsMaintenance";

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

  /**
   * ActivityResultLauncher for handling the result of the {@link Intent#ACTION_CREATE_DOCUMENT}
   * intent used for exporting the database.
   *
   * <p>When the user selects a destination file, this launcher receives the {@link Uri} and
   * initiates the database export process via {@link DBHelper#exportDatabase(Context, Uri)}. If the
   * export is cancelled or no Uri is returned, a toast message is shown and a warning is logged.
   */
  private final ActivityResultLauncher<Intent> exportDbLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Uri toUri = getUriFromResult(result);
            if (toUri != null) {
              DBHelper.exportDatabase(requireContext(), toUri);
            } else {
              Toast.makeText(
                      requireContext(),
                      org.runnerup.common.R.string.export_cancelled,
                      Toast.LENGTH_SHORT)
                  .show();
              Log.w(TAG, "Export cancelled or URI not found.");
            }
          });

  /**
   * ActivityResultLauncher for handling the result of the {@link Intent#ACTION_OPEN_DOCUMENT}
   * intent used for importing the database.
   *
   * <p>When the user selects a database file, this launcher receives its {@link Uri} and initiates
   * the database import process via {@link DBHelper#importDatabase(Context, Uri)}. If the import is
   * cancelled or no Uri is returned, a toast message is shown and a warning is logged.
   */
  private final ActivityResultLauncher<Intent> importDbLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Uri fromUri = getUriFromResult(result);
            if (fromUri != null) {
              DBHelper.importDatabase(requireContext(), fromUri);
            } else {
              Toast.makeText(
                      requireContext(),
                      org.runnerup.common.R.string.import_cancelled,
                      Toast.LENGTH_SHORT)
                  .show();
              Log.w(TAG, "Import cancelled or URI not found.");
            }
          });

  private final Preference.OnPreferenceClickListener onExportClick =
      preference -> {
        Intent intent =
            new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // Use "application/octet-stream" to be consistent with the mime type used in
                // the http intent-filter for MainLayout (in AndroidManifest).
                .setType("application/octet-stream")
                .putExtra(
                    Intent.EXTRA_TITLE,
                    "runnerup.db.export"); // Suggest a name (note: user may change it)
        exportDbLauncher.launch(intent);
        return true;
      };

  private final Preference.OnPreferenceClickListener onImportClick =
      preference -> {
        Intent intent =
            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        importDbLauncher.launch(intent);
        return true;
      };

  private final Preference.OnPreferenceClickListener onPruneClick =
      preference -> {
        final ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setTitle(org.runnerup.common.R.string.Pruning_deleted_activities_from_database);
        dialog.show();
        DBHelper.purgeDeletedActivities(requireContext(), dialog, dialog::dismiss);
        return false;
      };

  /**
   * Helper method to check the result of an ActivityResult and extract the Uri.
   *
   * @param result The ActivityResult from the launcher.
   * @return The Uri if the result was OK and data is present, otherwise null.
   */
  @Nullable
  private Uri getUriFromResult(ActivityResult result) {
    if (result.getResultCode() == Activity.RESULT_OK) {
      Intent data = result.getData();
      if (data != null && data.getData() != null) {
        return data.getData();
      }
    }
    return null;
  }
}
