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

package org.runnerup.export;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.LongSparseArray;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.Synchronizer.AuthMethod;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Encryption;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.WorkoutSerializer;

public class SyncManager {
  // Used by several activities
  public static final int CONFIGURE_REQUEST = 1;
  public static final long ERROR_ACTIVITY_ID = -1L;
  // Id to identify a permission request.
  private static final int REQUEST_STORAGE = 3003;
  private final Map<String, Synchronizer> synchronizers = new HashMap<>();
  private final LongSparseArray<Synchronizer> synchronizersById = new LongSparseArray<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  PathSimplifier simplifier;
  private SQLiteDatabase mDB = null;
  private AppCompatActivity mActivity = null;
  private Context mContext = null;
  private ProgressDialog mSpinner = null;
  private Synchronizer authSynchronizer = null;
  private Callback authCallback = null;
  private long mID = 0;
  private Callback uploadCallback = null;
  private HashSet<String> pendingSynchronizers = null;
  private final Callback disableSynchronizerCallback =
      (synchronizerName, status) -> nextSynchronizer();
  private Callback listWorkoutCallback = null;
  private HashSet<String> pendingListWorkout = null;
  private ArrayList<WorkoutRef> workoutRef = null;

  /** Synch set of activities for a specific synchronizer */
  private List<SyncActivityItem> syncActivitiesList = null;

  private Callback syncActivityCallback = null;
  private StringBuffer cancelSync = null;

  public SyncManager(AppCompatActivity activity) {
    init(activity, activity, new ProgressDialog(activity));
  }

  public SyncManager(Context context) {
    init(null, context, new ProgressDialog(context));
  }

  public SyncManager(Context context, ProgressDialog spinner) {
    init(null, context, spinner);
  }

