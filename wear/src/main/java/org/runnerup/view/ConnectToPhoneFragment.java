/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import org.runnerup.common.wear.WorkoutPreparationProtocol;
import org.runnerup.service.PreparedWorkoutStore;
import org.runnerup.wear.ui.WearConnectWaitingComposeContent;

/**
 * Relven-styled waiting surface; phone and tracker connection state remain owned by MainActivity.
 */
public final class ConnectToPhoneFragment extends Fragment
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private ComposeView composeView;

  public ConnectToPhoneFragment() {}

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(getActivity());
    composeView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    updatePreview();
    return composeView;
  }

  private void updatePreview() {
    if (composeView == null || getActivity() == null) return;
    String workoutName = null;
    String upcomingStep = null;
    byte[] payload = new PreparedWorkoutStore(getActivity()).load();
    try {
      WorkoutPreparationProtocol.PrepareMessage prepared =
          WorkoutPreparationProtocol.decodePrepare(payload);
      workoutName = prepared.getWorkoutName();
      upcomingStep = prepared.getUpcomingStep();
    } catch (java.io.IOException ignored) {
      // Invalid or missing state falls back to the honest idle/waiting surface.
    }
    org.runnerup.common.tracker.TrackerState state =
        getActivity() instanceof MainActivity
            ? ((MainActivity) getActivity()).getTrackerState()
            : null;
    org.runnerup.service.WearConnectionStatus connectionStatus =
        getActivity() instanceof MainActivity
            ? ((MainActivity) getActivity()).getWearConnectionStatus()
            : org.runnerup.service.WearConnectionStatus.DISCONNECTED;
    WearConnectWaitingComposeContent.bind(
        composeView,
        workoutName,
        upcomingStep,
        state,
        connectionStatus,
        () -> {
          if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).retryPhoneConnection();
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    getActivity()
        .getSharedPreferences(
            PreparedWorkoutStore.PREFERENCES_NAME, android.app.Activity.MODE_PRIVATE)
        .registerOnSharedPreferenceChangeListener(this);
    updatePreview();
  }

  @Override
  public void onPause() {
    getActivity()
        .getSharedPreferences(
            PreparedWorkoutStore.PREFERENCES_NAME, android.app.Activity.MODE_PRIVATE)
        .unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
    if (PreparedWorkoutStore.PREPARED_WORKOUT_KEY.equals(key)) updatePreview();
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
