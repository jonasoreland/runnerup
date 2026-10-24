/*
 * Copyright (C) 2014 weides@gmail.com
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

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;
import org.runnerup.common.wear.WorkoutPreparationProtocol;
import org.runnerup.service.PreparedWorkoutStore;
import org.runnerup.service.StateService;
import org.runnerup.wear.ui.WearControlPresentationState;
import org.runnerup.wear.ui.WearControlsComposeContent;

public class StartFragment extends Fragment
    implements ValueModel.ChangeListener<TrackerState>,
        SharedPreferences.OnSharedPreferenceChangeListener {

  private ComposeView composeView;
  private WearControlPresentationState presentationState;
  private MainActivity activity;
  private final Handler countdownHandler = new Handler(Looper.getMainLooper());
  private final Runnable countdownTick = this::updatePreparedCountdown;
  private final ValueModel.ChangeListener<Long> countdownListener =
      (instance, oldValue, newValue) -> {
        if (isAdded()) activity.runOnUiThread(this::updatePreparedCountdown);
      };

  public StartFragment() {}

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(activity);
    presentationState = new WearControlPresentationState();
    updateView(activity.getTrackerState());
    return composeView;
  }

  private void updateView(TrackerState state) {
    if (composeView == null || presentationState == null) return;
    byte[] payload =
        state == TrackerState.INITIALIZED || state == TrackerState.CONNECTED
            ? new PreparedWorkoutStore(activity).load()
            : null;
    try {
      WorkoutPreparationProtocol.PrepareMessage prepared =
          WorkoutPreparationProtocol.decodePrepare(payload);
      if (activity.getStateService() != null) {
        activity
            .getStateService()
            .setPreparedWorkoutSession(prepared.getSessionId(), prepared.isSkipSupported());
      }
      WearControlsComposeContent.bindPreparedWorkout(
          composeView,
          prepared.getWorkoutName(),
          prepared.getUpcomingStep(),
          presentationState,
          () -> startPreparedWorkout(prepared.getSessionId()));
    } catch (java.io.IOException ignored) {
      presentationState.updateStartState(state);
      WearControlsComposeContent.bindStart(composeView, presentationState, this::startActivity);
    }
  }

  private void startActivity() {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (stateService != null) stateService.sendStart();
  }

  private void startPreparedWorkout(String sessionId) {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (stateService == null) return;
    presentationState.startRequestSent();
    if (!stateService.sendStart(sessionId)) {
      presentationState.updatePreparedCountdown(null, SystemClock.elapsedRealtime());
    }
  }

  private void updatePreparedCountdown() {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (presentationState == null || stateService == null) return;
    long now = SystemClock.elapsedRealtime();
    Long deadline = stateService.getPreparedCountdownDeadlineElapsedRealtimeMs();
    presentationState.updatePreparedCountdown(deadline, now);
    countdownHandler.removeCallbacks(countdownTick);
    if (deadline != null && now < deadline) {
      countdownHandler.postDelayed(countdownTick, 75L);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    activity.registerTrackerStateListener(this);
    if (activity.getStateService() != null) {
      activity.getStateService().registerPreparedCountdownListener(countdownListener);
      updatePreparedCountdown();
    }
    activity
        .getSharedPreferences(PreparedWorkoutStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .registerOnSharedPreferenceChangeListener(this);
    updateView(this.activity.getTrackerState());
  }

  @Override
  public void onPause() {
    countdownHandler.removeCallbacks(countdownTick);
    if (activity.getStateService() != null) {
      activity.getStateService().unregisterPreparedCountdownListener(countdownListener);
    }
    activity.unregisterTrackerStateListener(this);
    activity
        .getSharedPreferences(PreparedWorkoutStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.activity = (MainActivity) activity;
  }

  @Override
  public void onValueChanged(
      ValueModel<TrackerState> obj, TrackerState oldValue, TrackerState newValue) {
    if (isAdded()) updateView(newValue);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
    if (PreparedWorkoutStore.PREPARED_WORKOUT_KEY.equals(key) && isAdded()) {
      updateView(activity.getTrackerState());
    }
  }

  @Override
  public void onDestroyView() {
    if (composeView != null) {
      composeView.disposeComposition();
      composeView = null;
    }
    presentationState = null;
    super.onDestroyView();
  }
}
