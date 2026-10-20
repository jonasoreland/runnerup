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
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;
import org.runnerup.wear.ui.WearControlPresentationState;
import org.runnerup.wear.ui.WearControlsComposeContent;

public class PauseResumeFragment extends Fragment
    implements ValueModel.ChangeListener<TrackerState> {

  private static final long SCROLL_DELAY = 1500; // 1.5s
  private final Handler handler = new Handler();
  private ComposeView composeView;
  private WearControlPresentationState presentationState;
  private MainActivity activity;
  private long clickCount = 0;

  public PauseResumeFragment() {}

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(activity);
    presentationState = new WearControlPresentationState();
    WearControlsComposeContent.bindPauseResume(
        composeView, presentationState, this::sendNewLap, this::sendPauseResume);
    updateView(activity.getTrackerState());
    return composeView;
  }

  private void updateView(TrackerState state) {
    if (presentationState != null) presentationState.updatePauseAction(state);
  }

  private void sendPauseResume() {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (stateService == null) return;
    clickCount++;
    stateService.sendPauseResume();
    TrackerState state = activity.getTrackerState();
    if (state == TrackerState.STARTED) {
      updateView(TrackerState.PAUSED);
    } else if (state == TrackerState.PAUSED) {
      updateView(TrackerState.STARTED);
      long saveClickCount = clickCount;
      handler.postDelayed(
          () -> {
            if (saveClickCount == clickCount && isAdded()) activity.scrollToRunInfo();
          },
          SCROLL_DELAY);
    }
  }

  private void sendNewLap() {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (stateService == null) return;
    clickCount++;
    stateService.sendNewLap();
  }

  @Override
  public void onResume() {
    super.onResume();
    activity.registerTrackerStateListener(this);
    updateView(this.activity.getTrackerState());
  }

  @Override
  public void onPause() {
    activity.unregisterTrackerStateListener(this);
    super.onPause();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.activity = (MainActivity) activity;
  }

  @Override
  public void onValueChanged(
      ValueModel<TrackerState> obj, TrackerState oldState, TrackerState newState) {
    if (isAdded()) updateView(newState);
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
