/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.runnerup.R;
import org.runnerup.common.util.Constants;

/**
 * An activity dedicated to handling a single runtime permission request.
 *
 * <p>This activity is designed to be launched with an {@link Intent} containing the specific
 * permission string to request (via {@link Constants.Intents#EXTRA_PERMISSION_TO_REQUEST}). It
 * manages the standard Android permission request flow, including:
 *
 * <ul>
 *   <li>Checking if the permission is already granted.
 *   <li>Showing a rationale explanation to the user if {@link
 *       #shouldShowRequestPermissionRationale(String)} indicates it's necessary. The rationale UI
 *       includes a message and an "OK" button to proceed.
 *   <li>Launching the system permission dialog using the {@link
 *       ActivityResultContracts.RequestPermission} contract.
 * </ul>
 *
 * <p>Upon completion of the permission request flow (whether granted or denied by the user), this
 * activity finishes itself and communicates the outcome by sending a local broadcast. The broadcast
 * {@link Intent} uses the action {@link Constants.Intents#ACTION_PERMISSION_RESULT} and includes
 * extras for the granted status ({@link Constants.Intents#EXTRA_PERMISSION_GRANTED}) and the name
 * of the permission that was requested ({@link Constants.Intents#EXTRA_REQUESTED_PERMISSION_NAME}).
 *
 * <p>Callers (like other Activities or Services) can register a {@link
 * LocalBroadcastManager#getInstance(Context)} with an {@link IntentFilter} for {@link
 * Constants.Intents#ACTION_PERMISSION_RESULT} to receive the result of the permission request.
 */
public class RequestPermissionActivity extends AppCompatActivity {

  private static final String TAG = "RequestPermissionActivity";
  private String permissionToRequest;
  private ActivityResultLauncher<String> requestPermissionLauncher;
  private View rationaleUIRoot;

  private final ActivityResultCallback<Boolean> resultCallback =
      isGranted -> {
        rationaleUIRoot.setVisibility(View.GONE);

        if (isGranted) {
          Log.d(TAG, "resultCallback: " + permissionToRequest + " granted by user.");
          sendPermissionResultAndFinish(true);
        } else {
          Log.w(TAG, permissionToRequest + " denied by user.");
          // TODO: As per Android guidelines, implement UI to inform the user that the specific
          //  feature (requiring permissionToRequest) is unavailable due to denial.
          // For now, just send the denial result back to the caller.
          sendPermissionResultAndFinish(false);
        }
      };

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Finish early if there is no permission to request
    Intent intent = getIntent();
    if (intent == null || !intent.hasExtra(Constants.Intents.EXTRA_PERMISSION_TO_REQUEST)) {
      Log.e(TAG, "Activity started without EXTRA_PERMISSION_TO_REQUEST. Finishing.");
      sendPermissionResultAndFinish(false);
      return;
    }

    setContentView(R.layout.activity_request_permission);
    rationaleUIRoot = findViewById(R.id.rationale_layout_root);
    rationaleUIRoot.setVisibility(View.GONE); // Initially, hide the rationale UI

    // Prepare the permission rationale message
    permissionToRequest = intent.getStringExtra(Constants.Intents.EXTRA_PERMISSION_TO_REQUEST);
    String simplePermissionName = getSimplePermissionName(permissionToRequest);
    String rationaleMessage =
        getString(R.string.permission_rationale_message, simplePermissionName);
    TextView rationaleMessageTextView = findViewById(R.id.rationale_message);
    rationaleMessageTextView.setText(rationaleMessage);

    // Set up the "OK" button in the rationale UI. When clicked, it hides the rationale
    // and proceeds to request the actual system permission.
    Button okButton = findViewById(R.id.rationale_button_ok);
    okButton.setOnClickListener(
        v -> {
          Log.d(TAG, "OK button clicked after rationale shown. Requesting permission.");
          rationaleUIRoot.setVisibility(View.GONE);
          requestPermissionLauncher.launch(permissionToRequest);
        });

    requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), resultCallback);

    startRequestPermissionFlow();
  }

  private void startRequestPermissionFlow() {
    // 1. Check if permission is already granted.
    if (ContextCompat.checkSelfPermission(this, permissionToRequest)
        == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "startRequestPermissionFlow: Permission is already granted. Finishing.");
      sendPermissionResultAndFinish(true);
      return;
    }

    // 2. If not already granted, check if a rationale should be shown.
    Log.d(
        TAG, "startRequestPermissionFlow: Permission is not already granted. Checking rationale.");
    boolean shouldShowRationale = shouldShowRequestPermissionRationale(permissionToRequest);

    if (shouldShowRationale) {
      // 3a. Rationale needed: Make rationale UI visible.
      Log.d(TAG, "startRequestPermissionFlow: Rationale needed. Making UI visible.");
      // TODO: As per Android guidelines, in an educational UI, explain to the user why the app
      //  requires this permission. In this UI, include a "cancel" or "no thanks" button.
      // For now, just show the rationale message with an OK button.
      rationaleUIRoot.setVisibility(View.VISIBLE);
    } else {
      // 3b. No rationale needed: Directly request permission.
      Log.d(
          TAG, "startRequestPermissionFlow: No rationale needed. Requesting permission directly.");
      requestPermissionLauncher.launch(permissionToRequest);
    }
  }

  private void sendPermissionResultAndFinish(boolean granted) {
    Log.d(TAG, "sendPermissionResultAndFinish: result=" + granted);

    Intent resultIntent = new Intent(Constants.Intents.ACTION_PERMISSION_RESULT);
    resultIntent.putExtra(Constants.Intents.EXTRA_PERMISSION_GRANTED, granted);
    resultIntent.putExtra(Constants.Intents.EXTRA_REQUESTED_PERMISSION_NAME, permissionToRequest);
    resultIntent.setPackage(getPackageName());
    LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);

    finish();
  }

  /**
   * Converts a full Android permission string (e.g., "android.permission.POST_NOTIFICATIONS") into
   * a more human-readable, simplified name (e.g., "POST NOTIFICATIONS"). If the permission string
   * does not start with "android.permission.", it's returned as is.
   *
   * @param permission The full permission string.
   * @return A simplified, human-readable version of the permission name, or "Unknown" if the input
   *     permission is null.
   */
  private String getSimplePermissionName(String permission) {
    if (permission == null) return "Unknown";

    if (permission.startsWith("android.permission.")) {
      return permission.substring("android.permission.".length()).replace("_", " ");
    }

    return permission;
  }
}
