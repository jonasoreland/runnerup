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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.WorkoutFileProvider;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.SyncManager.WorkoutRef;
import org.runnerup.relven.ui.ManageWorkoutActionsStateHolder;
import org.runnerup.relven.ui.ManageWorkoutsScreenCompose;
import org.runnerup.relven.ui.RelvenHomeActivity;
import org.runnerup.relven.ui.WorkoutManagerActionCallbacks;
import org.runnerup.relven.ui.WorkoutManagerEntry;
import org.runnerup.relven.ui.WorkoutManagerScreenStateHolder;
import org.runnerup.relven.ui.WorkoutManagerSelectionCallback;
import org.runnerup.relven.ui.WorkoutManagerSource;
import org.runnerup.util.ProgressDialogCompat;
import org.runnerup.workout.RelvenPlanImport;
import org.runnerup.workout.RelvenPlanImporter;
import org.runnerup.workout.RelvenPlanKit;
import org.runnerup.workout.RelvenPlanStore;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutFileImport;
import org.runnerup.workout.WorkoutImportPreview;
import org.runnerup.workout.WorkoutSerializer;

public class ManageWorkoutsActivity extends AppCompatActivity implements Constants {
  private static final ExecutorService PLAN_IMPORT_EXECUTOR = Executors.newSingleThreadExecutor();

  private SQLiteDatabase mDB = null;

  private String PHONE_STRING = "My phone";
  public static final String WORKOUT_NAME = "";
  public static final String WORKOUT_EDIT_MODE = "workout_edit_mode";

  private final ArrayList<ContentValues> providers = new ArrayList<>();
  private final HashMap<String, ArrayList<WorkoutRef>> workouts = new HashMap<>();

  private WorkoutRef currentlySelectedWorkout = null;
  private ManageWorkoutActionsStateHolder workoutActionsState;
  private WorkoutManagerScreenStateHolder workoutManagerState;

  private SyncManager syncManager = null;
  private ProgressDialogCompat planProgress;

