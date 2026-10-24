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
public final class ConnectToPhoneFragment extends Fragment {

  private ComposeView composeView;

  public ConnectToPhoneFragment() {}

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(getActivity());
    composeView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
    WearConnectWaitingComposeContent.bind(composeView, workoutName, upcomingStep);
    return composeView;
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