  private static void externalIdCompleted(
      Synchronizer synchronizer, SQLiteDatabase copyDB, Synchronizer.Status status) {
    ContentValues tmp = new ContentValues();
    tmp.put(DB.EXPORT.STATUS, status.externalIdStatus.getInt());
    tmp.put(DB.EXPORT.EXTERNAL_ID, status.externalId);
    String[] args = {Long.toString(synchronizer.getId()), Long.toString(status.activityId)};
    copyDB.update(
        DB.EXPORT.TABLE, tmp, DB.EXPORT.ACCOUNT + "= ? AND " + DB.EXPORT.ACTIVITY + " = ?", args);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean compareFiles(File w, File f) {
    if (w.length() != f.length()) return false;

    boolean cmp = true;
    FileInputStream f1 = null;
    FileInputStream f2 = null;
    try {
      f1 = new FileInputStream(w);
      f2 = new FileInputStream(f);
      byte[] buf1 = new byte[1024];
      byte[] buf2 = new byte[1024];
      do {
        int cnt1 = f1.read(buf1);
        int cnt2 = f2.read(buf2);
        if (cnt1 <= 0 || cnt2 <= 0) break;

        if (!java.util.Arrays.equals(buf1, buf2)) {
          cmp = false;
          break;
        }
      } while (true);
    } catch (Exception ex) {
      // f1, f2 checked
    }

    if (f1 != null) {
      try {
        f1.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (f2 != null) {
      try {
        f2.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return cmp;
  }

  private void init(AppCompatActivity activity, Context context, ProgressDialog spinner) {
    this.mActivity = activity;
    this.mContext = context;
    mDB = DBHelper.getWritableDatabase(context);
    mSpinner = spinner;
    mSpinner.setCancelable(false);
    simplifier = PathSimplifier.getPathSimplifierForExport(context);
  }

  public synchronized void close() {
    executor.shutdown();
    if (mDB != null) {
      DBHelper.closeDB(mDB);
    }
  }

  public void clear() {
    synchronizers.clear();
    synchronizersById.clear();
  }

  @SuppressWarnings("UnusedReturnValue")
  public long load(String synchronizerName) {
    String[] from =
        new String[] {
          "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FORMAT, DB.ACCOUNT.FLAGS
        };
    String[] args = {synchronizerName};
    Cursor c =
        mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args, null, null, null, null);
    long id = -1;
    if (c.moveToFirst()) {
      ContentValues config = DBHelper.get(c);
      //noinspection ConstantConditions
      id = config.getAsLong("_id");
      add(config);
    }
    c.close();
    return id;
  }

  @SuppressWarnings("null")
  public Synchronizer add(ContentValues config) {
    if (config == null) {
      Log.w(getClass().getName(), "Add null!");
      if (BuildConfig.DEBUG) {
        throw new AssertionError();
      }
      return null;
    }

    String synchronizerName = config.getAsString(DB.ACCOUNT.NAME);
    if (synchronizerName == null) {
      Log.w(getClass().getName(), "name not found!");
      return null;
    }
    if (synchronizers.containsKey(synchronizerName)) {
      return synchronizers.get(synchronizerName);
    }

    Synchronizer synchronizer = null;
    if (synchronizerName.contentEquals(RunKeeperSynchronizer.NAME)) {
      synchronizer = new RunKeeperSynchronizer(this, simplifier);
    } else if (synchronizerName.contentEquals(RunningAHEADSynchronizer.NAME)) {
      synchronizer = new RunningAHEADSynchronizer(this, simplifier);
    } else if (synchronizerName.contentEquals(RunnerUpLiveSynchronizer.NAME)) {
      synchronizer = new RunnerUpLiveSynchronizer(mContext);
    } else if (synchronizerName.contentEquals(StravaSynchronizer.NAME)) {
      synchronizer = new StravaSynchronizer(this, simplifier);
    } else if (synchronizerName.contentEquals(FileSynchronizer.NAME)) {
      synchronizer = new FileSynchronizer(mContext, simplifier);
    } else if (synchronizerName.contentEquals(RunalyzeSynchronizer.NAME)) {
      synchronizer = new RunalyzeSynchronizer(simplifier);
    } else if (synchronizerName.contentEquals(DropboxSynchronizer.NAME)) {
      synchronizer = new DropboxSynchronizer(mContext, simplifier);
    } else if (synchronizerName.contentEquals(WebDavSynchronizer.NAME)) {
      synchronizer = new WebDavSynchronizer(simplifier);
    } else if (synchronizerName.contentEquals(EndurainSynchronizer.NAME)) {
      synchronizer = new EndurainSynchronizer(simplifier);
    } else {
      Log.w(getClass().getName(), "synchronizer does not exist: " + synchronizerName);
    }

    if (synchronizer != null) {
      if (!config.containsKey(DB.ACCOUNT.FLAGS)) {
        if (BuildConfig.DEBUG) {
          throw new AssertionError();
        }
      }
      synchronizer.init(config);
      synchronizers.put(synchronizerName, synchronizer);
      synchronizersById.put(synchronizer.getId(), synchronizer);
    } else {
      Log.w(getClass().getName(), "Synchronizer not found for " + synchronizerName);
      try {
        long synchronizerId = Long.parseLong(config.getAsString(DB.PRIMARY_KEY));
        DBHelper.deleteAccount(mDB, synchronizerId);
      } catch (Exception ex) {
        Log.e(getClass().getName(), "Failed to deleted deprecated synchronizer", ex);
      }
    }
    return synchronizer;
  }

  public boolean isConfigured(final String name) {
    Synchronizer l = synchronizers.get(name);
    return l != null && l.isConfigured();
  }

  public Synchronizer getSynchronizer(long id) {
    return synchronizersById.get(id);
  }

  public Synchronizer getSynchronizerByName(String name) {
    return synchronizers.get(name);
  }

  public void connect(final Callback callback, final String name) {
    Synchronizer synchronizer = synchronizers.get(name);
    if (synchronizer == null) {
      callback.run(name, Synchronizer.Status.INCORRECT_USAGE);
      return;
    }
    Status s = synchronizer.connect();
    if (s == Synchronizer.Status.NEED_REFRESH) {
      s = handleRefreshComplete(synchronizer, synchronizer.refreshToken());
    }
    switch (s) {
      case OK:
        callback.run(name, s);
        return;

      case NEED_AUTH:
        mSpinner.show();
        handleAuth(
            (synchronizerName, status) -> {
              mSpinner.dismiss();
              callback.run(synchronizerName, status);
            },
            synchronizer,
            s.authMethod);
        return;

      default:
        synchronizer.reset();
        callback.run(name, s);
    }
  }

  private Status handleRefreshComplete(final Synchronizer synchronizer, final Status s) {
    if (s == Status.OK) {
      ContentValues tmp = new ContentValues();
      tmp.put("_id", synchronizer.getId());
      tmp.put(DB.ACCOUNT.AUTH_CONFIG, synchronizer.getAuthConfig());
      String[] args = {Long.toString(synchronizer.getId())};
      mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
    }
    // case NEED_AUTH: Handle by caller
    // default: No reset, refresh_token should still be valid

    return s;
  }

  private void handleAuth(Callback callback, final Synchronizer l, AuthMethod authMethod) {
    authSynchronizer = l;
    authCallback = callback;
    switch (authMethod) {
      case OAUTH2:
        mActivity.startActivityForResult(l.getAuthIntent(mActivity), CONFIGURE_REQUEST);
        return;
      case USER_PASS:
      case USER_PASS_URL:
        askUsernamePassword(l, authMethod);
        return;
      case FILEPERMISSION:
        checkStoragePermissions(mActivity);
        askFileUrl(l);
    }
  }

  private void handleAuthComplete(Synchronizer synchronizer, Status s) {
    Callback cb = authCallback;
    authCallback = null;
    authSynchronizer = null;
    if (s == Status.OK) {
      ContentValues tmp = new ContentValues();
      tmp.put("_id", synchronizer.getId());
      tmp.put(DB.ACCOUNT.AUTH_CONFIG, synchronizer.getAuthConfig());

      String[] args = {Long.toString(synchronizer.getId())};
      try {
        mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
      } catch (IllegalStateException ex) {
        Log.e(getClass().getName(), "Update failed:", ex);
        s = Status.ERROR;
      }
    }
    if (s != Status.OK) {
      synchronizer.reset();
    }
    cb.run(synchronizer.getName(), s);
  }

  private JSONObject newObj(String str) {
    try {
      return new JSONObject(str);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void askUsernamePassword(final Synchronizer sync, final AuthMethod authMethod) {
    final View view = View.inflate(mActivity, R.layout.userpass, null);
    final CheckBox cb = view.findViewById(R.id.showpass);
    final TextView tv1 = view.findViewById(R.id.username);
    final TextView tv2 = view.findViewById(R.id.password_input);
    final TextView urlInput = view.findViewById(R.id.url_input);
    final TextView tvAuthNotice = view.findViewById(R.id.userpass_textViewAuthNotice);
    final TableRow rowUrl = view.findViewById(R.id.table_row_url);
    String authConfigStr = sync.getAuthConfig();
    final JSONObject authConfig = newObj(authConfigStr);
    String username = authConfig != null ? authConfig.optString("username", "") : null;
    String password = authConfig != null ? authConfig.optString("password", "") : null;
    tv1.setText(username);
    tv2.setText(password);
    cb.setOnCheckedChangeListener(
        (buttonView, isChecked) ->
            tv2.setInputType(
                InputType.TYPE_CLASS_TEXT
                    | (isChecked ? 0 : InputType.TYPE_TEXT_VARIATION_PASSWORD)));
    if (sync.getAuthNotice() != 0) {
      tvAuthNotice.setVisibility(View.VISIBLE);
      tvAuthNotice.setText(sync.getAuthNotice());
    } else {
      tvAuthNotice.setVisibility(View.GONE);
    }
    if (AuthMethod.USER_PASS_URL.equals(authMethod)) {
      rowUrl.setVisibility(View.VISIBLE);
      String url = authConfig.optString(DB.ACCOUNT.URL);
      if (url == null || url.isEmpty()) {
        url = sync.getPublicUrl();
      }
      urlInput.setText(url);
    } else {
      rowUrl.setVisibility(View.GONE);
    }

    new AlertDialog.Builder(mActivity)
        .setTitle(sync.getName())

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        .setView(view)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              try {
                //noinspection ConstantConditions
                authConfig.put("username", tv1.getText());
                authConfig.put("password", tv2.getText());
                if (authMethod == AuthMethod.USER_PASS_URL) {
                  authConfig.put(DB.ACCOUNT.URL, urlInput.getText());
                }
              } catch (JSONException e) {
                e.printStackTrace();
              }
              testUserPass(sync, authConfig);
            })
        .setNeutralButton("Skip", (dialog, which) -> handleAuthComplete(sync, Status.SKIP))
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            (dialog, which) -> handleAuthComplete(sync, Status.SKIP))
        .setOnKeyListener(
            (dialogInterface, i, keyEvent) -> {
              if (i == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                handleAuthComplete(sync, Status.CANCEL);
              }
              return false;
            })
        .show();
  }

  private void testUserPass(final Synchronizer l, final JSONObject authConfig) {
    mSpinner.setTitle("Testing login " + l.getName());

    executor.execute(
        () -> {
          final ContentValues config = new ContentValues();
          config.put(DB.ACCOUNT.AUTH_CONFIG, authConfig.toString());
          config.put("_id", l.getId());
          l.init(config);

          Status result;
          try {
            result = l.connect();
          } catch (Exception ex) {
            Log.e(getClass().getName(), "Connection test failed", ex);
            result = Synchronizer.Status.ERROR;
          }

          final Status finalResult = result;

          mainHandler.post(
              () -> {
                handleAuthComplete(l, finalResult);
              });
        });
  }

  private void askFileUrl(final Synchronizer sync) {
    final View view = View.inflate(mActivity, R.layout.filepermission, null);
    final TextView tv1 = view.findViewById(R.id.fileuri);
    final TextView tvAuthNotice = view.findViewById(R.id.filepermission_textViewAuthNotice);

    String path;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // All paths are related to Environment.DIRECTORY_DOCUMENTS
      path = "";
    } else {
      path =
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath()
              + File.separator;
    }
    path += "RunnerUp";
    tv1.setText(path);

    if (sync.getAuthNotice() != 0) {
      tvAuthNotice.setVisibility(View.VISIBLE);
      tvAuthNotice.setText(sync.getAuthNotice());
    } else {
      tvAuthNotice.setVisibility(View.GONE);
    }

    AlertDialog.Builder builder =
        new AlertDialog.Builder(mActivity)
            .setTitle(sync.getName())

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            .setView(view)
            .setPositiveButton(
                org.runnerup.common.R.string.OK,
                (dialog, which) -> {
                  // Set default values
                  ContentValues tmp = new ContentValues();
                  String uri = tv1.getText().toString().trim();
                  while (uri.endsWith(File.separator)) {
                    uri = uri.substring(0, uri.length() - 1);
                  }
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    while (uri.startsWith(File.separator)) {
                      uri = uri.substring(1);
                    }
                  }
                  tmp.put(DB.ACCOUNT.URL, uri);
                  ContentValues config = new ContentValues();
                  config.put("_id", sync.getId());
                  config.put(
                      DB.ACCOUNT.AUTH_CONFIG, FileSynchronizer.contentValuesToAuthConfig(tmp));
                  sync.init(config);

                  handleAuthComplete(sync, sync.connect());
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel,
                (dialog, which) -> handleAuthComplete(sync, Status.SKIP))
            .setOnKeyListener(
                (dialogInterface, i, keyEvent) -> {
                  if (i == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    handleAuthComplete(sync, Status.CANCEL);
                  }
                  return false;
                });
    final AlertDialog dialog = builder.create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.show();
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean checkStoragePermissions(final AppCompatActivity activity) {
    boolean result = true;
    String[] requiredPerms;
    requiredPerms =
        new String[] {
          Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
        };
    List<String> defaultPerms = new ArrayList<>();
    for (final String perm : requiredPerms) {
      if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
        // Normally, ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        // should be used here but this is needed anyway (no specific motivation)
        defaultPerms.add(perm);
      }
    }
    if (!defaultPerms.isEmpty()) {
      // Request permission, dont care about the result
      final String[] perms = new String[defaultPerms.size()];
      defaultPerms.toArray(perms);
      ActivityCompat.requestPermissions(activity, perms, REQUEST_STORAGE);
      result = false;
    }
    // TODO A popup in the AccountActivity, to prompt for storage permissions
    return result;
  }

  public void startUploading(Callback callback, HashSet<String> synchronizers, long id) {
    mID = id;
    uploadCallback = callback;
    pendingSynchronizers = synchronizers;
    mSpinner.setTitle("Uploading (" + pendingSynchronizers.size() + ")");
    mSpinner.show();
    nextSynchronizer();
  }

  private void nextSynchronizer() {
    if (pendingSynchronizers.isEmpty()) {
      doneUploading();
      return;
    }

    mSpinner.setTitle("Uploading (" + pendingSynchronizers.size() + ")");
    final Synchronizer synchronizer = synchronizers.get(pendingSynchronizers.iterator().next());
    pendingSynchronizers.remove(synchronizer.getName());
    doUpload(synchronizer);
  }

  private void doUpload(final Synchronizer synchronizer) {
    final ProgressDialog copySpinner = mSpinner;
    final SQLiteDatabase copyDB = DBHelper.getWritableDatabase(mContext);

    copySpinner.setMessage(
        getResources().getString(SyncMode.UPLOAD.getTextId(), synchronizer.getName()));

    executor.execute(
        () -> {
          Status result;
          try {
            result = synchronizer.upload(copyDB, mID);
            // See doUpload() for motivation
            if (result == Synchronizer.Status.NEED_REFRESH) {
              result = handleRefreshComplete(synchronizer, synchronizer.refreshToken());
              if (result == Synchronizer.Status.OK) {
                result = synchronizer.upload(copyDB, mID);
              }
            }
          } catch (Exception ex) {
            Log.e(getClass().getName(), "Upload failed", ex);
            result = Synchronizer.Status.ERROR;
          }

          final Status finalResult = result;

          mainHandler.post(
              () -> {
                switch (finalResult) {
                  case OK:
                    syncOK(synchronizer, copySpinner, copyDB, finalResult);
                    nextSynchronizer();
                    break;
                  case NEED_AUTH:
                    handleAuth(
                        (synchronizerName, status) -> {
                          if (status == Synchronizer.Status.OK) {
                            doUpload(synchronizer);
                          } else {
                            nextSynchronizer();
                          }
                        },
                        synchronizer,
                        finalResult.authMethod);
                    break;
                  case CANCEL:
                    pendingSynchronizers.clear();
                    doneUploading();
                    break;
                  default:
                    nextSynchronizer();
                    break;
                }
              });
        });
  }

  /**
   * Fetch external identifiers for the services. This is done after normal upload has finished by
   * polling The result is seen in activity upload only (clickable link).
   *
   * @param synchronizer
   * @param copyDB
   * @param status
   */
  private void getExternalId(
      final Synchronizer synchronizer,
      final SQLiteDatabase copyDB,
      final Synchronizer.Status status) {
    if (status.externalIdStatus == Synchronizer.ExternalIdStatus.PENDING) {
      executor.execute(
          () -> {
            // Implementation must delay the call rate
            final Status result = synchronizer.getExternalId(copyDB, status);

            mainHandler.post(
                () ->
                    // the external status is updated, check
                    externalIdCompleted(synchronizer, copyDB, result));
          });
    }
  }

  private void syncOK(
      Synchronizer synchronizer,
      ProgressDialog copySpinner,
      SQLiteDatabase copyDB,
      Synchronizer.Status status) {
    copySpinner.setMessage(getResources().getString(org.runnerup.common.R.string.Saving));
    status.activityId = mID; // Not always set

    ContentValues tmp = new ContentValues();
    tmp.put(DB.EXPORT.ACCOUNT, synchronizer.getId());
    tmp.put(DB.EXPORT.ACTIVITY, status.activityId);
    tmp.put(DB.EXPORT.STATUS, status.externalIdStatus.getInt());
    tmp.put(DB.EXPORT.EXTERNAL_ID, status.externalId);
    copyDB.insert(DB.EXPORT.TABLE, null, tmp);

    getExternalId(synchronizer, copyDB, status);
  }

  private void doneUploading() {
    try {
      mSpinner.dismiss();
    } catch (IllegalArgumentException ex) {
      // Taskkiller?
      Log.e(getClass().getName(), "Dismissing spinner failed:", ex);
    }
    final Callback cb = uploadCallback;
    uploadCallback = null;
    if (cb != null) cb.run(null, null);
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != CONFIGURE_REQUEST || authSynchronizer == null) {
      return;
    }

    handleAuthComplete(authSynchronizer, authSynchronizer.getAuthResult(resultCode, data));
  }

  public void disableSynchronizer(
      Callback disconnectCallback, String synchronizer, boolean clearUploads) {
    disableSynchronizer(disconnectCallback, synchronizers.get(synchronizer), clearUploads);
  }

  private void disableSynchronizer(
      Callback callback, Synchronizer synchronizer, boolean clearUploads) {
    resetDB(callback, synchronizer, clearUploads);
  }

  private void resetDB(
      final Callback callback, final Synchronizer synchronizer, final boolean clearUploads) {
    mSpinner.show();
    mSpinner.setTitle("Resetting " + synchronizer.getName());

    executor.execute(
        () -> {
          try {
            final String[] args = {Long.toString(synchronizer.getId())};
            ContentValues config = new ContentValues();
            config.putNull(DB.ACCOUNT.AUTH_CONFIG);
            mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);

            if (clearUploads) {
              mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
            }
            synchronizer.reset();
          } catch (Exception ex) {
            Log.e(getClass().getName(), "Failed to reset " + synchronizer.getName(), ex);
          }

          mainHandler.post(
              () -> {
                mSpinner.dismiss();
                callback.run(synchronizer.getName(), Synchronizer.Status.OK);
              });
        });
  }

