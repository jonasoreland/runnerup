/*
 * Copyright (C) 2026 Relven contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.runnerup.relven.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.view.WindowCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Feature;
import org.runnerup.util.Bitfield;
import org.runnerup.util.ProgressDialogCompat;
import org.runnerup.view.AccountListActivity;

/** Relven's full-screen upload selection for one saved activity. */
public class ActivityUploadActivity extends AppCompatActivity implements Constants {

  private static final String EXTRA_ACTIVITY_ID = "relven.upload.activity_id";

  public static Intent intent(Context context, long activityId) {
    return new Intent(context, ActivityUploadActivity.class)
        .putExtra(EXTRA_ACTIVITY_ID, activityId);
  }

  private final HashSet<String> selectedServices = new HashSet<>();
  private final HashSet<String> uploadedServices = new HashSet<>();
  private final Map<String, String> uploadedExternalIds = new HashMap<>();
  private final ArrayList<ContentValues> accountRows = new ArrayList<>();
  private long activityId;
  private boolean uploading;
  private SQLiteDatabase database;
  private SyncManager syncManager;
  private ActivityUploadStateHolder stateHolder;

  private final ActivityResultLauncher<Intent> accountListLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            syncManager.clear();
            reloadAccounts();
          });

  private final ActivityResultLauncher<Intent> authLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            syncManager.onActivityResult(
                SyncManager.CONFIGURE_REQUEST, result.getResultCode(), result.getData());
            reloadAccounts();
          });

  @Override
  public void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    Window window = getWindow();
    WindowCompat.getInsetsController(window, window.getDecorView())
        .setAppearanceLightStatusBars(false);
    super.onCreate(savedInstanceState);

    activityId = getIntent().getLongExtra(EXTRA_ACTIVITY_ID, -1L);
    if (activityId <= 0L) {
      finish();
      return;
    }

    database = DBHelper.getReadableDatabase(this);
    // Upload progress is already shown by the Relven Compose screen.
    syncManager = new SyncManager(this, new ProgressDialogCompat(this, false));
    syncManager.setAuthLauncher(authLauncher);
    stateHolder =
        new ActivityUploadStateHolder(
            new ActivityUploadActions() {
              @Override
              public void onBack() {
                if (!uploading) finish();
              }

              @Override
              public void onSelectionChanged(String name, boolean selected) {
                if (uploadedServices.contains(name)) return;
                if (selected) {
                  selectedServices.add(name);
                } else {
                  selectedServices.remove(name);
                }
                publishState();
              }

              @Override
              public void onUpload() {
                if (uploading || selectedServices.isEmpty()) return;
                uploading = true;
                publishState();
                syncManager.startUploading(
                    (synchronizerName, status) -> {
                      uploading = false;
                      reloadAccounts();
                    },
                    new HashSet<>(selectedServices),
                    activityId);
              }

              @Override
              public void onOpenUploadedActivity(String name) {
                openUploadedActivity(name);
              }

              @Override
              public void onClearUpload(String name) {
                syncManager.clearUpload(name, activityId);
                reloadAccounts();
              }

              @Override
              public void onManageAccounts() {
                accountListLauncher.launch(
                    new Intent(ActivityUploadActivity.this, AccountListActivity.class));
              }
            },
            true);

    ComposeView composeView = new ComposeView(this);
    composeView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(composeView);
    ActivityUploadServicesCompose.install(composeView, stateHolder);
    reloadAccounts();
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (!uploading) finish();
              }
            });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (database != null) DBHelper.closeDB(database);
    if (syncManager != null) syncManager.close();
  }

  private void reloadAccounts() {
    String sql =
        "SELECT DISTINCT acc._id, acc."
            + DB.ACCOUNT.NAME
            + ", acc."
            + DB.ACCOUNT.FLAGS
            + ", acc."
            + DB.ACCOUNT.AUTH_CONFIG
            + ", acc."
            + DB.ACCOUNT.FORMAT
            + ", rep._id AS repid, rep."
            + DB.EXPORT.EXTERNAL_ID
            + ", rep."
            + DB.EXPORT.STATUS
            + " FROM "
            + DB.ACCOUNT.TABLE
            + " acc LEFT OUTER JOIN "
            + DB.EXPORT.TABLE
            + " rep"
            + " ON (acc._id = rep."
            + DB.EXPORT.ACCOUNT
            + " AND rep."
            + DB.EXPORT.ACTIVITY
            + " = ?)";
    Cursor cursor = database.rawQuery(sql, new String[] {Long.toString(activityId)});
    selectedServices.clear();
    uploadedServices.clear();
    uploadedExternalIds.clear();
    accountRows.clear();
    int reportIdIndex = cursor.getColumnIndexOrThrow("repid");
    try {
      while (cursor.moveToNext()) {
        ContentValues row = DBHelper.get(cursor);
        Synchronizer synchronizer = syncManager.add(row);
        if (synchronizer == null
            || !synchronizer.checkSupport(Feature.UPLOAD)
            || !synchronizer.isConfigured()) {
          continue;
        }

        String name = row.getAsString(DB.ACCOUNT.NAME);
        accountRows.add(row);
        if (!cursor.isNull(reportIdIndex)) {
          uploadedServices.add(name);
          if (row.containsKey(DB.EXPORT.STATUS)
              && row.getAsInteger(DB.EXPORT.STATUS)
                  == Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
            String externalId = row.getAsString(DB.EXPORT.EXTERNAL_ID);
            if (externalId != null && !externalId.isEmpty()) {
              String url = synchronizer.getActivityUrl(externalId);
              if (url != null) uploadedExternalIds.put(name, externalId);
            }
          }
        } else if (row.containsKey(DB.ACCOUNT.FLAGS)
            && Bitfield.test(row.getAsLong(DB.ACCOUNT.FLAGS), DB.ACCOUNT.FLAG_UPLOAD)) {
          selectedServices.add(name);
        }
      }
    } finally {
      cursor.close();
    }
    publishState();
  }

  private void publishState() {
    ArrayList<ActivityUploadServiceState> services = new ArrayList<>();
    for (ContentValues row : accountRows) {
      String name = row.getAsString(DB.ACCOUNT.NAME);
      boolean uploaded = uploadedServices.contains(name);
      String externalId = uploadedExternalIds.get(name);
      Synchronizer synchronizer = syncManager.getSynchronizerByName(name);
      String externalUrl =
          uploaded && externalId != null && synchronizer != null
              ? synchronizer.getActivityUrl(externalId)
              : null;
      services.add(
          new ActivityUploadServiceState(
              name, uploaded, selectedServices.contains(name), externalId, externalUrl));
    }
    stateHolder.update(services, uploading);
  }

  private void openUploadedActivity(@NonNull String name) {
    String externalId = uploadedExternalIds.get(name);
    Synchronizer synchronizer = syncManager.getSynchronizerByName(name);
    if (externalId == null || synchronizer == null) return;
    String url = synchronizer.getActivityUrl(externalId);
    if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
  }
}
