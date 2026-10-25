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
package org.runnerup.tracker.component;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.common.wear.WorkoutSessionEventJournal;
import org.runnerup.common.wear.WorkoutSessionEventJournalStore;
import org.runnerup.common.wear.WorkoutSessionProtocol;
import org.runnerup.common.wear.WorkoutSessionRecoveryPolicy;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Formatter;
import org.runnerup.wear.WearableClient;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutInfo;
import org.runnerup.workout.WorkoutStepListener;

@SuppressWarnings("deprecation")
public class TrackerWear extends DefaultTrackerComponent
    implements Constants,
        TrackerComponent,
        WorkoutObserver,
        MessageApi.MessageListener,
        DataApi.DataListener,
        WorkoutStepListener,
        ValueModel.ChangeListener<TrackerState> {

  public static final String NAME = "WEAR";
  private final Tracker tracker;
  private Context context;
  private WorkoutSessionEventJournalStore sessionEventStore;
  private GoogleApiClient mGoogleApiClient;
  private WearableClient mWearableClient;
  private Formatter formatter;
  private String wearNode;
  private String requestedSessionId;
  private long lastSessionCommandSequence;
  private boolean sessionStartAnnounced;
  private WorkoutSessionProtocol.SessionState workoutSessionState;
  private WorkoutSessionProtocol.Command lastAcceptedWatchCommand;
  private WorkoutSessionProtocol.Command scheduledWatchStart;
  private boolean scheduledWatchStartHeld;
  private WorkoutSessionProtocol.Command pendingPhoneSessionRequest;
  private int pendingPhoneSessionRequestAttempts;

  private final Handler handler = new Handler();
  private final Runnable scheduledWatchStartRunnable = this::startScheduledWatchSession;
  private final Runnable pendingPhoneSessionRequestRetry = this::retryPendingPhoneSessionRequest;
  private static final int MAX_PHONE_SESSION_REQUEST_ATTEMPTS = 5;
  private static final long PHONE_SESSION_REQUEST_RETRY_DELAY_MS = 1_000L;
  private Bundle lastCreatedWorkoutEvent;
  private Bundle lastSentWorkoutEvent;
  private boolean mWorkoutSenderRunning = false;

  private final ArrayList<Integer> screenSizes = new ArrayList<>();
  private final List<List<Pair<Pair<Scope, Dimension>, Formatter.Format>>> screens =
      new ArrayList<>(3);
  private Step currentStep;
  private boolean pauseStep;
  private int workoutType;
  private Intensity intensity;

  public TrackerWear(Tracker tracker) {
    this.tracker = tracker;

    // Wear now supports arbitrary no of screens with 1-3 items per screen
    // and automatically scrolls between them
    initBasicScreens();
  }

  private void initBasicScreens() {
    // TODO read this from settings!!
    screens.clear();
    screenSizes.clear();
    {
      ArrayList<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen = new ArrayList<>();
      screen.add(
          new Pair<>(new Pair<>(Scope.ACTIVITY, Dimension.TIME), Formatter.Format.TXT_SHORT));
      screen.add(
          new Pair<>(new Pair<>(Scope.ACTIVITY, Dimension.DISTANCE), Formatter.Format.TXT_SHORT));
      screen.add(new Pair<>(new Pair<>(Scope.LAP, Dimension.PACE), Formatter.Format.TXT_SHORT));
      screens.add(screen);
    }
    {
      ArrayList<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen = new ArrayList<>();
      screen.add(
          new Pair<>(
              new Pair<>(Scope.CURRENT, Dimension.TIME),
              Formatter.Format.TXT_TIMESTAMP)); // I.e time of day
      screens.add(screen);
    }
    for (List<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen : screens) {
      screenSizes.add(screen.size());
    }
  }

  private void initIntervalScreens() {
    // TODO read this from settings!!
    screens.clear();
    screenSizes.clear();
    {
      ArrayList<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen = new ArrayList<>();
      screen.add(new Pair<>(new Pair<>(Scope.STEP, Dimension.TIME), Formatter.Format.TXT_SHORT));
      screen.add(
          new Pair<>(new Pair<>(Scope.STEP, Dimension.DISTANCE), Formatter.Format.TXT_SHORT));
      screen.add(new Pair<>(new Pair<>(Scope.LAP, Dimension.PACE), Formatter.Format.TXT_SHORT));
      screens.add(screen);
    }
    for (List<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen : screens) {
      screenSizes.add(screen.size());
    }
  }

  private void initWarmupCooldownScreens() {
    // TODO read this from settings!!
    screens.clear();
    screenSizes.clear();
    {
      ArrayList<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen = new ArrayList<>();
      screen.add(
          new Pair<>(new Pair<>(Scope.ACTIVITY, Dimension.TIME), Formatter.Format.TXT_SHORT));
      screen.add(
          new Pair<>(new Pair<>(Scope.ACTIVITY, Dimension.DISTANCE), Formatter.Format.TXT_SHORT));
      screen.add(
          new Pair<>(
              new Pair<>(Scope.CURRENT, Dimension.TIME),
              Formatter.Format.TXT_TIMESTAMP)); // I.e time of day
      screens.add(screen);
    }
    for (List<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen : screens) {
      screenSizes.add(screen.size());
    }
  }

  @Override
  public String getName() {
    return NAME;
  }

  private boolean hasPlay() {
    int result;
    try {
      result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    } catch (Exception e) {
      return false;
    }
    return (result == ConnectionResult.SUCCESS);
  }

  @Override
  public TrackerComponent.ResultCode onInit(final Callback callback, Context context) {
    this.context = context;
    sessionEventStore = new WorkoutSessionEventJournalStore(context.getApplicationContext());
    if (!hasPlay()) {
      return ResultCode.RESULT_NOT_SUPPORTED;
    }

    tracker.registerTrackerStateListener(this);
    mWearableClient = new WearableClient(context);
    mGoogleApiClient =
        new GoogleApiClient.Builder(context)
            .addConnectionCallbacks(
                new GoogleApiClient.ConnectionCallbacks() {
                  @Override
                  public void onConnected(Bundle connectionHint) {
                    callback.run(TrackerWear.this, ResultCode.RESULT_OK);

                    /* set "our" data */
                    setData();

                    Wearable.MessageApi.addListener(mGoogleApiClient, TrackerWear.this);
                    Wearable.DataApi.addListener(mGoogleApiClient, TrackerWear.this);

                    /* read already existing data */
                    readData();

                    /* get info about connected nodes in background */
                  }

                  @Override
                  public void onConnectionSuspended(int cause) {}
                })
            .addOnConnectionFailedListener(
                result -> callback.run(TrackerWear.this, ResultCode.RESULT_ERROR))
            .addApi(Wearable.API)
            .build();
    mGoogleApiClient.connect();
    return ResultCode.RESULT_PENDING;
  }

  private void readData() {
    mWearableClient.readData(
        Wear.Path.WEAR_NODE_ID,
        dataItem -> {
          if (dataItem != null) {
            wearNode = dataItem.getUri().getHost();
            Log.d(getName(), "getDataItem => wearNode:" + wearNode);
          }
        });
  }

  private void setData() {
    mWearableClient.putData(Constants.Wear.Path.PHONE_NODE_ID);
  }

  @Override
  public void onBind(HashMap<String, Object> bindValues) {
    formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
    workoutType = (Integer) bindValues.get(Workout.KEY_WORKOUT_TYPE);
    switch (workoutType) {
      case WORKOUT_TYPE.INTERVAL:
      case WORKOUT_TYPE.ADVANCED:
        initIntervalScreens();
        Log.d("TrackerWear::onBind()", "initIntervalScreens()");
        break;
      default:
      case WORKOUT_TYPE.BASIC:
        initBasicScreens();
        Log.d("TrackerWear::onBind()", "initBasicScreens()");
        break;
    }
  }

  @Override
  public void onStart() {
    updateHeaders();
    setTrackerState(tracker.getState());
    tracker.getWorkout().registerWorkoutStepListener(this);
  }

  private void setTrackerState(TrackerState val) {
    Log.d(getName(), "setTrackerState(" + val + ")");
    Bundle b = new Bundle();
    b.putInt(Wear.TrackerState.STATE, val.getValue());
    setData(Wear.Path.TRACKER_STATE, b);
  }

  private void setData(String path, Bundle b) {
    mWearableClient.putData(path, b);
  }

  @Override
  public void workoutEvent(WorkoutInfo workoutInfo, int type) {
    switch (workoutType) {
      case WORKOUT_TYPE.BASIC:
        break;
      case WORKOUT_TYPE.ADVANCED:
      case WORKOUT_TYPE.INTERVAL:
        setScreensBasedOnIntensity(workoutInfo.getIntensity());
        break;
    }
    switch (type) {
      case DB.LOCATION.TYPE_START:
      case DB.LOCATION.TYPE_RESUME:
        setTrackerState(TrackerState.STARTED);
        break;
      case DB.LOCATION.TYPE_PAUSE:
        setTrackerState(TrackerState.PAUSED);
      case DB.LOCATION.TYPE_END:
        break;
    }

    Bundle b = new Bundle();
    {
      int screenNo = 0;
      for (List<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen : screens) {
        int itemNo = 0;
        String itemPrefix = screenNo + ".";
        for (Pair<Pair<Scope, Dimension>, Formatter.Format> item : screen) {
          b.putString(
              Wear.RunInfo.DATA + itemPrefix + itemNo,
              formatter.format(
                  item.second,
                  item.first.second,
                  workoutInfo.get(item.first.first, item.first.second)));
          itemNo++;
        }
        screenNo++;
      }
    }

    lastCreatedWorkoutEvent = b;
  }

  private void setScreensBasedOnIntensity(Intensity intensity) {
    if (this.intensity == intensity) return;
    this.intensity = intensity;
    switch (intensity) {
      case ACTIVE:
      case RESTING:
      case RECOVERY:
      case REPEAT:
        initIntervalScreens();
        break;
      case WARMUP:
      case COOLDOWN:
        initWarmupCooldownScreens();
        break;
    }
  }

  private void sendWorkoutEvent() {
    if (!isConnected()) return;

    /* special handling of pauseStep */
    if (pauseStep) {
      if (lastCreatedWorkoutEvent == null) lastCreatedWorkoutEvent = lastSentWorkoutEvent;

      if (lastCreatedWorkoutEvent == null) {
        lastCreatedWorkoutEvent = new Bundle();
      }

      Dimension dim = currentStep.getDurationType();
      if (dim != null) {
        double remaining = tracker.getWorkout().getRemaining(Scope.STEP, dim);
        if (remaining < 0) {
          remaining = 0;
        }
        lastCreatedWorkoutEvent.putString(
            Wear.RunInfo.COUNTDOWN,
            formatter.formatRemaining(Formatter.Format.TXT_SHORT, dim, remaining));
      }
    }

    if (lastCreatedWorkoutEvent != null) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          wearNode,
          Wear.Path.MSG_WORKOUT_EVENT,
          DataMap.fromBundle(lastCreatedWorkoutEvent).toByteArray());
      lastSentWorkoutEvent = lastCreatedWorkoutEvent;
      lastCreatedWorkoutEvent = null;
    }
  }

  private final Runnable workoutEventSender =
      new Runnable() {
        @Override
        public void run() {
          sendWorkoutEvent();
          mWorkoutSenderRunning = false;

          if (!isConnected()) return;

          if (currentStep == null) return;

          mWorkoutSenderRunning = true;
          long tickFrequencyPause = 500; // so that seconds does show "slowly"
          long tickFrequency = 1000;
          handler.postDelayed(workoutEventSender, pauseStep ? tickFrequencyPause : tickFrequency);
        }
      };

  @Override
  public void onStepChanged(Step oldStep, Step newStep) {
    currentStep = newStep;

    if (!mWorkoutSenderRunning) {
      // this starts workout sender
      workoutEventSender.run();
    }

    if (currentStep == null) {
      return; // this is end
    }

    updateHeaders();
  }

  private void updateHeaders() {
    Bundle b = new Bundle();

    if (currentStep != null && currentStep.getIntensity() != null) {
      b.putString(
          Wear.RunInfo.CURRENT_STEP, context.getString(currentStep.getIntensity().getTextId()));
    }

    int screenNo = 0;
    for (List<Pair<Pair<Scope, Dimension>, Formatter.Format>> screen : screens) {
      int itemNo = 0;
      String itemPrefix = screenNo + ".";
      for (Pair<Pair<Scope, Dimension>, Formatter.Format> item : screen) {
        b.putString(
            Wear.RunInfo.HEADER + itemPrefix + itemNo,
            context.getString(item.first.second.getTextId()));
        itemNo++;
      }
      screenNo++;
    }

    pauseStep = false;
    if (currentStep != null && currentStep.isPauseStep()) {
      pauseStep = true;
      b.putBoolean(Wear.RunInfo.PAUSE_STEP, true);
    }

    b.putIntegerArrayList(Wear.RunInfo.SCREENS, screenSizes);
    b.putInt(Wear.RunInfo.SCROLL, 5); // 5 seconds
    setData(Wear.Path.HEADERS, b);
  }

  @Override
  public void onComplete(boolean discarded) {
    cancelScheduledWatchSessionStart();
    tracker.getWorkout().unregisterWorkoutStepListener(this);
    currentStep = null;

    clearData(/* don't clear own node id */ false);
  }

  @Override
  public boolean isConnected() {
    if (mGoogleApiClient == null) return false;

    if (!mGoogleApiClient.isConnected()) return false;

    return wearNode != null;
  }

  @Override
  public void onMessageReceived(final MessageEvent messageEvent) {
    Log.d(getName(), "onMessageReceived: " + messageEvent);
    if (Wear.Path.MSG_SESSION_CLOCK_REQUEST.contentEquals(messageEvent.getPath())) {
      handleClockRequest(messageEvent);
      return;
    }
    if (Wear.Path.MSG_WORKOUT_SESSION_COMMAND.contentEquals(messageEvent.getPath())) {
      handleSessionCommand(messageEvent);
      return;
    }
    if (Wear.Path.MSG_WORKOUT_SESSION_REJECT.contentEquals(messageEvent.getPath())) {
      handleRejectedSessionCommand(messageEvent);
      return;
    }
    // note: skip state checking, do that in receiver instead
    if (Wear.Path.MSG_CMD_WORKOUT_PAUSE.contentEquals(messageEvent.getPath())) {
      sendBroadcast(Intents.PAUSE_WORKOUT);
    } else if (Wear.Path.MSG_CMD_WORKOUT_RESUME.contentEquals(messageEvent.getPath())) {
      sendBroadcast(Intents.RESUME_WORKOUT);
    } else if (Wear.Path.MSG_CMD_WORKOUT_NEW_LAP.contentEquals(messageEvent.getPath())) {
      sendBroadcast(Intents.NEW_LAP);
    } else if (Wear.Path.MSG_CMD_WORKOUT_FINISH.contentEquals(messageEvent.getPath())) {
      sendBroadcast(Intents.STOP_WORKOUT);
    } else if (Wear.Path.MSG_CMD_WORKOUT_START.contentEquals(messageEvent.getPath())) {
      /* send broadcast to start the workout */
      sendBroadcast(Intents.START_WORKOUT);
    }
  }

  private void handleRejectedSessionCommand(MessageEvent event) {
    if (wearNode == null || !wearNode.equals(event.getSourceNodeId())) return;
    try {
      WorkoutSessionProtocol.Command command =
          WorkoutSessionProtocol.decodeCommand(event.getData());
      if (command.getActor() != WorkoutSessionProtocol.Actor.WATCH
          && command.getActor() != WorkoutSessionProtocol.Actor.PHONE) return;
      clearPendingPhoneSessionRequest(command);
      Intent intent =
          new Intent()
              .setPackage(context.getPackageName())
              .setAction(Constants.Intents.WORKOUT_SESSION_COMMAND_REJECTED)
              .putExtra(
                  Constants.Intents.EXTRA_WORKOUT_SESSION_COMMAND,
                  WorkoutSessionProtocol.encodeCommand(command));
      context.sendBroadcast(intent);
    } catch (java.io.IOException malformed) {
      Log.w(getName(), "Ignoring malformed session rejection", malformed);
    }
  }

  private void handleClockRequest(MessageEvent event) {
    if (wearNode == null
        || !wearNode.equals(event.getSourceNodeId())
        || mGoogleApiClient == null
        || !mGoogleApiClient.isConnected()) return;
    long phoneReceivedAt = android.os.SystemClock.elapsedRealtime();
    try {
      org.runnerup.common.wear.WearClockSyncProtocol.Request request =
          org.runnerup.common.wear.WearClockSyncProtocol.decodeRequest(event.getData());
      long phoneSentAt = android.os.SystemClock.elapsedRealtime();
      byte[] reply =
          org.runnerup.common.wear.WearClockSyncProtocol.encodeReply(
              request.getRequestId(),
              request.getWatchSentAtElapsedRealtimeMs(),
              phoneReceivedAt,
              phoneSentAt);
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, event.getSourceNodeId(), Wear.Path.MSG_SESSION_CLOCK_REPLY, reply);
    } catch (java.io.IOException malformed) {
      Log.w(getName(), "Ignoring invalid clock synchronization request", malformed);
    }
  }

  private void handleSessionCommand(MessageEvent event) {
    if (wearNode == null || !wearNode.equals(event.getSourceNodeId())) {
      Log.w(getName(), "Ignoring session command from an unverified watch node");
      return;
    }
    final WorkoutSessionProtocol.Command command;
    try {
      command = WorkoutSessionProtocol.decodeCommand(event.getData());
    } catch (java.io.IOException malformed) {
      Log.w(getName(), "Ignoring malformed session command", malformed);
      return;
    }
    if (command.getActor() != WorkoutSessionProtocol.Actor.WATCH) {
      Log.w(getName(), "Ignoring stale or non-watch session command");
      return;
    }
    if (isAcceptedDuplicate(command)) {
      clearPendingPhoneSessionRequest(command);
      if (command.getAction() == WorkoutSessionProtocol.Action.START) {
        if (tracker.getState() == TrackerState.CONNECTED
            && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.COUNTDOWN) {
          scheduleWatchSessionStart(command);
        } else if (tracker.getState() == TrackerState.STARTED
            || tracker.getState() == TrackerState.PAUSED) {
          // The persisted start is already reflected in the RunnerUp tracker.
        } else {
          return;
        }
        acknowledgeWatchSessionCommand(command);
      } else {
        if (WorkoutSessionRecoveryPolicy.shouldApplyControl(
            command.getAction(), tracker.getState())) {
          tracker.applyWatchSessionAction(command.getAction());
        }
        acknowledgeWatchSessionCommand(command);
      }
      if (command.getAction() == WorkoutSessionProtocol.Action.START
          || command.getAction() == WorkoutSessionProtocol.Action.CANCEL) {
        try {
          relaySessionCommandToPhoneUi(command);
        } catch (java.io.IOException error) {
          Log.w(getName(), "Unable to relay duplicate preparation command", error);
        }
      }
      return;
    }
    if (command.getAction() == WorkoutSessionProtocol.Action.START) {
      if (tracker.getState() != TrackerState.CONNECTED) {
        Log.w(getName(), "Ignoring start request without the matching prepared session");
        rejectWatchSessionStart(command);
        return;
      }
      if (workoutSessionState == null
          || !workoutSessionState.getSessionId().equals(command.getSessionId())) {
        Log.w(getName(), "Ignoring start request for a different prepared session");
        rejectWatchSessionCommand(command);
        return;
      }
      if (command.getPlannedStartElapsedRealtimeMs() <= command.getIssuedAtElapsedRealtimeMs()
          || command.getPlannedStartElapsedRealtimeMs() - command.getIssuedAtElapsedRealtimeMs()
              > 10_000L
          || !WorkoutSessionProtocol.isStartDeadlineInFuture(
              command, android.os.SystemClock.elapsedRealtime())) {
        Log.w(getName(), "Ignoring a start command with an invalid monotonic deadline");
        rejectWatchSessionStart(command);
        return;
      }
      WorkoutSessionProtocol.Command scheduled = command;
      WorkoutSessionProtocol.Result result = applySessionCommand(scheduled);
      if (result != WorkoutSessionProtocol.Result.ACCEPTED) {
        rejectWatchSessionStart(command);
        return;
      }
      requestedSessionId = command.getSessionId();
      clearPendingPhoneSessionRequest(command);
      scheduleWatchSessionStart(scheduled);
      try {
        relaySessionCommandToPhoneUi(scheduled);
        acknowledgeWatchSessionCommand(scheduled);
      } catch (java.io.IOException error) {
        Log.w(getName(), "Unable to relay start request", error);
      }
      return;
    }
    if (command.getAction() == WorkoutSessionProtocol.Action.CANCEL) {
      if (requestedSessionId == null || !requestedSessionId.equals(command.getSessionId())) return;
      WorkoutSessionProtocol.Result result = applySessionCommand(command);
      if (result == WorkoutSessionProtocol.Result.ACCEPTED) {
        clearPendingPhoneSessionRequest(command);
        cancelScheduledWatchSessionStart();
        try {
          relaySessionCommandToPhoneUi(command);
          acknowledgeWatchSessionCommand(command);
        } catch (java.io.IOException error) {
          Log.w(getName(), "Unable to relay cancellation to the preparation screen", error);
        }
      }
      return;
    }
    if (requestedSessionId == null || !requestedSessionId.equals(command.getSessionId())) {
      Log.w(getName(), "Ignoring session command for a different session");
      return;
    }
    // Validate against the real Tracker state before advancing the protocol reducer.
    // Duplicate accepted commands were acknowledged above without repeating their side effect.
    switch (command.getAction()) {
      case PAUSE:
      case LAP:
      case SKIP:
        if (tracker.getState() != TrackerState.STARTED) return;
        break;
      case RESUME:
        if (tracker.getState() != TrackerState.PAUSED) return;
        break;
      case FINISH:
        if (tracker.getState() != TrackerState.STARTED && tracker.getState() != TrackerState.PAUSED)
          return;
        break;
      default:
        return;
    }
    WorkoutSessionProtocol.Result result = applySessionCommand(command);
    if (result != WorkoutSessionProtocol.Result.ACCEPTED) return;
    clearPendingPhoneSessionRequest(command);
    tracker.applyWatchSessionAction(command.getAction());
    acknowledgeWatchSessionCommand(command);
  }

  private boolean isAcceptedDuplicate(WorkoutSessionProtocol.Command command) {
    return workoutSessionState != null
        && workoutSessionState.getSessionId().equals(command.getSessionId())
        && command.getSequence() == workoutSessionState.getLastSequence()
        && command.getCommandId().equals(workoutSessionState.getLastCommandId());
  }

  private void scheduleWatchSessionStart(WorkoutSessionProtocol.Command command) {
    cancelScheduledWatchSessionStart();
    scheduledWatchStart = command;
    scheduleWatchSessionStartAtDeadline(command);
  }

  private void scheduleWatchSessionStartAtDeadline(WorkoutSessionProtocol.Command command) {
    long delay =
        Math.max(
            0L,
            command.getPlannedStartElapsedRealtimeMs() - android.os.SystemClock.elapsedRealtime());
    handler.postDelayed(scheduledWatchStartRunnable, delay);
  }

  private void startScheduledWatchSession() {
    WorkoutSessionProtocol.Command command = scheduledWatchStart;
    if (command == null) return;
    if (scheduledWatchStartHeld) return;
    if (workoutSessionState == null
        || workoutSessionState.getPhase() != WorkoutSessionProtocol.Phase.COUNTDOWN
        || !command.getCommandId().equals(workoutSessionState.getLastCommandId())
        || !command.getSessionId().equals(requestedSessionId)) {
      scheduledWatchStart = null;
      return;
    }
    boolean started = false;
    try {
      started = tracker.startIfConnected();
    } catch (RuntimeException error) {
      Log.e(getName(), "Unable to start Tracker at the accepted watch deadline", error);
    }
    if (!started) {
      Log.w(getName(), "Accepted watch start reached its deadline without a prepared Tracker");
      rejectWatchSessionCommand(command);
      workoutSessionState =
          WorkoutSessionProtocol.SessionState.ready(
              command.getSessionId(), WorkoutSessionProtocol.Actor.WATCH);
      lastSessionCommandSequence = 0L;
      lastAcceptedWatchCommand = null;
      sessionStartAnnounced = false;
      broadcastRejectedSessionCommand(command, WorkoutSessionProtocol.Actor.WATCH);
    }
    scheduledWatchStart = null;
  }

  private void cancelScheduledWatchSessionStart() {
    handler.removeCallbacks(scheduledWatchStartRunnable);
    scheduledWatchStart = null;
    scheduledWatchStartHeld = false;
  }

  private void retryPendingPhoneSessionRequest() {
    WorkoutSessionProtocol.Command request = pendingPhoneSessionRequest;
    if (request == null) return;
    if (pendingPhoneSessionRequestAttempts >= MAX_PHONE_SESSION_REQUEST_ATTEMPTS) {
      Log.w(
          getName(), "Phone session request received no matching watch response; stopping retries");
      pendingPhoneSessionRequest = null;
      pendingPhoneSessionRequestAttempts = 0;
      return;
    }
    if (wearNode != null && mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
      try {
        Wearable.MessageApi.sendMessage(
            mGoogleApiClient,
            wearNode,
            Wear.Path.MSG_WORKOUT_SESSION_COMMAND,
            WorkoutSessionProtocol.encodeCommand(request));
      } catch (java.io.IOException error) {
        Log.w(getName(), "Unable to retry phone session request", error);
      }
    }
    pendingPhoneSessionRequestAttempts++;
    handler.postDelayed(pendingPhoneSessionRequestRetry, PHONE_SESSION_REQUEST_RETRY_DELAY_MS);
  }

  private void clearPendingPhoneSessionRequest(WorkoutSessionProtocol.Command response) {
    WorkoutSessionProtocol.Command pending = pendingPhoneSessionRequest;
    if (pending == null
        || response == null
        || !pending.getSessionId().equals(response.getSessionId())
        || !pending.getCommandId().equals(response.getCommandId())
        || pending.getSequence() != response.getSequence()
        || pending.getAction() != response.getAction()) return;
    handler.removeCallbacks(pendingPhoneSessionRequestRetry);
    pendingPhoneSessionRequest = null;
    pendingPhoneSessionRequestAttempts = 0;
  }

  public void holdScheduledWatchSessionStart() {
    if (scheduledWatchStart != null) {
      scheduledWatchStartHeld = true;
      handler.removeCallbacks(scheduledWatchStartRunnable);
    }
  }

  public void releaseScheduledWatchSessionStart() {
    if (scheduledWatchStart == null || !scheduledWatchStartHeld) return;
    scheduledWatchStartHeld = false;
    scheduleWatchSessionStartAtDeadline(scheduledWatchStart);
  }

  private void acknowledgeWatchSessionCommand(WorkoutSessionProtocol.Command command) {
    if (wearNode == null || mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;
    try {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          wearNode,
          Wear.Path.MSG_WORKOUT_SESSION_ACK,
          WorkoutSessionProtocol.encodeCommand(command));
    } catch (java.io.IOException error) {
      Log.w(getName(), "Unable to acknowledge watch session command", error);
    }
  }

  private void rejectWatchSessionCommand(WorkoutSessionProtocol.Command command) {
    if (wearNode == null || mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;
    try {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          wearNode,
          Wear.Path.MSG_WORKOUT_SESSION_REJECT,
          WorkoutSessionProtocol.encodeCommand(command));
    } catch (java.io.IOException error) {
      Log.w(getName(), "Unable to reject watch session command", error);
    }
  }

  private void rejectWatchSessionStart(WorkoutSessionProtocol.Command command) {
    rejectWatchSessionCommand(command);
    // A phone-start request is still pending until this watch projection is accepted.
    broadcastRejectedSessionCommand(command, WorkoutSessionProtocol.Actor.PHONE);
  }

  private void broadcastRejectedSessionCommand(
      WorkoutSessionProtocol.Command command, WorkoutSessionProtocol.Actor actor) {
    try {
      WorkoutSessionProtocol.Command rejection =
          new WorkoutSessionProtocol.Command(
              command.getSessionId(),
              command.getCommandId(),
              command.getSequence(),
              actor,
              command.getAction(),
              command.getIssuedAtElapsedRealtimeMs(),
              command.getPlannedStartElapsedRealtimeMs());
      Intent intent =
          new Intent()
              .setPackage(context.getPackageName())
              .setAction(Constants.Intents.WORKOUT_SESSION_COMMAND_REJECTED)
              .putExtra(
                  Constants.Intents.EXTRA_WORKOUT_SESSION_COMMAND,
                  WorkoutSessionProtocol.encodeCommand(rejection));
      context.sendBroadcast(intent);
    } catch (java.io.IOException malformed) {
      Log.w(getName(), "Unable to notify the preparation screen of a rejected start", malformed);
    }
  }

  private void relaySessionCommandToPhoneUi(WorkoutSessionProtocol.Command command)
      throws java.io.IOException {
    Intent intent =
        new Intent()
            .setPackage(context.getPackageName())
            .setAction(Constants.Intents.WORKOUT_SESSION_COMMAND)
            .putExtra(
                Constants.Intents.EXTRA_WORKOUT_SESSION_COMMAND,
                WorkoutSessionProtocol.encodeCommand(command));
    context.sendBroadcast(intent);
  }

  private void sendBroadcast(String action) {
    Intent intent = new Intent().setPackage(context.getPackageName()).setAction(action);
    context.sendBroadcast(intent);
  }

  @Override
  public ResultCode onEnd(Callback callback, Context context) {
    handler.removeCallbacks(pendingPhoneSessionRequestRetry);
    pendingPhoneSessionRequest = null;
    pendingPhoneSessionRequestAttempts = 0;
    requestedSessionId = null;
    lastSessionCommandSequence = 0L;
    sessionStartAnnounced = false;
    workoutSessionState = null;
    if (mGoogleApiClient != null) {
      if (mGoogleApiClient.isConnected()) {
        clearData(true);
        wearNode = null;

        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        // connectedNodes.clear();
      }
      mGoogleApiClient.disconnect();
      mGoogleApiClient = null;
    }
    if (mWearableClient != null) {
      mWearableClient = null;
    }
    tracker.unregisterTrackerStateListener(this);
    return ResultCode.RESULT_OK;
  }

  private void clearData(boolean self) {
    if (self) {
      /* clear our node id */
      mWearableClient.deleteData(Wear.Path.PHONE_NODE_ID);
    }

    /* clear HEADERS */
    mWearableClient.deleteData(Wear.Path.HEADERS);

    /* clear WORKOUT PLAN */
    mWearableClient.deleteData(Wear.Path.WORKOUT_PLAN);

    /* clear TRACKER_STATE */
    mWearableClient.deleteData(Wear.Path.TRACKER_STATE);
  }

  @Override
  public void onDataChanged(final DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      Log.d(getName(), "onDataChanged: " + ev.getDataItem().getUri());
      String path = ev.getDataItem().getUri().getPath();
      if (Constants.Wear.Path.WEAR_NODE_ID.contentEquals(path)) {
        setWearNode(ev);
      }
    }
  }

  private void setWearNode(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      wearNode = ev.getDataItem().getUri().getHost();
      if (lastCreatedWorkoutEvent == null) {
        lastCreatedWorkoutEvent = lastSentWorkoutEvent;
      }
      if (!mWorkoutSenderRunning) workoutEventSender.run();
      else sendWorkoutEvent();
      announceSessionStarted();
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      wearNode = null;
    }
  }

  @Override
  public void onValueChanged(
      ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {
    setTrackerState(newValue);
    reflectTrackerState(newValue);
    if (newValue == TrackerState.STARTED) announceSessionStarted();
  }

  public void prepareWorkoutSession(String sessionId, boolean watchReady) {
    if (sessionId == null || sessionId.isBlank()) return;
    if (!sessionId.equals(requestedSessionId)) {
      cancelScheduledWatchSessionStart();
      requestedSessionId = sessionId;
      lastSessionCommandSequence = 0L;
      sessionStartAnnounced = false;
      lastAcceptedWatchCommand = null;
      WorkoutSessionProtocol.Actor authority =
          watchReady ? WorkoutSessionProtocol.Actor.WATCH : WorkoutSessionProtocol.Actor.PHONE;
      WorkoutSessionEventJournal journal = sessionEventStore.load(sessionId);
      if (journal != null && journal.getAuthority() == authority) {
        workoutSessionState = journal.replayState();
        lastSessionCommandSequence = journal.lastSequence();
        if (!journal.events().isEmpty()) {
          lastAcceptedWatchCommand = journal.events().get(journal.events().size() - 1);
        }
      } else {
        workoutSessionState = WorkoutSessionProtocol.SessionState.ready(sessionId, authority);
      }
      scheduleRestoredWatchSessionStart(journal);
    }
    if (tracker.getState() == TrackerState.STARTED) announceSessionStarted();
  }

  private void scheduleRestoredWatchSessionStart(WorkoutSessionEventJournal journal) {
    WorkoutSessionProtocol.Command start =
        WorkoutSessionRecoveryPolicy.startToRecover(journal, tracker.getState());
    if (start != null && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.COUNTDOWN) {
      scheduleWatchSessionStart(start);
    }
  }

  public void resetWorkoutSession(String sessionId, boolean watchReady) {
    cancelScheduledWatchSessionStart();
    handler.removeCallbacks(pendingPhoneSessionRequestRetry);
    pendingPhoneSessionRequest = null;
    pendingPhoneSessionRequestAttempts = 0;
    requestedSessionId = null;
    workoutSessionState = null;
    lastSessionCommandSequence = 0L;
    sessionStartAnnounced = false;
    prepareWorkoutSession(sessionId, watchReady);
  }

  public WorkoutSessionProtocol.Result applySessionCommand(WorkoutSessionProtocol.Command command) {
    if (workoutSessionState == null
        || command == null
        || !workoutSessionState.getSessionId().equals(command.getSessionId())) {
      return WorkoutSessionProtocol.Result.REJECTED;
    }
    WorkoutSessionProtocol.Transition transition =
        workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH
            ? WorkoutSessionProtocol.applyProjection(workoutSessionState, command)
            : WorkoutSessionProtocol.applyAtAuthority(
                workoutSessionState, WorkoutSessionProtocol.Actor.PHONE, command);
    if (transition.getResult() == WorkoutSessionProtocol.Result.ACCEPTED) {
      if (workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH) {
        WorkoutSessionEventJournalStore.AppendResult persisted =
            sessionEventStore.append(
                command.getSessionId(), WorkoutSessionProtocol.Actor.WATCH, command);
        if (persisted != WorkoutSessionEventJournalStore.AppendResult.APPENDED
            && persisted != WorkoutSessionEventJournalStore.AppendResult.DUPLICATE) {
          Log.e(getName(), "Unable to persist watch-authoritative workout event");
          return WorkoutSessionProtocol.Result.REJECTED;
        }
      }
      workoutSessionState = transition.getState();
      lastSessionCommandSequence = workoutSessionState.getLastSequence();
      if (workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH) {
        lastAcceptedWatchCommand = command;
      }
      if (workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.PHONE
          && command.getActor() == WorkoutSessionProtocol.Actor.PHONE) {
        publishPhoneSessionCommand(command);
      }
    }
    return transition.getResult();
  }

  public WorkoutSessionProtocol.Command getLastAcceptedWatchCommand() {
    return lastAcceptedWatchCommand;
  }

  public boolean requestPhoneSessionCommand(WorkoutSessionProtocol.Command command) {
    if (workoutSessionState == null
        || workoutSessionState.getAuthority() != WorkoutSessionProtocol.Actor.WATCH
        || command == null
        || command.getActor() != WorkoutSessionProtocol.Actor.PHONE
        || !workoutSessionState.getSessionId().equals(command.getSessionId())
        || wearNode == null
        || mGoogleApiClient == null
        || !mGoogleApiClient.isConnected()) return false;
    if (pendingPhoneSessionRequest != null) {
      if (!pendingPhoneSessionRequest.getCommandId().equals(command.getCommandId())) return false;
      handler.removeCallbacks(pendingPhoneSessionRequestRetry);
    } else {
      pendingPhoneSessionRequest = command;
      pendingPhoneSessionRequestAttempts = 0;
    }
    retryPendingPhoneSessionRequest();
    return true;
  }

  public boolean isWatchSessionAuthority() {
    return workoutSessionState != null
        && workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH;
  }

  /** Returns true when the phone must defer this action to the connected session authority. */
  public boolean requestPhoneSessionAction(WorkoutSessionProtocol.Action action) {
    if (!isWatchSessionAuthority()) return false;
    long now = android.os.SystemClock.elapsedRealtime();
    long sequence = workoutSessionState.getLastSequence() + 1L;
    WorkoutSessionProtocol.Command request =
        new WorkoutSessionProtocol.Command(
            requestedSessionId,
            java.util.UUID.randomUUID().toString(),
            sequence,
            WorkoutSessionProtocol.Actor.PHONE,
            action,
            now,
            0L);
    if (!requestPhoneSessionCommand(request)) {
      Log.w(getName(), "Watch-authoritative control is pending; not applying it on the phone");
    }
    return true;
  }

  private void publishPhoneSessionCommand(WorkoutSessionProtocol.Command command) {
    if (wearNode == null || mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;
    try {
      WorkoutSessionProtocol.Command projection = WorkoutSessionProtocol.phoneProjection(command);
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          wearNode,
          Wear.Path.MSG_WORKOUT_SESSION_COMMAND,
          WorkoutSessionProtocol.encodeCommand(projection));
    } catch (java.io.IOException error) {
      Log.w(getName(), "Unable to project session countdown to watch", error);
    }
  }

  private void reflectTrackerState(TrackerState trackerState) {
    if (workoutSessionState == null) return;
    if (trackerState == TrackerState.STARTED
        && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.COUNTDOWN) {
      WorkoutSessionProtocol.Transition advanced =
          WorkoutSessionProtocol.advance(
              workoutSessionState, android.os.SystemClock.elapsedRealtime());
      if (advanced.getResult() == WorkoutSessionProtocol.Result.ACCEPTED) {
        workoutSessionState = advanced.getState();
      }
      return;
    }
    if (workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH) return;
    WorkoutSessionProtocol.Action action = null;
    if (trackerState == TrackerState.PAUSED
        && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.ACTIVE) {
      action = WorkoutSessionProtocol.Action.PAUSE;
    } else if (trackerState == TrackerState.STARTED
        && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.PAUSED) {
      action = WorkoutSessionProtocol.Action.RESUME;
    } else if (trackerState == TrackerState.STOPPED
        && (workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.ACTIVE
            || workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.PAUSED)) {
      action = WorkoutSessionProtocol.Action.FINISH;
    }
    if (action != null) {
      long now = android.os.SystemClock.elapsedRealtime();
      WorkoutSessionProtocol.Command event =
          new WorkoutSessionProtocol.Command(
              requestedSessionId,
              java.util.UUID.randomUUID().toString(),
              workoutSessionState.getLastSequence() + 1L,
              workoutSessionState.getAuthority(),
              action,
              now,
              0L);
      applySessionCommand(event);
    }
  }

  private void announceSessionStarted() {
    if (sessionStartAnnounced
        || (workoutSessionState != null
            && workoutSessionState.getAuthority() == WorkoutSessionProtocol.Actor.WATCH)
        || requestedSessionId == null
        || wearNode == null
        || mGoogleApiClient == null
        || !mGoogleApiClient.isConnected()) return;
    try {
      WorkoutSessionProtocol.Command started =
          new WorkoutSessionProtocol.Command(
              requestedSessionId,
              java.util.UUID.randomUUID().toString(),
              Math.max(1L, lastSessionCommandSequence),
              WorkoutSessionProtocol.Actor.PHONE,
              WorkoutSessionProtocol.Action.START,
              android.os.SystemClock.elapsedRealtime(),
              0L);
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          wearNode,
          Wear.Path.MSG_WORKOUT_SESSION_COMMAND,
          WorkoutSessionProtocol.encodeCommand(started));
      sessionStartAnnounced = true;
    } catch (java.io.IOException | IllegalArgumentException error) {
      Log.w(getName(), "Unable to announce active workout session", error);
    }
  }

  public static class WearNotifier {
    private final WearableClient mWearableClient;

    public WearNotifier(Context context) {
      mWearableClient = new WearableClient(context);
    }

    public void onViewCreated() {}

    public void onResume() {
      mWearableClient.putData(Wear.Path.PHONE_APP);
    }

    public void onPause() {
      mWearableClient.deleteData(Wear.Path.PHONE_APP);
    }

    public void onDestroy() {}
  }
}
