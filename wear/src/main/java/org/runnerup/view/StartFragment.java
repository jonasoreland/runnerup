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
import org.runnerup.hr.HeartRateReadiness;
import org.runnerup.relven.strength.StrengthSession;
import org.runnerup.service.PreparedStrengthSessionStore;
import org.runnerup.service.PreparedWorkoutStore;
import org.runnerup.service.StateService;
import org.runnerup.service.WearStrengthExecutionController;
import org.runnerup.service.WearStrengthExecutionStore;
import org.runnerup.wear.ui.WearControlPresentationState;
import org.runnerup.wear.ui.WearControlsComposeContent;
import org.runnerup.wear.ui.WearStrengthWorkoutUiState;

public class StartFragment extends Fragment
    implements ValueModel.ChangeListener<TrackerState>,
        SharedPreferences.OnSharedPreferenceChangeListener {

  private ComposeView composeView;
  private WearControlPresentationState presentationState;
  private MainActivity activity;
  private final Handler countdownHandler = new Handler(Looper.getMainLooper());
  private final Runnable countdownTick = this::updatePreparedCountdown;
  private final Runnable strengthTick = this::updateStrengthTick;
  private WearStrengthExecutionStore strengthStore;
  private WearStrengthExecutionController strengthController;
  private StrengthSession displayStrengthSession;
  private WearStrengthWorkoutUiState strengthUiState;
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
    strengthStore = new WearStrengthExecutionStore(activity);
    strengthController =
        new WearStrengthExecutionController(strengthStore, () -> SystemClock.elapsedRealtime());
    updateView(activity.getTrackerState());
    return composeView;
  }

  private void updateView(TrackerState state) {
    if (composeView == null || presentationState == null) return;
    StrengthSession execution = strengthStore == null ? null : strengthStore.load();
    if (execution != null
        && state != TrackerState.STARTED
        && state != TrackerState.PAUSED
        && state != TrackerState.CONNECTING
        && (execution.getPhase() == StrengthSession.Phase.PREPARING
            || execution.getPhase() == StrengthSession.Phase.REPETITIONS
            || execution.getPhase() == StrengthSession.Phase.TIMED_WORK
            || execution.getPhase() == StrengthSession.Phase.REST
            || execution.getPhase() == StrengthSession.Phase.AWAITING_RESULT
            || execution.getPhase() == StrengthSession.Phase.PAUSED
            || execution.getPhase() == StrengthSession.Phase.COMPLETE)) {
      displayStrengthSession = execution;
      renderStrengthSession(execution);
      scheduleStrengthTick(execution);
      return;
    }
    if (state != TrackerState.STARTED
        && state != TrackerState.PAUSED
        && state != TrackerState.CONNECTING) {
      org.runnerup.wear.ui.PreparedStrengthSessionPresentation strength =
          org.runnerup.wear.ui.PreparedStrengthSessionPresentationFactory.from(
              new org.runnerup.service.PreparedStrengthSessionStore(activity).load());
      if (strength != null) {
        WearControlsComposeContent.bindPreparedStrengthWorkout(
            composeView,
            strength.getWorkoutName(),
            strength.getExerciseName(),
            strength.getPrescription(),
            strength.getInstructions(),
            () -> startStrengthSession());
        return;
      }
    }
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
          org.runnerup.wear.ui.WearHeartRatePermissionPresentationKt.wearHeartRatePreparationLabel(
              HeartRateReadiness.inspect(activity)),
          presentationState,
          () -> startPreparedWorkout(prepared.getSessionId()));
    } catch (java.io.IOException ignored) {
      presentationState.updateStartState(state);
      WearControlsComposeContent.bindStart(composeView, presentationState);
    }
  }

  private void renderStrengthSession(StrengthSession session) {
    if (composeView == null || session == null) return;
    if (session.getPhase() == StrengthSession.Phase.PREPARING) {
      strengthUiState = null;
      var presentation =
          org.runnerup.wear.ui.PreparedStrengthSessionPresentationFactory.from(
              new PreparedStrengthSessionStore(activity).load());
      if (presentation != null) {
        WearControlsComposeContent.bindPreparedStrengthWorkout(
            composeView,
            presentation.getWorkoutName(),
            presentation.getExerciseName(),
            presentation.getPrescription(),
            presentation.getInstructions(),
            () -> startStrengthSession());
        return;
      }
    }
    if (strengthUiState == null) {
      strengthUiState = new WearStrengthWorkoutUiState(session);
      WearControlsComposeContent.bindActiveStrengthWorkout(
          composeView,
          strengthUiState,
          () -> startStrengthSession(),
          repetitions -> logStrengthRepetitions(repetitions),
          () -> updateStrength(s -> s.pause(SystemClock.elapsedRealtime())),
          () -> updateStrength(s -> s.resume(SystemClock.elapsedRealtime())),
          () -> updateStrength(s -> s.skipPendingTimedResult(SystemClock.elapsedRealtime())),
          () -> updateStrength(s -> s.skipCurrentExercise(SystemClock.elapsedRealtime())),
          () -> updateStrength(s -> s.skipRest(SystemClock.elapsedRealtime())),
          () -> updateStrength(s -> s.finishEarly(SystemClock.elapsedRealtime())));
    } else {
      strengthUiState.update(session);
    }
  }

  private void startStrengthSession() {
    updateStrength(s -> s.begin(SystemClock.elapsedRealtime()));
  }

  private boolean logStrengthRepetitions(int repetitions) {
    return updateStrength(s -> s.completeRepetitions(repetitions, SystemClock.elapsedRealtime()));
  }

  private boolean updateStrength(
      java.util.function.Function<StrengthSession, StrengthSession> change) {
    if (strengthController == null) return false;
    try {
      WearStrengthExecutionController.Update update =
          strengthController.update(session -> change.apply(session));
      if (update.getSession() == null || !update.getCommitted()) return false;
      displayStrengthSession = update.getSession();
      publishStrengthUpdate(update);
      renderStrengthSession(displayStrengthSession);
      scheduleStrengthTick(displayStrengthSession);
      return true;
    } catch (IllegalStateException | IllegalArgumentException invalidTransition) {
      android.util.Log.w(
          "RU:StartFragment", "Ignoring invalid strength UI action", invalidTransition);
      return false;
    }
  }

  private void updateStrengthTick() {
    if (strengthController == null || !isAdded()) return;
    try {
      WearStrengthExecutionController.Update update = strengthController.tick();
      if (update.getSession() != null) {
        displayStrengthSession = update.getSession();
        publishStrengthUpdate(update);
        renderStrengthSession(displayStrengthSession);
        scheduleStrengthTick(displayStrengthSession);
      }
    } catch (IllegalStateException invalidTransition) {
      android.util.Log.w("RU:StartFragment", "Strength timer refresh failed", invalidTransition);
    }
  }

  private void scheduleStrengthTick(StrengthSession session) {
    countdownHandler.removeCallbacks(strengthTick);
    if (session != null
        && (session.getPhase() == StrengthSession.Phase.TIMED_WORK
            || session.getPhase() == StrengthSession.Phase.REST)) {
      countdownHandler.postDelayed(strengthTick, 500L);
    }
  }

  private void publishStrengthUpdate(WearStrengthExecutionController.Update update) {
    StateService service = activity == null ? null : activity.getStateService();
    if (service == null || !update.getCommitted()) return;
    service.publishStrengthAuthorityProjection();
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
    activity
        .getSharedPreferences(PreparedStrengthSessionStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .registerOnSharedPreferenceChangeListener(this);
    activity
        .getSharedPreferences(WearStrengthExecutionStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .registerOnSharedPreferenceChangeListener(this);
    updateView(this.activity.getTrackerState());
  }

  @Override
  public void onPause() {
    countdownHandler.removeCallbacks(countdownTick);
    countdownHandler.removeCallbacks(strengthTick);
    if (activity.getStateService() != null) {
      activity.getStateService().unregisterPreparedCountdownListener(countdownListener);
    }
    activity.unregisterTrackerStateListener(this);
    activity
        .getSharedPreferences(PreparedWorkoutStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .unregisterOnSharedPreferenceChangeListener(this);
    activity
        .getSharedPreferences(PreparedStrengthSessionStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
        .unregisterOnSharedPreferenceChangeListener(this);
    activity
        .getSharedPreferences(WearStrengthExecutionStore.PREFERENCES_NAME, Activity.MODE_PRIVATE)
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
    if ((PreparedWorkoutStore.PREPARED_WORKOUT_KEY.equals(key)
            || PreparedStrengthSessionStore.PREPARED_SESSION_KEY.equals(key)
            || WearStrengthExecutionStore.STATE_KEY.equals(key))
        && isAdded()) {
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
    strengthStore = null;
    strengthController = null;
    displayStrengthSession = null;
    strengthUiState = null;
    super.onDestroyView();
  }
}
