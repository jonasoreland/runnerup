package org.runnerup.view;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.runnerup.R;
import org.runnerup.hr.HeartRatePermissionState;

/** Visible watch surface for reviewing and granting the heart-rate sensor permission. */
public final class HeartRatePermissionFragment extends Fragment {
  private MainActivity activity;
  private TextView status;
  private TextView details;
  private Button action;
  private String permission;
  private String backgroundPermission;
  private boolean sensorAvailable;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.heart_rate_permission, container, false);
    status = view.findViewById(R.id.heart_rate_permission_status);
    details = view.findViewById(R.id.heart_rate_permission_details);
    action = view.findViewById(R.id.heart_rate_permission_button);
    permission = HeartRatePermissionState.permissionForSdk(Build.VERSION.SDK_INT);
    backgroundPermission =
        HeartRatePermissionState.backgroundPermissionForSdk(Build.VERSION.SDK_INT);
    SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    sensorAvailable =
        sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null;
    action.setOnClickListener(ignored -> requestMissingPermission());
    refreshStatus();
    return view;
  }

  private void requestMissingPermission() {
    boolean foregroundGranted = isPermissionGranted(permission);
    if (foregroundGranted && backgroundPermission != null) {
      if (Build.VERSION.SDK_INT < 36) {
        Intent settingsIntent =
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", activity.getPackageName(), null));
        startActivity(settingsIntent);
      } else {
        requestPermission(backgroundPermission);
      }
      return;
    }
    requestPermission(permission);
  }

  private void requestPermission(String permissionToRequest) {
    Intent intent = new Intent(activity, RequestPermissionActivity.class);
    intent.putExtra(
        org.runnerup.common.util.Constants.Intents.EXTRA_PERMISSION_TO_REQUEST,
        permissionToRequest);
    startActivity(intent);
  }

  private void refreshStatus() {
    if (status == null) return;
    boolean granted = isPermissionGranted(permission);
    boolean backgroundGranted =
        backgroundPermission == null || isPermissionGranted(backgroundPermission);
    HeartRatePermissionState.State state =
        HeartRatePermissionState.resolve(
            sensorAvailable, granted, backgroundGranted, Build.VERSION.SDK_INT);
    switch (state) {
      case UNAVAILABLE:
        status.setText(org.runnerup.common.R.string.wear_hr_sensor_unavailable);
        details.setText(org.runnerup.common.R.string.wear_hr_sensor_unavailable_details);
        action.setVisibility(View.GONE);
        break;
      case NEEDS_PERMISSION:
        status.setText(org.runnerup.common.R.string.wear_hr_permission_needed);
        details.setText(org.runnerup.common.R.string.wear_hr_permission_needed_details);
        action.setText(org.runnerup.common.R.string.wear_hr_grant_permission);
        action.setVisibility(View.VISIBLE);
        break;
      case READY:
        status.setText(org.runnerup.common.R.string.wear_hr_permission_ready);
        details.setText(org.runnerup.common.R.string.wear_hr_permission_ready_details);
        action.setVisibility(View.GONE);
        break;
      case NEEDS_BACKGROUND_PERMISSION:
        status.setText(org.runnerup.common.R.string.wear_hr_background_permission_needed);
        details.setText(
            Build.VERSION.SDK_INT < 36
                ? org.runnerup.common.R.string.wear_hr_background_permission_settings_details
                : org.runnerup.common.R.string.wear_hr_background_permission_details);
        action.setText(
            Build.VERSION.SDK_INT < 36
                ? org.runnerup.common.R.string.wear_hr_open_permission_settings
                : org.runnerup.common.R.string.wear_hr_grant_background_permission);
        action.setVisibility(View.VISIBLE);
        break;
    }
  }

  private boolean isPermissionGranted(String permissionName) {
    return ContextCompat.checkSelfPermission(activity, permissionName)
        == PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshStatus();
  }

  @Override
  public void onAttach(Activity host) {
    super.onAttach(host);
    activity = (MainActivity) host;
  }
}