  private final ActivityResultLauncher<Intent> configureLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            syncManager.onActivityResult(
                SyncManager.CONFIGURE_REQUEST, result.getResultCode(), result.getData());
            requery();
          });

  private final ActivityResultLauncher<String[]> importLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) handleImportUri(uri);
          });

  private final ActivityResultLauncher<String[]> planImportLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) importPlan(uri);
          });

  private final ActivityResultLauncher<String> savePlanKitLauncher =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/zip"), this::savePlanKit);

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    Window window = getWindow();
    WindowCompat.getInsetsController(window, window.getDecorView())
        .setAppearanceLightStatusBars(false);
    super.onCreate(savedInstanceState);
    ComposeView workoutManagerView = new ComposeView(this);
    setContentView(workoutManagerView);

    PHONE_STRING = getResources().getString(org.runnerup.common.R.string.my_phone);

    mDB = DBHelper.getReadableDatabase(this);
    syncManager = new SyncManager(this);
    syncManager.setAuthLauncher(configureLauncher);
    workoutActionsState =
        new ManageWorkoutActionsStateHolder(
            new WorkoutManagerActionCallbacks() {
              @Override
              public void importWorkout() {
                importLauncher.launch(new String[] {"*/*"});
              }

              @Override
              public void importPlan() {
                planImportLauncher.launch(
                    new String[] {"application/json", "text/json", "application/*+json", "*/*"});
              }

              @Override
              public void showPlanKit() {
                showPlanKitActions();
              }

              @Override
              public void viewPlans() {
                startActivity(
                    RelvenHomeActivity.openPlanLibraryIntent(ManageWorkoutsActivity.this));
                finish();
              }

              @Override
              public void share() {
                shareButtonClick.onClick(workoutManagerView);
              }

              @Override
              public void edit() {
                editButtonClick.onClick(workoutManagerView);
              }

              @Override
              public void create() {
                createButtonClick.onClick(workoutManagerView);
              }

              @Override
              public void delete() {
                deleteButtonClick.onClick(workoutManagerView);
              }
            });
    workoutManagerState =
        new WorkoutManagerScreenStateHolder(
            (WorkoutManagerSelectionCallback)
                (sourceName, workoutName) -> selectWorkout(sourceName, workoutName));
    ManageWorkoutsScreenCompose.install(
        workoutManagerView, workoutManagerState, workoutActionsState, this::finish);

    handleButtons();

    requery();
    listLocal();

    Uri data = getIntent().getData();
    if (data != null) {
      getIntent().setData(null);
      handleImportUri(data);
    }

    // launch home Activity (with FLAG_ACTIVITY_CLEAR_TOP)
  }

  private void handleImportUri(Uri data) {
    String fileName = getFilename(data);
    if (fileName == null) fileName = "noname";
    fileName = fileName.replace('/', '_').replace('\\', '_').trim();
    if (fileName.isEmpty()) fileName = "noname";
    if (fileName.length() > 128) fileName = fileName.substring(0, 128);
    try {
      importData(fileName, data);
    } catch (Exception e) {
      Log.e(getClass().getName(), "Failed to import workout", e);
      new AlertDialog.Builder(this)
          .setTitle(org.runnerup.common.R.string.Error)
          .setMessage(
              getString(org.runnerup.common.R.string.Failed_to_import)
                  + ": "
                  + fileName
                  + "\n"
                  + e.getMessage())
          .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
          .show();
    }
  }

  private void importPlan(Uri uri) {
    showPlanProgress(org.runnerup.common.R.string.Validating_plan);
    PLAN_IMPORT_EXECUTOR.execute(
        () -> {
          try (InputStream input = getContentResolver().openInputStream(uri)) {
            RelvenPlanImporter.Plan plan =
                RelvenPlanStore.parse(getApplicationContext(), RelvenPlanImport.read(input));
            runOnUiThread(
                () -> {
                  dismissPlanProgress();
                  showPlanPreview(plan);
                });
          } catch (Exception error) {
            Log.e(getClass().getName(), "Failed to read plan package", error);
            runOnUiThread(
                () -> {
                  dismissPlanProgress();
                  showPlanImportError(error);
                });
          }
        });
  }

  private void showPlanProgress(int message) {
    if (isFinishing() || isDestroyed()) return;
    planProgress = new ProgressDialogCompat(this);
    planProgress.setTitle(org.runnerup.common.R.string.Import_plan);
    planProgress.setMessage(getString(message));
    planProgress.setCancelable(false);
    planProgress.show();
  }

  private void dismissPlanProgress() {
    if (planProgress != null && planProgress.isShowing()) planProgress.dismiss();
    planProgress = null;
  }

  private void showPlanPreview(RelvenPlanImporter.Plan plan) {
    if (isFinishing() || isDestroyed()) return;
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle(planDisplayName(plan))
            .setMessage(RelvenPlanImport.preview(plan))
            .setPositiveButton(
                org.runnerup.common.R.string.Save, (ignored, which) -> savePlan(plan))
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (ignored, which) -> ignored.dismiss());
    dialog.show();
  }

  private void showPlanKitActions() {
    String[] actions = {
      getString(org.runnerup.common.R.string.AI_plan_kit_copy_prompt),
      getString(org.runnerup.common.R.string.AI_plan_kit_share),
      getString(org.runnerup.common.R.string.AI_plan_kit_save)
    };
    new AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.AI_plan_kit_title)
        .setItems(
            actions,
            (dialog, which) -> {
              if (which == 0) {
                copyPlanKitInstructions();
              } else if (which == 1) {
                sharePlanKit();
              } else if (which == 2) {
                savePlanKitLauncher.launch("relven-ai-plan-kit-v1.zip");
              }
            })
        .setNegativeButton(org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void copyPlanKitInstructions() {
    PLAN_IMPORT_EXECUTOR.execute(
        () -> {
          try {
            String instructions = RelvenPlanKit.readInstructions(getApplicationContext());
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  ClipboardManager clipboard =
                      (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                  clipboard.setPrimaryClip(
                      ClipData.newPlainText(
                          getString(org.runnerup.common.R.string.AI_plan_kit_title), instructions));
                  Toast.makeText(
                          this,
                          org.runnerup.common.R.string.AI_plan_kit_prompt_copied,
                          Toast.LENGTH_LONG)
                      .show();
                });
          } catch (IOException error) {
            Log.e(getClass().getName(), "Could not read AI Plan Kit instructions", error);
            showPlanKitFeedback(org.runnerup.common.R.string.AI_plan_kit_copy_failed);
          }
        });
  }

  private void sharePlanKit() {
    PLAN_IMPORT_EXECUTOR.execute(
        () -> {
          try {
            File archive = RelvenPlanKit.createShareFile(getApplicationContext());
            runOnUiThread(() -> launchPlanKitShare(archive));
          } catch (IOException error) {
            Log.e(getClass().getName(), "Could not prepare AI Plan Kit for sharing", error);
            showPlanKitFeedback(org.runnerup.common.R.string.AI_plan_kit_share_failed);
          }
        });
  }

  private void launchPlanKitShare(File archive) {
    if (isFinishing() || isDestroyed()) return;
    try {
      Uri uri =
          FileProvider.getUriForFile(
              this, getPackageName() + ".relven.ai.plan.kit.provider", archive);
      Intent share = new Intent(Intent.ACTION_SEND);
      share.setType("application/zip");
      share.putExtra(
          Intent.EXTRA_SUBJECT, getString(org.runnerup.common.R.string.AI_plan_kit_title));
      share.putExtra(Intent.EXTRA_STREAM, uri);
      share.setClipData(
          ClipData.newUri(
              getContentResolver(),
              getString(org.runnerup.common.R.string.AI_plan_kit_title),
              uri));
      share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(
          Intent.createChooser(
              share, getString(org.runnerup.common.R.string.AI_plan_kit_share_chooser)));
    } catch (RuntimeException error) {
      Log.e(getClass().getName(), "Could not open AI Plan Kit share chooser", error);
      Toast.makeText(this, org.runnerup.common.R.string.AI_plan_kit_share_failed, Toast.LENGTH_LONG)
          .show();
    }
  }

  private void savePlanKit(Uri destination) {
    if (destination == null) return;
    PLAN_IMPORT_EXECUTOR.execute(
        () -> {
          try {
            OutputStream output = getContentResolver().openOutputStream(destination, "w");
            if (output == null) throw new IOException("Document destination is unavailable");
            try (OutputStream stream = output) {
              RelvenPlanKit.copyArchive(getApplicationContext(), stream);
            }
            showPlanKitFeedback(org.runnerup.common.R.string.AI_plan_kit_saved);
          } catch (Exception error) {
            Log.e(getClass().getName(), "Could not save AI Plan Kit", error);
            showPlanKitFeedback(org.runnerup.common.R.string.AI_plan_kit_save_failed);
          }
        });
  }

  private void showPlanKitFeedback(int message) {
    runOnUiThread(
        () -> {
          if (isFinishing() || isDestroyed()) return;
          Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
  }

  private String planDisplayName(RelvenPlanImporter.Plan plan) {
    if (plan.revisionNumber() == 0) return plan.name();
    return getString(
        org.runnerup.common.R.string.Plan_revision_named, plan.name(), plan.revisionNumber());
  }

  private void savePlan(RelvenPlanImporter.Plan plan) {
    showPlanProgress(org.runnerup.common.R.string.Saving_plan);
    PLAN_IMPORT_EXECUTOR.execute(
        () -> {
          try {
            RelvenPlanStore.save(getApplicationContext(), plan);
            runOnUiThread(
                () -> {
                  dismissPlanProgress();
                  Toast.makeText(
                          getApplicationContext(),
                          org.runnerup.common.R.string.Plan_saved,
                          Toast.LENGTH_LONG)
                      .show();
                });
          } catch (Exception error) {
            Log.e(getClass().getName(), "Failed to save plan package", error);
            runOnUiThread(
                () -> {
                  dismissPlanProgress();
                  showPlanImportError(error);
                });
          }
        });
  }

  private void showPlanImportError(Exception error) {
    if (isFinishing() || isDestroyed()) return;
    new AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Error)
        .setMessage(
            getString(org.runnerup.common.R.string.Failed_to_import) + ": " + error.getMessage())
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private String getFilename(Uri data) {
    Log.i(getClass().getName(), "scheme: " + data.toString());
    String name = null;
    if (ContentResolver.SCHEME_FILE.contentEquals(data.getScheme())) {
      name = data.getLastPathSegment();
    } else if (ContentResolver.SCHEME_CONTENT.contentEquals(data.getScheme())) {
      String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
      Cursor c = getContentResolver().query(data, projection, null, null, null);
      if (c != null && c.moveToFirst()) {
        final int fileNameColumnId = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
        if (fileNameColumnId >= 0) name = c.getString(fileNameColumnId);
      }
      if (c != null) c.close();
    }
    return name;
  }

  private void importData(String fileName, final Uri data) throws Exception {
    final ContentResolver cr = getContentResolver();
    WorkoutFileImport.Imported imported =
        WorkoutFileImport.read(fileName, cr.openInputStream(data));
    boolean fit = imported.format() == WorkoutFileImport.Format.FIT;
    boolean relven = imported.format() == WorkoutFileImport.Format.RELVEN;
    Workout w = imported.workout();

    if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".fit")) {
      fileName = fileName.substring(0, fileName.length() - 4);
    } else if (fileName.endsWith(".json")) {
      fileName = fileName.substring(0, fileName.length() - ".json".length());
    }

    final String prefix = getString(org.runnerup.common.R.string.RunnerUp_workout) + ": ";
    if (fileName.startsWith(prefix) && fileName.length() > prefix.length()) {
      fileName = fileName.substring(prefix.length());
    }

    final boolean exists = WorkoutSerializer.getFile(this, fileName).exists();
    final boolean[] selected = {false};

    final String workoutName = fileName;
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(getString(org.runnerup.common.R.string.Import_workout) + ": " + workoutName)
            .setMessage(WorkoutImportPreview.format(this, w))
            .setPositiveButton(
                org.runnerup.common.R.string.Yes,
                (dialog, which) -> {
                  dialog.dismiss();
                  String saveName = workoutName;
                  try {
                    if (exists && !selected[0]) {
                      for (int i = 1; i < 25; i++) {
                        saveName = workoutName + "-" + i;
                        if (!WorkoutSerializer.getFile(ManageWorkoutsActivity.this, saveName)
                            .exists()) break;
                      }
                      Toast.makeText(
                              ManageWorkoutsActivity.this,
                              getString(org.runnerup.common.R.string.Saving_as) + " " + saveName,
                              Toast.LENGTH_SHORT)
                          .show();
                    }
                    if (fit || relven) {
                      WorkoutSerializer.writeFile(ManageWorkoutsActivity.this, saveName, w);
                    } else {
                      saveImport(saveName, cr.openInputStream(data));
                    }
                    launchMain(saveName);
                  } catch (Exception e) {
                    Log.e(getClass().getName(), "Failed to save imported workout", e);
                    Toast.makeText(
                            ManageWorkoutsActivity.this,
                            org.runnerup.common.R.string.Failed_to_import,
                            Toast.LENGTH_LONG)
                        .show();
                  }
                })
            .setNegativeButton(
                org.runnerup.common.R.string.No,
                (dialog, which) -> {
                  // Do nothing but close the dialog
                  dialog.dismiss();
                  finish();
                });

    if (exists) {
      String[] items = {getString(org.runnerup.common.R.string.Overwrite_existing)};
      builder.setMultiChoiceItems(items, selected, (arg0, arg1, arg2) -> selected[arg1] = arg2);
    }

    builder.show();
  }

  private void saveImport(String file, InputStream is) throws IOException {
    File f = WorkoutSerializer.getFile(this, file);
    BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
    BufferedInputStream in = new BufferedInputStream(is);
    byte[] buf = new byte[1024];
    int bytesRead;
    while ((bytesRead = in.read(buf)) > 0) {
      out.write(buf, 0, bytesRead);
    }
    in.close();
    out.close();
  }

  private void launchMain(String fileName) {
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    pref.edit()
        .putString(getResources().getString(R.string.pref_advanced_workout), fileName)
        .apply();

    Intent intent =
        new Intent(this, MainLayout.class)
            .putExtra("mode", StartFragment.TAB_ADVANCED)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();

    listLocal();
  }

  private void handleButtons() {
    boolean canModifySelected = false;
    if (currentlySelectedWorkout != null) {
      canModifySelected = PHONE_STRING.contentEquals(currentlySelectedWorkout.synchronizer());
    }
    workoutActionsState.update(canModifySelected);
    refreshWorkoutManagerScreen();
  }

  private void listLocal() {
    ArrayList<WorkoutRef> newlist = new ArrayList<>();
    String[] list = WorkoutListAdapter.load(this);
    if (list != null) {
      for (String s : list) {
        newlist.add(new WorkoutRef(PHONE_STRING, null, s.substring(0, s.lastIndexOf('.'))));
      }
    }

    workouts.remove(PHONE_STRING);
    workouts.put(PHONE_STRING, newlist);
    if (currentlySelectedWorkout != null) {
      boolean stillExists =
          newlist.stream()
              .anyMatch(
                  workout -> workout.workoutName().equals(currentlySelectedWorkout.workoutName()));
      if (!stillExists) currentlySelectedWorkout = null;
    }
    handleButtons();
  }

  @Override
  public void onDestroy() {
    dismissPlanProgress();
    super.onDestroy();
    DBHelper.closeDB(mDB);
    syncManager.close();
  }

  private void requery() {
    // ContentValues[] allSynchronizers;
    try {
      /*
       * Accounts/reports
       */
      String sql =
          "SELECT DISTINCT "
              + "  acc._id, " // 0
              + ("  acc." + DB.ACCOUNT.NAME + ", ")
              + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
              + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
              + ("  acc." + DB.ACCOUNT.ENABLED + " ")
              + (" FROM " + DB.ACCOUNT.TABLE + " acc ");

      Cursor c = mDB.rawQuery(sql, null);
      // allSynchronizers = DBHelper.toArray(c);
      c.close();
    } catch (IllegalStateException e) {
      Log.e(getClass().getName(), "requery: " + e.getMessage());
      return;
    }

    providers.clear();

    ContentValues phone = new ContentValues();
    phone.put(DB.ACCOUNT.NAME, PHONE_STRING);
    providers.add(phone);

    // Only local phone provider is currently supported (Garmin was a remote provider a long time
    // ago)
    // Comment out the handling to avoid accidental activations
    //        for (ContentValues tmp : allSynchronizers) {
    //            Synchronizer synchronizer = syncManager.add(tmp);
    //            //There is no option to show disabled providers, so check for enable or configured
    //            if (synchronizer != null &&
    // synchronizer.checkSupport(Synchronizer.Feature.WORKOUT_LIST) &&
    //                    (synchronizer.isConfigured() || tmp.getAsInteger(DB.ACCOUNT.ENABLED) ==
    // 1)) {
    //                providers.add(tmp);
    //
    //                workouts.remove(synchronizer.getName());
    //                workouts.put(synchronizer.getName(), new ArrayList<>());
    //            }
    //        }

    refreshWorkoutManagerScreen();
  }

  private void refreshWorkoutManagerScreen() {
    ArrayList<WorkoutManagerSource> sources = new ArrayList<>();
    for (ContentValues provider : providers) {
      String sourceName = provider.getAsString(DB.ACCOUNT.NAME);
      ArrayList<WorkoutRef> sourceWorkouts = workouts.get(sourceName);
      if (sourceWorkouts == null) continue;
      ArrayList<WorkoutManagerEntry> entries = new ArrayList<>();
      for (WorkoutRef workout : sourceWorkouts) {
        entries.add(new WorkoutManagerEntry(workout.workoutName()));
      }
      sources.add(new WorkoutManagerSource(sourceName, entries));
    }
    String selectedKey =
        currentlySelectedWorkout == null
            ? null
            : org.runnerup.relven.ui.WorkoutManagerScreenStateKt.workoutManagerSelectionKey(
                currentlySelectedWorkout.synchronizer(), currentlySelectedWorkout.workoutName());
    workoutManagerState.update(sources, selectedKey);
  }

  private void selectWorkout(String sourceName, String workoutName) {
    currentlySelectedWorkout = null;
    if (sourceName != null && workoutName != null) {
      ArrayList<WorkoutRef> sourceWorkouts = workouts.get(sourceName);
      if (sourceWorkouts != null) {
        for (WorkoutRef workout : sourceWorkouts) {
          if (workout.workoutName().equals(workoutName)) {
            currentlySelectedWorkout = workout;
            break;
          }
        }
      }
    }
    handleButtons();
  }

  private final OnClickListener createButtonClick =
      view -> {
        final Intent intent = new Intent(ManageWorkoutsActivity.this, CreateAdvancedWorkout.class);
        // Set an EditText view to get user input
        final EditText input = new EditText(ManageWorkoutsActivity.this);

        new AlertDialog.Builder(ManageWorkoutsActivity.this)
            .setTitle(org.runnerup.common.R.string.Create_new_workout)
            .setMessage(org.runnerup.common.R.string.Set_workout_name)
            .setView(input)
            .setPositiveButton(
                org.runnerup.common.R.string.OK,
                (dialog, whichButton) -> {
                  String value = input.getText().toString().trim();
                  if (value.isEmpty()
                      || value.contains("/")
                      || value.contains("\\")
                      || value.contains("..")) {
                    Toast.makeText(
                            ManageWorkoutsActivity.this,
                            org.runnerup.common.R.string.Invalid_workout_name,
                            Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                  File f = WorkoutSerializer.getFile(getApplicationContext(), value);
                  if (f.exists()) {
                    Toast.makeText(
                            ManageWorkoutsActivity.this,
                            org.runnerup.common.R.string.Workout_name_already_in_use,
                            Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                  intent.putExtra(WORKOUT_NAME, value);
                  intent.putExtra(WORKOUT_EDIT_MODE, false);
                  startActivity(intent);
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
            .show();
      };

  private final OnClickListener deleteButtonClick =
      v -> {
        if (currentlySelectedWorkout == null) return;

        final WorkoutRef selected = currentlySelectedWorkout;
        new AlertDialog.Builder(ManageWorkoutsActivity.this)
            .setTitle(
                getString(org.runnerup.common.R.string.Delete_workout)
                    + " "
                    + selected.workoutName())
            .setMessage(org.runnerup.common.R.string.Are_you_sure)
            .setPositiveButton(
                org.runnerup.common.R.string.Yes,
                (dialog, which) -> {
                  dialog.dismiss();
                  deleteWorkout(selected);
                })
            .setNegativeButton(
                org.runnerup.common.R.string.No,
                // Do nothing but close the dialog
                (dialog, which) -> dialog.dismiss())
            .show();
      };

  private void deleteWorkout(WorkoutRef selected) {
    File f = WorkoutSerializer.getFile(this, selected.workoutName());
    //noinspection ResultOfMethodCallIgnored
    f.delete();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    if (selected
        .workoutName()
        .contentEquals(
            pref.getString(getResources().getString(R.string.pref_advanced_workout), ""))) {
      pref.edit().putString(getResources().getString(R.string.pref_advanced_workout), "").apply();
    }
    currentlySelectedWorkout = null;
    listLocal();
  }

  private final OnClickListener shareButtonClick =
      v -> {
        if (currentlySelectedWorkout == null) return;

        final AppCompatActivity context = ManageWorkoutsActivity.this;
        final WorkoutRef selected = currentlySelectedWorkout;
        final String name = selected.workoutName();
        final Intent intent = new Intent(Intent.ACTION_SEND);

        intent.putExtra(
            Intent.EXTRA_SUBJECT,
            getString(org.runnerup.common.R.string.RunnerUp_workout) + ": " + name);
        intent.putExtra(
            Intent.EXTRA_TEXT,
            getString(org.runnerup.common.R.string.HinHere_is_a_workout_I_think_you_might_like));

        intent.setType(WorkoutFileProvider.MIME);
        Uri uri = Uri.parse("content://" + WorkoutFileProvider.AUTHORITY + "/" + name + ".json");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        context.startActivity(
            Intent.createChooser(intent, getString(org.runnerup.common.R.string.Share_workout)));
      };

  private final OnClickListener editButtonClick =
      v -> {
        if (currentlySelectedWorkout == null) return;

        final WorkoutRef selected = currentlySelectedWorkout;
        final Intent intent = new Intent(ManageWorkoutsActivity.this, CreateAdvancedWorkout.class);

        intent.putExtra(WORKOUT_NAME, selected.workoutName());
        intent.putExtra(WORKOUT_EDIT_MODE, true);
        startActivity(intent);
      };

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == SyncManager.CONFIGURE_REQUEST) {
      syncManager.onActivityResult(requestCode, resultCode, data);
    }
    requery();
  }
}
