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
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import org.runnerup.hr.HeartRatePermissionState;
import org.runnerup.wear.ui.WearHeartRatePermissionComposeContent;

/** Visible watch surface for reviewing and granting the heart-rate sensor permission. */
public final class HeartRatePermissionFragment extends Fragment {
  private MainActivity activity;
  private ComposeView composeView;
  private String permission;
  private String backgroundPermission;
  private boolean sensorAvailable;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(activity);
    composeView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    permission = HeartRatePermissionState.permissionForSdk(Build.VERSION.SDK_INT);
    backgroundPermission =
        HeartRatePermissionState.backgroundPermissionForSdk(Build.VERSION.SDK_INT);
    SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    sensorAvailable =
        sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null;
    refreshStatus();
    return composeView;
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
    if (composeView == null) return;
    boolean granted = isPermissionGranted(permission);
    boolean backgroundGranted =
        backgroundPermission == null || isPermissionGranted(backgroundPermission);
    HeartRatePermissionState.State state =
        HeartRatePermissionState.resolve(
            sensorAvailable, granted, backgroundGranted, Build.VERSION.SDK_INT);
    WearHeartRatePermissionComposeContent.bind(
        composeView, state, Build.VERSION.SDK_INT, this::requestMissingPermission);
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

  @Override
  public void onDestroyView() {
    if (composeView != null) {
      composeView.disposeComposition();
      composeView = null;
    }
    super.onDestroyView();
  }
}
