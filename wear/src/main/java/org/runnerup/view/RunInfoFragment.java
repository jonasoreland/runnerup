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

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import java.util.HashMap;
import java.util.Map;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.hr.WearHeartRateSampleStore;
import org.runnerup.service.StateService;
import org.runnerup.wear.ui.WearHeartRateDisplayKt;
import org.runnerup.wear.ui.WearWorkoutComposeContent;
import org.runnerup.wear.ui.WearWorkoutDisplayKt;
import org.runnerup.wear.ui.WearWorkoutPresentationState;

public class RunInfoFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

  private final Map<String, String> dataValues = new HashMap<>();
  private final Map<String, String> headerValues = new HashMap<>();
  private ComposeView composeView;
  private WearWorkoutPresentationState presentationState;
  private int screen;
  private int rowsOnScreen;
  private long dataUpdateTime;
  private long headersTimestamp;
  private final Handler handler = new Handler();
  private boolean handlerOutstanding = false;
  private final Runnable periodicTick =
      new Runnable() {
        @Override
        public void run() {
          update();
          render();
          handlerOutstanding = false;
          if (isResumed()) {
            startTimer();
          }
        }
      };
  private MainActivity mainActivity;

  public static RunInfoFragment createForScreen(int screen, int rowsOnScreen) {
    RunInfoFragment frag = new RunInfoFragment();
    frag.screen = screen;
    frag.rowsOnScreen = rowsOnScreen;
    return frag;
  }

  public RunInfoFragment() {
    super();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    composeView = new ComposeView(mainActivity);
    presentationState = new WearWorkoutPresentationState();
    WearWorkoutComposeContent.bind(composeView, presentationState);
    render();
    return composeView;
  }

  private void startTimer() {
    if (handlerOutstanding) return;
    handlerOutstanding = true;
    handler.postDelayed(periodicTick, 1000);
  }

  private void reset() {
    dataUpdateTime = 0;
    headersTimestamp = 0;
  }

  private void update() {
    Bundle data = mainActivity.getData(dataUpdateTime);
    if (data != null) {
      dataUpdateTime = data.getLong(StateService.UPDATE_TIME);
      update(data);
    }

    Bundle headers = mainActivity.getHeaders(headersTimestamp);
    if (headers != null) {
      headersTimestamp = headers.getLong(StateService.UPDATE_TIME);
      updateHeaders(headers);
    }
  }

  private void update(Bundle b) {
    update(b, false);
  }

  private void updateHeaders(Bundle b) {
    update(b, true);
  }

  private void update(Bundle b, boolean isHeaderSnapshot) {
    Map<String, String> nextHeaders = isHeaderSnapshot ? new HashMap<>() : headerValues;
    for (String key : b.keySet()) {
      Object value = b.get(key);
      if (value instanceof String) {
        if (key.startsWith(Constants.Wear.RunInfo.HEADER)
            || key.equals(Constants.Wear.RunInfo.CURRENT_STEP)) {
          nextHeaders.put(key, (String) value);
        } else {
          dataValues.put(key, (String) value);
        }
      }
    }
    if (isHeaderSnapshot) {
      WearWorkoutDisplayKt.replaceWearHeaderSnapshot(headerValues, nextHeaders);
    }
    render();
  }

  private boolean isPaused() {
    TrackerState state = mainActivity == null ? null : mainActivity.getTrackerState();
    return state == TrackerState.PAUSED;
  }

  private void render() {
    if (composeView != null) {
      presentationState.update(
          headerValues,
          dataValues,
          screen,
          rowsOnScreen,
          isPaused(),
          WearHeartRateSampleStore.getLatest(),
          SystemClock.elapsedRealtime(),
          WearHeartRateDisplayKt.shouldShowWearHeartRate(
              WearHeartRateSampleStore.isCaptureActive(),
              mainActivity == null ? null : mainActivity.getTrackerState()),
          WearHeartRateSampleStore.getCaptureStartedAtElapsedRealtime());
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    startTimer();
    mainActivity.registerTrackerStateListener(this);
    reset();
    update();
    onValueChanged(null, null, mainActivity.getTrackerState());
  }

  @Override
  public void onPause() {
    mainActivity.unregisterTrackerStateListener(this);
    super.onPause();
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

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mainActivity = (MainActivity) activity;
  }

  @Override
  public void onValueChanged(
      ValueModel<TrackerState> obj, TrackerState oldValue, TrackerState newValue) {
    if (!isAdded()) return;

    if (newValue == null) return;

    render();
  }
}