  public void clearUploadsByName(Callback callback, String synchronizerName) {
    Synchronizer synchronizer = synchronizers.get(synchronizerName);
    final String[] args = {Long.toString(synchronizer.getId())};
    mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
    callback.run(synchronizerName, Synchronizer.Status.OK);
  }

  public void clearUpload(String name, long id) {
    Synchronizer synchronizer = synchronizers.get(name);
    if (synchronizer != null) {
      final String[] args = {Long.toString(synchronizer.getId()), Long.toString(id)};
      mDB.delete(
          DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ? AND " + DB.EXPORT.ACTIVITY + " = ?", args);
    }
  }

  public void loadActivityList(
      final List<SyncActivityItem> items, final String synchronizerName, final Callback callback) {
    mSpinner.setTitle(getResources().getString(org.runnerup.common.R.string.Loading_activities));
    mSpinner.setMessage(
        getResources()
            .getString(org.runnerup.common.R.string.Fetching_activities_from_1s, synchronizerName));
    mSpinner.show();

    final Synchronizer synchronizer = synchronizers.get(synchronizerName);
    if (synchronizer == null) {
      mainHandler.post(
          () -> {
            mSpinner.dismiss();
            callback.run(synchronizerName, Status.ERROR);
          });
      return;
    }

    executor.execute(
        () -> {
          final Status result = synchronizer.listActivities(items);

          mainHandler.post(
              () -> {
                callback.run(synchronizerName, result);
                mSpinner.dismiss();
              });
        });
  }

  public void loadWorkoutList(
      ArrayList<WorkoutRef> workoutRef, Callback callback, HashSet<String> wourkouts) {

    listWorkoutCallback = callback;
    pendingListWorkout = wourkouts;
    this.workoutRef = workoutRef;

    mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
    mSpinner.show();
    nextListWorkout();
  }

  private void nextListWorkout() {
    if (pendingListWorkout.isEmpty()) {
      doneListing();
      return;
    }

    mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
    Synchronizer synchronizer = synchronizers.get(pendingListWorkout.iterator().next());
    pendingListWorkout.remove(synchronizer.getName());
    mSpinner.setMessage("Configure " + synchronizer.getName());
    if (!synchronizer.isConfigured()) {
      nextListWorkout();
      return;
    }
    doListWorkout(synchronizer);
  }

  private void doListWorkout(final Synchronizer synchronizer) {
    final ProgressDialog copySpinner = mSpinner;
    copySpinner.setMessage("Listing from " + synchronizer.getName());

    executor.execute(
        () -> {
          final ArrayList<Pair<String, String>> list = new ArrayList<>();
          Status result;
          try {
            result = synchronizer.listWorkouts(list);
            if (result == Synchronizer.Status.NEED_REFRESH) {
              result = handleRefreshComplete(synchronizer, synchronizer.refreshToken());
              if (result == Synchronizer.Status.OK) {
                result = synchronizer.listWorkouts(list);
              }
            }
          } catch (Exception ex) {
            Log.e(getClass().getName(), "List workouts failed", ex);
            result = Synchronizer.Status.ERROR;
          }

          final Status finalResult = result;
          mainHandler.post(
              () -> {
                switch (finalResult) {
                  case OK:
                    for (Pair<String, String> w : list) {
                      workoutRef.add(new WorkoutRef(synchronizer.getName(), w.first, w.second));
                    }
                    nextListWorkout();
                    break;

                  case NEED_AUTH:
                    handleAuth(
                        (synchronizerName, status) -> {
                          if (status == Synchronizer.Status.OK) {
                            doListWorkout(synchronizer);
                          } else {
                            // Unexpected result, nothing to do
                            nextListWorkout();
                          }
                        },
                        synchronizer,
                        finalResult.authMethod);
                    return;

                  default:
                    nextListWorkout();
                    break;
                }
              });
        });
  }

  private void doneListing() {
    mSpinner.dismiss();
    Callback cb = listWorkoutCallback;
    listWorkoutCallback = null;
    if (cb != null) cb.run(null, Status.OK);
  }

  public void loadWorkouts(final HashSet<WorkoutRef> pendingWorkouts, final Callback callback) {
    int cnt = pendingWorkouts.size();
    mSpinner.setTitle("Downloading workouts (" + cnt + ")");
    mSpinner.show();

    executor.execute(
        () -> {
          for (WorkoutRef ref : pendingWorkouts) {
            mainHandler.post(
                () ->
                    mSpinner.setMessage(
                        "Loading " + ref.workoutName + " from " + ref.synchronizer));

            Synchronizer synchronizer = synchronizers.get(ref.synchronizer);
            if (synchronizer == null) continue;

            File f = WorkoutSerializer.getFile(mContext, ref.workoutName);
            File w = f;
            if (f.exists()) {
              w = WorkoutSerializer.getFile(mContext, ref.workoutName + ".tmp");
            }
            try {
              synchronizer.downloadWorkout(w, ref.workoutKey);
              if (w != f) {
                if (!compareFiles(w, f)) {
                  Log.w(
                      getClass().getName(), "overwriting " + f.getPath() + " with " + w.getPath());
                  // TODO dialog
                  if (f.exists()) f.delete();
                  w.renameTo(f);
                } else {
                  Log.v(getClass().getName(), "file identical...deleting temporary " + w.getPath());
                  w.delete();
                }
              }
            } catch (Exception e) {
              Log.e(getClass().getName(), "Workout download failed for " + ref.workoutName, e);
              w.delete();
            }
          }

          mainHandler.post(
              () -> {
                mSpinner.dismiss();
                if (callback != null) {
                  callback.run(null, Synchronizer.Status.OK);
                }
              });
        });
  }

  /**
   * Load synchronizer private data
   *
   * @param synchronizer The instance
   * @return The private data
   * @throws Exception
   */
  String loadData(Synchronizer synchronizer) throws Exception {
    InputStream in = mContext.getAssets().open(synchronizer.getName() + ".data");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    String key = Encryption.calculateRFC2104HMAC("RunnerUp", synchronizer.getName());
    Encryption.decrypt(in, out, key);

    return out.toString();
  }

  private Resources getResources() {
    return mContext.getResources();
  }

  public Context getContext() {
    return mContext;
  }

  public void syncActivities(
      SyncMode mode,
      Callback synchCallback,
      String synchronizerName,
      List<SyncActivityItem> list,
      final StringBuffer cancel) {

    prepareSpinnerForSync(list, cancel, mode, synchronizerName);

    load(synchronizerName);
    final Synchronizer synchronizer = synchronizers.get(synchronizerName);
    if (synchronizer == null) {
      synchCallback.run(synchronizerName, Status.INCORRECT_USAGE);
      return;
    }
    cancelSync = cancel;
    syncActivityCallback = synchCallback;
    syncActivitiesList = list;
    mSpinner.show();
    Button noButton = mSpinner.getButton(DialogInterface.BUTTON_NEGATIVE);
    if (noButton != null) {
      noButton.setOnClickListener(
          v -> {
            synchronized (cancel) {
              cancel.append('t');
            }
            if (mSpinner != null)
              mSpinner.setMessage(
                  getResources().getString(org.runnerup.common.R.string.Cancellingplease_wait));
          });
    }
    syncNextActivity(synchronizer, mode);
  }

  private void prepareSpinnerForSync(
      List<SyncActivityItem> list,
      final StringBuffer cancel,
      SyncMode mode,
      String synchronizerName) {
    String msg = getResources().getString(mode.getTextId(), synchronizerName);
    mSpinner.setTitle(msg);
    mSpinner.setButton(
        DialogInterface.BUTTON_NEGATIVE,
        "Cancel",
        (dialog, which) -> {
          synchronized (cancel) {
            cancel.append('t');
          }
          mSpinner.setMessage(
              getResources().getString(org.runnerup.common.R.string.Cancellingplease_wait));
        });
    mSpinner.setCancelable(false);
    mSpinner.setCanceledOnTouchOutside(false);
    mSpinner.setMax(list.size());
  }

  private boolean checkCancel(StringBuffer cancel) {
    if (cancel != null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (cancel) {
        return cancel.length() > 0;
      }
    }
    return false;
  }

  private void syncNextActivity(final Synchronizer synchronizer, SyncMode mode) {
    if (checkCancel(cancelSync)) {
      mSpinner.cancel();
      syncActivityCallback.run(synchronizer.getName(), Synchronizer.Status.CANCEL);
      return;
    }

    if (syncActivitiesList.isEmpty()) {
      try {
        mSpinner.cancel();
      } catch (IllegalStateException ex) {
        Log.i(getClass().getName(), "Failed to dismiss dialog:", ex);
        return;
      }

      syncActivityCallback.run(synchronizer.getName(), Synchronizer.Status.OK);
      return;
    }

    mSpinner.setProgress(syncActivitiesList.size());
    SyncActivityItem ai = syncActivitiesList.get(0);
    syncActivitiesList.remove(0);
    mID = ai.getId();
    doSyncMulti(synchronizer, mode, ai);
  }

  private void doSyncMulti(
      final Synchronizer synchronizer, final SyncMode mode, final SyncActivityItem activityItem) {
    final ProgressDialog copySpinner = mSpinner;
    final SQLiteDatabase copyDB = DBHelper.getWritableDatabase(mContext);

    copySpinner.setMessage((1 + syncActivitiesList.size()) + " remaining");

    executor.execute(
        () -> {
          Status result;
          try {
            switch (mode) {
              case UPLOAD:
                result = synchronizer.upload(copyDB, activityItem.getId());
                break;
              case DOWNLOAD:
                result = synchronizer.download(copyDB, activityItem);
                break;
              default:
                result = Synchronizer.Status.INCORRECT_USAGE;
            }

            // See doUpload() for motivation
            if (result == Synchronizer.Status.NEED_REFRESH) {
              result = handleRefreshComplete(synchronizer, synchronizer.refreshToken());
              if (result == Synchronizer.Status.OK) {
                switch (mode) {
                  case UPLOAD:
                    result = synchronizer.upload(copyDB, activityItem.getId());
                    break;
                  case DOWNLOAD:
                    result = synchronizer.download(copyDB, activityItem);
                    break;
                  default:
                    result = Synchronizer.Status.INCORRECT_USAGE;
                }
              }
            }
          } catch (Exception ex) {
            Log.e(getClass().getName(), "Sync (multi) failed", ex);
            result = Synchronizer.Status.ERROR;
          }

          final Status finalResult = result;

          mainHandler.post(
              () -> {
                switch (finalResult) {
                  case OK:
                    syncOK(synchronizer, copySpinner, copyDB, finalResult);
                    syncNextActivity(synchronizer, mode);
                    break;
                  case NEED_AUTH:
                    handleAuth(
                        (synchronizerName, s2) -> {
                          if (s2 == Synchronizer.Status.OK) {
                            doSyncMulti(synchronizer, mode, activityItem);
                          } else {
                            syncNextActivity(synchronizer, mode);
                          }
                        },
                        synchronizer,
                        finalResult.authMethod);
                    return;
                  case CANCEL:
                    syncActivitiesList.clear();
                    syncNextActivity(synchronizer, mode);
                    break;
                  default:
                    syncNextActivity(synchronizer, mode);
                    break;
                }
              });
        });
  }

  public void loadLiveLoggers(List<WorkoutObserver> liveLoggers) {
    liveLoggers.clear();
    Resources res = getResources();
    String key = res.getString(R.string.pref_runneruplive_active);
    if (!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(key, false)) {
      return;
    }

    String[] from =
        new String[] {
          "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FORMAT, DB.ACCOUNT.FLAGS
        };

    try (Cursor c =
        mDB.query(
            DB.ACCOUNT.TABLE,
            from,
            "( " + DB.ACCOUNT.FLAGS + "&" + (1 << DB.ACCOUNT.FLAG_LIVE) + ") != 0",
            null,
            null,
            null,
            null,
            null)) {
      if (c.moveToFirst()) {
        do {
          ContentValues config = DBHelper.get(c);
          Synchronizer u = add(config);
          if (u != null
              && u.isConfigured()
              && u.checkSupport(Synchronizer.Feature.LIVE)
              && (u instanceof WorkoutObserver)) {
            liveLoggers.add((WorkoutObserver) u);
          }
        } while (c.moveToNext());
      }
    } catch (IllegalStateException ex) {
      // Taskkiller?
      Log.e(getClass().getName(), "Query for liveloggers failed:", ex);
    }
  }

  public enum SyncMode {
    DOWNLOAD(org.runnerup.common.R.string.Downloading_from_1s),
    UPLOAD(org.runnerup.common.R.string.Uploading_to_1s);

    final int textId;

    SyncMode(int textId) {
      this.textId = textId;
    }

    public int getTextId() {
      return textId;
    }
  }

  public interface Callback {
    void run(String synchronizerName, Synchronizer.Status status);
  }

  public record WorkoutRef(String synchronizer, String workoutKey, String workoutName) {}
}
