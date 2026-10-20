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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;
import org.runnerup.wear.ui.WearControlPresentationState;
import org.runnerup.wear.ui.WearControlsComposeContent;

public class StartFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

  private ComposeView composeView;
  private WearControlPresentationState presentationState;
  private MainActivity activity;

  public StartFragment() {}

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(activity);
    presentationState = new WearControlPresentationState();
    WearControlsComposeContent.bindStart(composeView, presentationState, this::startActivity);
    updateView(activity.getTrackerState());
    return composeView;
  }

  private void updateView(TrackerState state) {
    if (presentationState != null) presentationState.updateStartState(state);
  }

  private void startActivity() {
    StateService stateService = activity == null ? null : activity.getStateService();
    if (stateService != null) stateService.sendStart();
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
      ValueModel<TrackerState> obj, TrackerState oldValue, TrackerState newValue) {
    if (isAdded()) updateView(newValue);
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
