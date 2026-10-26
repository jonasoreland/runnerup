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
package org.runnerup.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeClient;
import com.google.android.gms.wearable.Wearable;
import java.util.Objects;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.common.wear.StrengthResultInboxStore;
import org.runnerup.common.wear.StrengthResultOutboxStore;
import org.runnerup.common.wear.StrengthResultProtocol;
import org.runnerup.common.wear.WorkoutSessionEventJournal;
import org.runnerup.common.wear.WorkoutSessionEventJournalStore;
import org.runnerup.common.wear.WorkoutSessionProtocol;
import org.runnerup.common.wear.WorkoutSessionTerminalDeliveryPolicy;
import org.runnerup.view.MainActivity;
import org.runnerup.wear.WearableClient;

public class StateService extends Service
    implements MessageApi.MessageListener, DataApi.DataListener, ValueModel.ChangeListener<Bundle> {

  private static String TAG = "RU:StateService";
  public static final String UPDATE_TIME = "UPDATE_TIME";

  private final IBinder mBinder = new LocalBinder();
  private final android.os.Handler handler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private GoogleApiClient mGoogleApiClient;
  private WearableClient mDataClient;
  private String phoneNode;
  private String workoutSessionId;
  private boolean skipSupported;
  private long workoutCommandSequence;
  private WorkoutSessionProtocol.SessionState workoutSessionState;
  private WorkoutSessionProtocol.Command lastCommittedSessionCommand;
  private WorkoutSessionProtocol.Command pendingPhoneProjection;
  private int pendingProjectionAttempts;
  private final Runnable pendingProjectionRetry = this::retryPendingPhoneProjection;
  private static final long PROJECTION_RETRY_DELAY_MS = 1_000L;
  private static final int MAX_PROJECTION_ATTEMPTS = 8;
  private final ValueModel<Long> preparedCountdownDeadline = new ValueModel<>();
  private WorkoutSessionProtocol.ClockOffset watchClockOffset;
  private WorkoutSessionEventJournalStore sessionEventStore;
  private StrengthResultOutboxStore strengthResultOutbox;
  private StrengthResultInboxStore strengthResultInbox;
  private int strengthAuthorityProjectionRetryAttempt;
  private final Runnable strengthAuthorityProjectionRetry =
      this::publishStrengthAuthorityProjection;
  private int strengthResultRetryAttempt;
  private final Runnable strengthResultRetry = this::retryPendingStrengthResult;
  private long clockSampleReceivedAtWatchElapsedRealtimeMs = -1L;
  private String pendingClockRequestId;
  private long pendingClockRequestSentAtElapsedRealtimeMs = -1L;
  private Runnable clockRequestTimeout;
  private Bundle data;
  private final ValueModel<TrackerState> trackerState = new ValueModel<>();
  private final ValueModel<Bundle> headers = new ValueModel<>();
  private final ValueModel<WearConnectionStatus> connectionStatus =
      new ValueModel<>(WearConnectionStatus.CONNECTING);
  private int connectionFailureCount;
  private long phonePeerQueryGeneration;
  private boolean serviceDestroyed;
  private final Runnable connectionRetry = this::retryAfterDelay;
  private MainActivity headersListener;

  @Override
  public void onCreate() {
    super.onCreate();

    sessionEventStore = new WorkoutSessionEventJournalStore(getApplicationContext());
    strengthResultOutbox = new StrengthResultOutboxStore(getApplicationContext());
    strengthResultInbox = new StrengthResultInboxStore(getApplicationContext());

    mDataClient = new WearableClient(getApplicationContext());
    mGoogleApiClient =
        new GoogleApiClient.Builder(getApplicationContext())
            .addConnectionCallbacks(
                new GoogleApiClient.ConnectionCallbacks() {
                  @Override
                  public void onConnected(Bundle connectionHint) {
                    connectionFailureCount = 0;
                    handler.removeCallbacks(connectionRetry);
                    connectionStatus.set(WearConnectionStatus.CONNECTING);
                    /* set "our" data */
                    setData();

                    Wearable.MessageApi.addListener(mGoogleApiClient, StateService.this);
                    Wearable.DataApi.addListener(mGoogleApiClient, StateService.this);

                    /* read already existing data */
                    readData(/* force= */ true);
                    refreshPhonePeerStatus();
                    restorePendingSessionProjection();
                    retryPendingPhoneProjection();
                    retryPendingStrengthResult();
                    publishStrengthAuthorityProjection();
                  }

                  @Override
                  public void onConnectionSuspended(int cause) {
                    connectionStatus.set(WearConnectionStatus.DISCONNECTED);
                    scheduleConnectionRetry();
                  }
                })
            .addOnConnectionFailedListener(
                new GoogleApiClient.OnConnectionFailedListener() {
                  @Override
                  public void onConnectionFailed(@NonNull ConnectionResult result) {
                    connectionStatus.set(WearConnectionStatus.DISCONNECTED);
                    scheduleConnectionRetry();
                  }
                })
            .addApi(Wearable.API)
            .build();
    mGoogleApiClient.connect();
    this.headers.registerChangeListener(this);

    Log.d(TAG, "StateService.onCreate()");
  }

  private String checkConnection() {
    if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && phoneNode != null) {
      return null;
    }
    return "mGoogleApiClient="
        + mGoogleApiClient
        + ", isConnected="
        + (mGoogleApiClient != null ? mGoogleApiClient.isConnected() : false)
        + ", phoneNode="
        + phoneNode;
  }

  public ValueModel<WearConnectionStatus> getConnectionStatus() {
    return connectionStatus;
  }

  /** Retries the transport now, or refreshes the current snapshot if already connected. */
  public void retryConnection() {
    if (serviceDestroyed || mGoogleApiClient == null) return;
    handler.removeCallbacks(connectionRetry);
    if (mGoogleApiClient.isConnected()) {
      connectionStatus.set(WearConnectionStatus.CONNECTING);
      readData(/* force= */ true);
      refreshPhonePeerStatus();
      restorePendingSessionProjection();
      retryPendingPhoneProjection();
      retryPendingStrengthResult();
      publishStrengthAuthorityProjection();
      return;
    }
    if (mGoogleApiClient.isConnecting()) return;
    connectionStatus.set(WearConnectionStatus.CONNECTING);
    connectionFailureCount = 0;
    mGoogleApiClient.connect();
  }

  private void scheduleConnectionRetry() {
    if (serviceDestroyed || mGoogleApiClient == null) return;
    handler.removeCallbacks(connectionRetry);
    long delay = WearConnectionRetryPolicy.delayMillis(connectionFailureCount++);
    handler.postDelayed(connectionRetry, delay);
  }

  private void retryAfterDelay() {
    if (serviceDestroyed || mGoogleApiClient == null) return;
    if (mGoogleApiClient.isConnected()) {
      connectionStatus.set(WearConnectionStatus.CONNECTING);
      readData(/* force= */ true);
      refreshPhonePeerStatus();
      return;
    }
    if (!WearConnectionRetryPolicy.shouldRetry(
        mGoogleApiClient.isConnected(), mGoogleApiClient.isConnecting())) {
      scheduleConnectionRetry();
      return;
    }
    connectionStatus.set(WearConnectionStatus.CONNECTING);
    mGoogleApiClient.connect();
  }

  private void refreshPhonePeerStatus() {
    long queryGeneration = ++phonePeerQueryGeneration;
    if (serviceDestroyed || mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
      connectionStatus.set(WearConnectionStatus.DISCONNECTED);
      return;
    }
    connectionStatus.set(WearConnectionStatus.CONNECTING);
    try {
      NodeClient nodeClient = Wearable.getNodeClient(getApplicationContext());
      nodeClient
          .getConnectedNodes()
          .addOnCompleteListener(
              task -> {
                if (serviceDestroyed || queryGeneration != phonePeerQueryGeneration) return;
                if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
                  connectionStatus.set(WearConnectionStatus.DISCONNECTED);
                  scheduleConnectionRetry();
                  return;
                }
                if (!task.isSuccessful() || task.getResult() == null) {
                  connectionStatus.set(WearConnectionStatus.DISCONNECTED);
                  scheduleConnectionRetry();
                  return;
                }
                java.util.List<String> connectedNodeIds = new java.util.ArrayList<>();
                for (Node node : task.getResult()) connectedNodeIds.add(node.getId());
                boolean peerConnected =
                    WearPeerAvailability.isPhonePeerConnected(phoneNode, connectedNodeIds);
                connectionStatus.set(WearPeerAvailability.status(true, peerConnected));
                if (peerConnected) {
                  connectionFailureCount = 0;
                  handler.removeCallbacks(connectionRetry);
                } else {
                  scheduleConnectionRetry();
                }
              });
    } catch (RuntimeException error) {
      Log.w(TAG, "Unable to check connected phone peer", error);
      connectionStatus.set(WearConnectionStatus.DISCONNECTED);
      scheduleConnectionRetry();
    }
  }

  public void readDataIfMissing() {
    readData(/* force= */ false);
  }

  private void readData(boolean force) {
    if (force || phoneNode == null) {
      mDataClient.readData(
          Constants.Wear.Path.PHONE_NODE_ID,
          dataItem -> {
            if (dataItem != null) {
              phoneNode = dataItem.getUri().getHost();
              Log.d(TAG, "getDataItem => phoneNode:" + phoneNode);
              requestClockSync();
            } else {
              phoneNode = null;
            }
            refreshPhonePeerStatus();
          });
    }
    if (force || trackerState.get() == null) {
      mDataClient.readData(
          Constants.Wear.Path.TRACKER_STATE,
          dataItem -> {
            if (dataItem != null) {
              TrackerState newState = getTrackerStateFromDataItem(dataItem);
              if (newState != null) {
                setTrackerState(newState);
              }
            }
          });
    }
    if (force || headers.get() == null) {
      mDataClient.readData(
          Constants.Wear.Path.HEADERS,
          dataItem -> {
            if (dataItem != null) {
              Bundle b = DataMapItem.fromDataItem(dataItem).getDataMap().toBundle();
              b.putLong(UPDATE_TIME, System.currentTimeMillis());
              headers.set(b);
            }
          });
    }
  }

  private void setData() {
    /* set our node id */
    mDataClient.putData(Constants.Wear.Path.WEAR_NODE_ID);
  }

  private void requestClockSync() {
    if (pendingClockRequestId != null || checkConnection() != null) return;
    long nowElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
    if (watchClockOffset != null
        && clockSampleReceivedAtWatchElapsedRealtimeMs >= 0
        && nowElapsedRealtimeMs - clockSampleReceivedAtWatchElapsedRealtimeMs <= 600_000L) return;
    pendingClockRequestId = java.util.UUID.randomUUID().toString();
    String requestId = pendingClockRequestId;
    pendingClockRequestSentAtElapsedRealtimeMs = nowElapsedRealtimeMs;
    handler.removeCallbacks(clockRequestTimeout);
    clockRequestTimeout =
        () -> {
          if (requestId.equals(pendingClockRequestId)) {
            pendingClockRequestId = null;
            pendingClockRequestSentAtElapsedRealtimeMs = -1L;
          }
          clockRequestTimeout = null;
        };
    handler.postDelayed(clockRequestTimeout, 5_000L);
    try {
      byte[] request =
          org.runnerup.common.wear.WearClockSyncProtocol.encodeRequest(
              requestId, pendingClockRequestSentAtElapsedRealtimeMs);
      Wearable.MessageApi.sendMessage(
              mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_SESSION_CLOCK_REQUEST, request)
          .setResultCallback(
              result -> {
                if (!result.getStatus().isSuccess()
                    && pendingClockRequestId != null
                    && pendingClockRequestId.equals(requestId)) {
                  pendingClockRequestId = null;
                  pendingClockRequestSentAtElapsedRealtimeMs = -1L;
                  handler.removeCallbacks(clockRequestTimeout);
                  clockRequestTimeout = null;
                }
              });
    } catch (java.io.IOException invalid) {
      pendingClockRequestId = null;
      pendingClockRequestSentAtElapsedRealtimeMs = -1L;
      handler.removeCallbacks(clockRequestTimeout);
      clockRequestTimeout = null;
      Log.w(TAG, "Unable to create clock synchronization request", invalid);
    }
  }

  private boolean hasFreshClockOffset(long nowElapsedRealtimeMs) {
    return watchClockOffset != null
        && clockSampleReceivedAtWatchElapsedRealtimeMs >= 0
        && nowElapsedRealtimeMs >= clockSampleReceivedAtWatchElapsedRealtimeMs
        && nowElapsedRealtimeMs - clockSampleReceivedAtWatchElapsedRealtimeMs <= 600_000L;
  }

  private void handleClockReply(MessageEvent event) {
    if (phoneNode == null
        || !phoneNode.equals(event.getSourceNodeId())
        || pendingClockRequestId == null) return;
    try {
      org.runnerup.common.wear.WearClockSyncProtocol.Reply reply =
          org.runnerup.common.wear.WearClockSyncProtocol.decodeReply(event.getData());
      if (!pendingClockRequestId.equals(reply.getRequestId())
          || pendingClockRequestSentAtElapsedRealtimeMs != reply.getWatchSentAtElapsedRealtimeMs())
        return;
      long watchReceivedAt = android.os.SystemClock.elapsedRealtime();
      WorkoutSessionProtocol.ClockOffset offset =
          WorkoutSessionProtocol.estimateWatchInitiatedClockOffset(
              reply.getWatchSentAtElapsedRealtimeMs(),
              reply.getPhoneReceivedAtElapsedRealtimeMs(),
              reply.getPhoneSentAtElapsedRealtimeMs(),
              watchReceivedAt,
              2_000L);
      if (offset.isValid()) {
        watchClockOffset = offset;
        clockSampleReceivedAtWatchElapsedRealtimeMs = watchReceivedAt;
      }
      pendingClockRequestId = null;
      pendingClockRequestSentAtElapsedRealtimeMs = -1L;
      handler.removeCallbacks(clockRequestTimeout);
      clockRequestTimeout = null;
    } catch (java.io.IOException malformed) {
      Log.w(TAG, "Ignoring invalid clock synchronization reply", malformed);
    }
  }

  private void clearData() {
    /* delete our node id */
    mDataClient.deleteData(Constants.Wear.Path.WEAR_NODE_ID);
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "StateService.onDestroy()");
    serviceDestroyed = true;
    handler.removeCallbacks(connectionRetry);
    handler.removeCallbacks(strengthAuthorityProjectionRetry);
    handler.removeCallbacksAndMessages(null);
    trackerState.clearListeners();
    connectionStatus.clearListeners();
    preparedCountdownDeadline.clearListeners();
    if (mGoogleApiClient != null) {
      if (mGoogleApiClient.isConnected()) {
        phoneNode = null;

        clearData();
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
      }
      mGoogleApiClient.disconnect();
      mGoogleApiClient = null;
    }

    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public void onValueChanged(ValueModel<Bundle> instance, Bundle oldValue, Bundle newValue) {
    if (headersListener != null) {
      headersListener.onValueChanged(newValue);
    }
  }

  public class LocalBinder extends android.os.Binder {
    public StateService getService() {
      return StateService.this;
    }
  }

  private Bundle getBundle(Bundle src, long lastUpdateTime) {
    if (src == null) return null;

    long updateTime = src.getLong(UPDATE_TIME, 0);
    if (lastUpdateTime >= updateTime) return null;

    Bundle b = new Bundle();
    b.putAll(src);
    return b;
  }

  public Bundle getHeaders(long lastUpdateTime) {
    return getBundle(headers.get(), lastUpdateTime);
  }

  public Bundle getData(long lastUpdateTime) {
    return getBundle(data, lastUpdateTime);
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    if (Constants.Wear.Path.MSG_SESSION_CLOCK_REPLY.contentEquals(messageEvent.getPath())) {
      handleClockReply(messageEvent);
      return;
    }
    if (Constants.Wear.Path.MSG_WORKOUT_SESSION_COMMAND.contentEquals(messageEvent.getPath())) {
      if (phoneNode == null || !phoneNode.equals(messageEvent.getSourceNodeId())) return;
      try {
        WorkoutSessionProtocol.Command command =
            WorkoutSessionProtocol.decodeCommand(messageEvent.getData());
        if (command.getActor() == WorkoutSessionProtocol.Actor.PHONE) {
          handlePhoneSessionRequest(command);
        }
      } catch (java.io.IOException malformed) {
        Log.w(TAG, "Ignoring malformed phone session update", malformed);
      }
    } else if (Constants.Wear.Path.MSG_WORKOUT_SESSION_ACK.contentEquals(messageEvent.getPath())) {
      if (phoneNode == null || !phoneNode.equals(messageEvent.getSourceNodeId())) return;
      handlePhoneSessionAcknowledgement(messageEvent.getData());
    } else if (Constants.Wear.Path.MSG_WORKOUT_SESSION_REJECT.contentEquals(
        messageEvent.getPath())) {
      if (phoneNode == null || !phoneNode.equals(messageEvent.getSourceNodeId())) return;
      handlePhoneSessionRejection(messageEvent.getData());
    } else if (Constants.Wear.Path.MSG_STRENGTH_RESULT.contentEquals(messageEvent.getPath())) {
      handlePhoneStrengthResult(messageEvent);
    } else if (Constants.Wear.Path.MSG_STRENGTH_RESULT_ACK.contentEquals(messageEvent.getPath())) {
      if (phoneNode == null || !phoneNode.equals(messageEvent.getSourceNodeId())) return;
      handlePhoneStrengthResultAcknowledgement(messageEvent.getData());
    } else if (Constants.Wear.Path.MSG_WORKOUT_EVENT.contentEquals(messageEvent.getPath())) {
      data = DataMap.fromByteArray(messageEvent.getData()).toBundle();
      data.putLong(UPDATE_TIME, System.currentTimeMillis());
    } else {
      Log.d(TAG, "onMessageReceived: " + messageEvent);
    }
  }

  /** Durably queues a watch-origin result before making it eligible for transport. */
  public StrengthResultOutboxStore.EnqueueResult queueStrengthResult(
      StrengthResultProtocol.Result result) {
    StrengthResultOutboxStore.EnqueueResult outcome = strengthResultOutbox.enqueue(result);
    if (outcome == StrengthResultOutboxStore.EnqueueResult.ENQUEUED
        || outcome == StrengthResultOutboxStore.EnqueueResult.DUPLICATE) {
      retryPendingStrengthResult();
    }
    return outcome;
  }

  /**
   * Publishes the watch's already-durable latest strength state; ListenerService owns ACK storage.
   */
  public void publishStrengthAuthorityProjection() {
    if (phoneNode == null) return;
    WearStrengthExecutionStore executionStore = new WearStrengthExecutionStore(this);
    org.runnerup.relven.strength.StrengthSession execution = executionStore.load();
    if (execution != null) {
      for (var result : execution.syncableResults()) queueStrengthResult(result);
    }
    byte[] projection = executionStore.pendingProjection();
    if (projection == null) {
      handler.removeCallbacks(strengthAuthorityProjectionRetry);
      strengthAuthorityProjectionRetryAttempt = 0;
      return;
    }
    Wearable.getMessageClient(this)
        .sendMessage(phoneNode, Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_PROJECTION, projection)
        .addOnFailureListener(error -> Log.w(TAG, "Strength authority projection failed", error));
    long[] delaysMillis = {2_000L, 5_000L, 12_000L};
    handler.removeCallbacks(strengthAuthorityProjectionRetry);
    if (strengthAuthorityProjectionRetryAttempt < delaysMillis.length) {
      handler.postDelayed(
          strengthAuthorityProjectionRetry,
          delaysMillis[strengthAuthorityProjectionRetryAttempt++]);
    }
  }

  private void handlePhoneStrengthResult(MessageEvent event) {
    if (phoneNode == null || !phoneNode.equals(event.getSourceNodeId())) return;
    try {
      StrengthResultProtocol.Result result = StrengthResultProtocol.decodeResult(event.getData());
      StrengthResultInboxStore.AcceptResult accepted = strengthResultInbox.accept(result);
      if (accepted == StrengthResultInboxStore.AcceptResult.STORAGE_ERROR
          || accepted == StrengthResultInboxStore.AcceptResult.LIMIT_REACHED) return;
      StrengthResultProtocol.AckOutcome outcome;
      switch (accepted) {
        case STORED:
          outcome = StrengthResultProtocol.AckOutcome.COMMITTED;
          break;
        case DUPLICATE:
          outcome = StrengthResultProtocol.AckOutcome.DUPLICATE;
          break;
        case CONFLICT:
          outcome = StrengthResultProtocol.AckOutcome.CONFLICT;
          break;
        default:
          return;
      }
      sendStrengthResultAcknowledgement(
          phoneNode,
          new StrengthResultProtocol.Acknowledgement(
              result.getExecutionId(), result.getResultId(), outcome));
    } catch (java.io.IOException | IllegalArgumentException malformed) {
      Log.w(TAG, "Ignoring invalid phone strength result", malformed);
    }
  }

  private void handlePhoneStrengthResultAcknowledgement(byte[] payload) {
    try {
      StrengthResultProtocol.Acknowledgement acknowledgement =
          StrengthResultProtocol.decodeAcknowledgement(payload);
      StrengthResultOutboxStore.AcknowledgeResult result =
          strengthResultOutbox.acknowledge(acknowledgement);
      if (result == StrengthResultOutboxStore.AcknowledgeResult.REMOVED) {
        strengthResultRetryAttempt = 0;
        handler.removeCallbacks(strengthResultRetry);
        retryPendingStrengthResult();
        retryPendingPhoneProjection();
      }
    } catch (java.io.IOException malformed) {
      Log.w(TAG, "Ignoring malformed strength result acknowledgment", malformed);
    }
  }

  private void sendStrengthResultAcknowledgement(
      String destination, StrengthResultProtocol.Acknowledgement acknowledgement)
      throws java.io.IOException {
    if (checkConnection() != null || destination == null) return;
    Wearable.MessageApi.sendMessage(
        mGoogleApiClient,
        destination,
        Constants.Wear.Path.MSG_STRENGTH_RESULT_ACK,
        StrengthResultProtocol.encodeAcknowledgement(acknowledgement));
  }

  private void retryPendingStrengthResult() {
    handler.removeCallbacks(strengthResultRetry);
    if (checkConnection() != null || strengthResultOutbox == null) return;
    try {
      java.util.List<StrengthResultProtocol.Result> pending = strengthResultOutbox.pending(1);
      if (pending.isEmpty()) {
        strengthResultRetryAttempt = 0;
        return;
      }
      StrengthResultProtocol.Result result = pending.get(0);
      Wearable.MessageApi.sendMessage(
              mGoogleApiClient,
              phoneNode,
              Constants.Wear.Path.MSG_STRENGTH_RESULT,
              StrengthResultProtocol.encodeResult(result))
          .setResultCallback(
              status -> {
                long delay = Math.min(60_000L, 2_000L << Math.min(strengthResultRetryAttempt, 5));
                strengthResultRetryAttempt = Math.min(strengthResultRetryAttempt + 1, 5);
                handler.postDelayed(strengthResultRetry, delay);
              });
    } catch (java.io.IOException | RuntimeException failure) {
      Log.w(TAG, "Could not send pending strength result", failure);
      handler.postDelayed(strengthResultRetry, 5_000L);
    }
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      String path = ev.getDataItem().getUri().getPath();
      if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
        setPhoneNode(ev);
      } else if (Constants.Wear.Path.HEADERS.contentEquals(path)) {
        setHeaders(ev);
      } else if (Constants.Wear.Path.TRACKER_STATE.contentEquals(path)) {
        setTrackerState(ev);
      } else {
        Log.d(TAG, "onDataChanged: " + ev.getDataItem().getUri());
        readDataIfMissing();
      }
    }
  }

  private void setPhoneNode(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED
        && Objects.requireNonNull(ev.getDataItem().getData()).length > 0) {
      String newPhoneNode = new String(ev.getDataItem().getData());
      if (!newPhoneNode.equals(phoneNode)) {
        watchClockOffset = null;
        clockSampleReceivedAtWatchElapsedRealtimeMs = -1L;
        pendingClockRequestId = null;
        pendingClockRequestSentAtElapsedRealtimeMs = -1L;
        handler.removeCallbacks(clockRequestTimeout);
        clockRequestTimeout = null;
      }
      phoneNode = newPhoneNode;
      Log.d(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", phoneNode=" + phoneNode);
      requestClockSync();
      retryPendingPhoneProjection();
      retryPendingStrengthResult();
      publishStrengthAuthorityProjection();
      readDataIfMissing();
      refreshPhonePeerStatus();
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      Log.d(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", phoneNode=null");
      phoneNode = null;
      watchClockOffset = null;
      clockSampleReceivedAtWatchElapsedRealtimeMs = -1L;
      pendingClockRequestId = null;
      pendingClockRequestSentAtElapsedRealtimeMs = -1L;
      handler.removeCallbacks(clockRequestTimeout);
      clockRequestTimeout = null;
      preparedCountdownDeadline.set(null);
      resetState();
      refreshPhonePeerStatus();
    }
  }

  private void setHeaders(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      Bundle b = DataMapItem.fromDataItem(ev.getDataItem()).getDataMap().toBundle();
      b.putLong(UPDATE_TIME, System.currentTimeMillis());
      Log.d(TAG, "setHeaders(): b=" + b);
      headers.set(b);
      readDataIfMissing();
    } else {
      Log.d(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", headers=null");
      headers.set(null);
      resetState();
    }
  }

  public static TrackerState getTrackerStateFromDataItem(DataItem dataItem) {
    if (!dataItem.isDataValid()) {
      Log.d(TAG, "onDataChanged: " + dataItem.getUri() + ", dataItem.isDataValid() == false");
      return null;
    }

    Bundle b = DataMap.fromByteArray(dataItem.getData()).toBundle();
    if (b.containsKey(Constants.Wear.TrackerState.STATE)) {
      var state = TrackerState.valueOf(b.getInt(Constants.Wear.TrackerState.STATE));
      Log.d(TAG, "onDataChanged: " + dataItem.getUri() + ", trackerState=" + state);
      return state;
    }
    return null;
  }

  private void setTrackerState(DataEvent ev) {
    TrackerState newVal = null;
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      newVal = getTrackerStateFromDataItem(ev.getDataItem());
      if (newVal == null) {
        // This is weird. TrackerState is set to a invalid value...skip out
        return;
      }
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      // trackerState being deleted
      newVal = null;
      resetState();
    }
    setTrackerState(newVal);
    readDataIfMissing();
  }

  private void resetState() {
    data = null;
    headers.set(null);
  }

  private void setTrackerState(TrackerState newVal) {
    if (newVal == TrackerState.STARTED
        && workoutSessionState != null
        && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.COUNTDOWN) {
      WorkoutSessionProtocol.Transition advanced =
          WorkoutSessionProtocol.advance(
              workoutSessionState, android.os.SystemClock.elapsedRealtime());
      if (advanced.getResult() == WorkoutSessionProtocol.Result.ACCEPTED) {
        workoutSessionState = advanced.getState();
      }
    }
    trackerState.set(newVal);
  }

  public TrackerState getTrackerState() {
    return trackerState.get();
  }

  public void registerTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    trackerState.registerChangeListener(listener);
  }

  public void unregisterTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    trackerState.unregisterChangeListener(listener);
  }

  public void registerHeadersListener(MainActivity listener) {
    this.headersListener = listener;
  }

  public void unregisterHeadersListener(MainActivity listener) {
    this.headersListener = null;
  }

  public void sendStart() {
    var msg = checkConnection();
    if (msg != null) {
      Log.d(TAG, "sendStart => not connected: " + msg);
      return;
    }

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_START, null);
  }

  /** Keeps the command sequence scoped to the workout currently shown in the watch UI. */
  public void setPreparedWorkoutSession(String sessionId) {
    setPreparedWorkoutSession(sessionId, false);
  }

  public void setPreparedWorkoutSession(String sessionId, boolean supportsSkip) {
    if (sessionId == null || sessionId.isEmpty()) return;
    skipSupported = supportsSkip;
    if (sessionId.equals(workoutSessionId)) {
      requestClockSync();
      return;
    }
    workoutSessionId = sessionId;
    workoutCommandSequence = 0L;
    restoreWatchSessionJournal(sessionId);
    pendingProjectionAttempts = 0;
    handler.removeCallbacks(pendingProjectionRetry);
    preparedCountdownDeadline.set(null);
    requestClockSync();
    retryPendingPhoneProjection();
  }

  /** Commits the start on the watch, then projects the accepted event to the phone. */
  public boolean sendStart(String sessionId) {
    if (!sessionId.equals(workoutSessionId)) setPreparedWorkoutSession(sessionId, false);
    long now = android.os.SystemClock.elapsedRealtime();
    return sendSessionCommand(
        WorkoutSessionProtocol.Action.START,
        now + 3_000L,
        java.util.UUID.randomUUID().toString(),
        now);
  }

  public void sendPauseResume() {
    if (workoutSessionId != null) {
      if (workoutSessionState != null
          && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.ACTIVE) {
        sendSessionCommand(WorkoutSessionProtocol.Action.PAUSE, 0L);
      } else if (workoutSessionState != null
          && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.PAUSED) {
        sendSessionCommand(WorkoutSessionProtocol.Action.RESUME, 0L);
      }
      return;
    }
    var msg = checkConnection();
    if (msg != null) {
      Log.d(TAG, "sendPauseResume => not connected: " + msg);
      return;
    }

    if (getTrackerState() == TrackerState.STARTED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_PAUSE, null);
    } else if (getTrackerState() == TrackerState.PAUSED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_RESUME, null);
    }
  }

  public void sendNewLap() {
    if (workoutSessionId != null) {
      sendSessionCommand(WorkoutSessionProtocol.Action.LAP, 0L);
      return;
    }
    var msg = checkConnection();
    if (msg != null) {
      Log.d(TAG, "sendNewLap => not connected: " + msg);
      return;
    }

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_NEW_LAP, null);
  }

  public void sendSkip() {
    if (workoutSessionId != null
        && skipSupported
        && workoutSessionState != null
        && workoutSessionState.getPhase() == WorkoutSessionProtocol.Phase.ACTIVE) {
      sendSessionCommand(WorkoutSessionProtocol.Action.SKIP, 0L);
    }
  }

  public boolean isSkipSupported() {
    return skipSupported;
  }

  public void sendFinish() {
    if (workoutSessionId != null) {
      if (workoutSessionState == null
          || (workoutSessionState.getPhase() != WorkoutSessionProtocol.Phase.ACTIVE
              && workoutSessionState.getPhase() != WorkoutSessionProtocol.Phase.PAUSED)) return;
      publishStrengthAuthorityProjection();
      sendSessionCommand(WorkoutSessionProtocol.Action.FINISH, 0L);
      return;
    }
    if (getTrackerState() != TrackerState.STARTED && getTrackerState() != TrackerState.PAUSED)
      return;
    var msg = checkConnection();
    if (msg != null) {
      Log.d(TAG, "sendFinish => not connected: " + msg);
      return;
    }
    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_FINISH, null);
  }

  private void sendSessionCommand(WorkoutSessionProtocol.Action action, long plannedStart) {
    sendSessionCommand(
        action,
        plannedStart,
        java.util.UUID.randomUUID().toString(),
        android.os.SystemClock.elapsedRealtime());
  }

  private boolean sendSessionCommand(
      WorkoutSessionProtocol.Action action,
      long plannedStart,
      String commandId,
      long issuedAtElapsedRealtimeMs) {
    boolean connected = checkConnection() == null;
    // RunnerUp on the phone is the recording engine. Until the watch can enact controls locally,
    // do not show an offline pause/finish as accepted while the phone keeps recording.
    if (!connected) return false;
    if (action == WorkoutSessionProtocol.Action.START
        && !hasFreshClockOffset(android.os.SystemClock.elapsedRealtime())) {
      requestClockSync();
      return false;
    }
    if (pendingPhoneProjection != null) {
      retryPendingPhoneProjection();
      return false;
    }
    try {
      WorkoutSessionProtocol.Command command =
          new WorkoutSessionProtocol.Command(
              workoutSessionId,
              commandId,
              workoutSessionState.getLastSequence() + 1L,
              WorkoutSessionProtocol.Actor.WATCH,
              action,
              issuedAtElapsedRealtimeMs,
              plannedStart);
      WorkoutSessionProtocol.Transition transition =
          WorkoutSessionProtocol.applyAtAuthority(
              workoutSessionState, WorkoutSessionProtocol.Actor.WATCH, command);
      if (transition.getResult() != WorkoutSessionProtocol.Result.ACCEPTED) return false;
      if (!persistWatchEvent(command)) return false;
      workoutSessionState = transition.getState();
      workoutCommandSequence = workoutSessionState.getLastSequence();
      lastCommittedSessionCommand = command;
      sendAcceptedSessionProjection(command);
      return true;
    } catch (IllegalArgumentException invalidCommand) {
      Log.w(TAG, "Unable to send session command", invalidCommand);
      return false;
    }
  }

  private void handlePhoneSessionRequest(WorkoutSessionProtocol.Command request) {
    if (request.getSessionId() == null
        || !request.getSessionId().equals(workoutSessionId)
        || workoutSessionState == null) return;
    if (request.getAction() == WorkoutSessionProtocol.Action.SKIP && !skipSupported) {
      rejectPhoneSessionRequest(request);
      return;
    }
    if (lastCommittedSessionCommand != null
        && lastCommittedSessionCommand.getCommandId().equals(request.getCommandId())) {
      sendAcceptedSessionProjection(lastCommittedSessionCommand);
      return;
    }
    if (pendingPhoneProjection != null) {
      retryPendingPhoneProjection();
      return;
    }
    long expectedSequence = workoutSessionState.getLastSequence() + 1L;
    if (request.getSequence() != expectedSequence) {
      // A stale phone projection must not turn a redelivered control into a second action.
      // Re-send our latest accepted event so the phone can catch up before issuing another one.
      if (lastCommittedSessionCommand != null) {
        sendAcceptedSessionProjection(lastCommittedSessionCommand);
      }
      rejectPhoneSessionRequest(request);
      return;
    }
    long now = android.os.SystemClock.elapsedRealtime();
    long plannedStart = 0L;
    if (request.getAction() == WorkoutSessionProtocol.Action.START) {
      if (!hasFreshClockOffset(now)) {
        requestClockSync();
        rejectPhoneSessionRequest(request);
        return;
      }
      plannedStart =
          watchClockOffset.toWatchElapsedRealtime(request.getPlannedStartElapsedRealtimeMs());
      if (plannedStart <= now || plannedStart - now > 10_000L) {
        rejectPhoneSessionRequest(request);
        return;
      }
    }
    WorkoutSessionProtocol.Command accepted =
        new WorkoutSessionProtocol.Command(
            workoutSessionId,
            request.getCommandId(),
            request.getSequence(),
            WorkoutSessionProtocol.Actor.WATCH,
            request.getAction(),
            now,
            plannedStart);
    WorkoutSessionProtocol.Transition transition =
        WorkoutSessionProtocol.applyAtAuthority(
            workoutSessionState, WorkoutSessionProtocol.Actor.WATCH, accepted);
    if (transition.getResult() != WorkoutSessionProtocol.Result.ACCEPTED) {
      rejectPhoneSessionRequest(request);
      return;
    }
    if (!persistWatchEvent(accepted)) {
      rejectPhoneSessionRequest(request);
      return;
    }
    workoutSessionState = transition.getState();
    workoutCommandSequence = workoutSessionState.getLastSequence();
    lastCommittedSessionCommand = accepted;
    sendAcceptedSessionProjection(accepted);
  }

  private void rejectPhoneSessionRequest(WorkoutSessionProtocol.Command request) {
    if (checkConnection() != null) return;
    try {
      WorkoutSessionProtocol.Command rejection =
          new WorkoutSessionProtocol.Command(
              request.getSessionId(),
              request.getCommandId(),
              request.getSequence(),
              request.getActor(),
              request.getAction(),
              request.getIssuedAtElapsedRealtimeMs(),
              request.getPlannedStartElapsedRealtimeMs());
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          phoneNode,
          Constants.Wear.Path.MSG_WORKOUT_SESSION_REJECT,
          WorkoutSessionProtocol.encodeCommand(rejection));
    } catch (java.io.IOException | IllegalArgumentException malformed) {
      Log.w(TAG, "Unable to reject phone session request", malformed);
    }
  }

  private void sendAcceptedSessionProjection(WorkoutSessionProtocol.Command accepted) {
    WorkoutSessionEventJournal journal = sessionEventStore.load(accepted.getSessionId());
    if (journal == null || journal.pendingEvents().isEmpty()) return;
    WorkoutSessionProtocol.Command firstPending = journal.pendingEvents().get(0);
    // Recorder control follows every committed authority event, independently of whether the
    // phone has acknowledged an earlier event in the projection journal.
    WearExerciseRecorderStarter.dispatch(this, accepted);
    if (pendingPhoneProjection != null
        && !pendingPhoneProjection.getCommandId().equals(firstPending.getCommandId())) return;
    if (pendingPhoneProjection == null) pendingProjectionAttempts = 0;
    pendingPhoneProjection = firstPending;
    retryPendingPhoneProjection();
  }

  private boolean persistWatchEvent(WorkoutSessionProtocol.Command command) {
    WorkoutSessionEventJournalStore.AppendResult result =
        sessionEventStore.append(
            command.getSessionId(), WorkoutSessionProtocol.Actor.WATCH, command);
    return result == WorkoutSessionEventJournalStore.AppendResult.APPENDED
        || result == WorkoutSessionEventJournalStore.AppendResult.DUPLICATE;
  }

  private void restoreWatchSessionJournal(String sessionId) {
    WorkoutSessionEventJournal journal = sessionEventStore.load(sessionId);
    if (journal == null || journal.getAuthority() != WorkoutSessionProtocol.Actor.WATCH) {
      workoutSessionState =
          WorkoutSessionProtocol.SessionState.ready(sessionId, WorkoutSessionProtocol.Actor.WATCH);
      lastCommittedSessionCommand = null;
      pendingPhoneProjection = null;
      return;
    }
    workoutSessionState = journal.replayState();
    workoutCommandSequence = journal.lastSequence();
    lastCommittedSessionCommand =
        journal.events().isEmpty() ? null : journal.events().get(journal.events().size() - 1);
    pendingPhoneProjection =
        journal.pendingEvents().isEmpty() ? null : journal.pendingEvents().get(0);
  }

  private void restorePendingSessionProjection() {
    if (workoutSessionId != null) restoreWatchSessionJournal(workoutSessionId);
  }

  private void retryPendingPhoneProjection() {
    WorkoutSessionProtocol.Command accepted = pendingPhoneProjection;
    if (accepted == null) return;
    if (!canProjectSessionCommand(accepted)) {
      retryPendingStrengthResult();
      return;
    }
    if (checkConnection() != null) return;
    try {
      WorkoutSessionProtocol.Command projection = accepted;
      if (accepted.getAction() == WorkoutSessionProtocol.Action.START) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!hasFreshClockOffset(now)) {
          requestClockSync();
          Log.w(TAG, "Cannot project a watch start without a fresh clock sample");
          return;
        }
        projection =
            WorkoutSessionProtocol.rebaseCommandClock(
                accepted,
                watchClockOffset.toPhoneElapsedRealtime(accepted.getIssuedAtElapsedRealtimeMs()),
                watchClockOffset.toPhoneElapsedRealtime(
                    accepted.getPlannedStartElapsedRealtimeMs()));
      }
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient,
          phoneNode,
          Constants.Wear.Path.MSG_WORKOUT_SESSION_COMMAND,
          WorkoutSessionProtocol.encodeCommand(projection));
      if (pendingProjectionAttempts < MAX_PROJECTION_ATTEMPTS) pendingProjectionAttempts++;
      if (pendingProjectionAttempts < MAX_PROJECTION_ATTEMPTS) {
        handler.removeCallbacks(pendingProjectionRetry);
        handler.postDelayed(pendingProjectionRetry, PROJECTION_RETRY_DELAY_MS);
      } else {
        Log.w(TAG, "Waiting for phone acknowledgment of watch session command");
        handler.removeCallbacks(pendingProjectionRetry);
        handler.postDelayed(pendingProjectionRetry, 5_000L);
      }
    } catch (java.io.IOException error) {
      Log.w(TAG, "Unable to project watch-authoritative session event", error);
      handler.removeCallbacks(pendingProjectionRetry);
      handler.postDelayed(pendingProjectionRetry, PROJECTION_RETRY_DELAY_MS);
    }
  }

  private boolean canProjectSessionCommand(WorkoutSessionProtocol.Command command) {
    boolean readable = strengthResultOutbox != null;
    int pendingCount = 0;
    if (readable) {
      try {
        pendingCount =
            strengthResultOutbox.pending(StrengthResultOutboxStore.MAX_PENDING_RESULTS).size();
      } catch (RuntimeException unavailable) {
        readable = false;
        Log.w(
            TAG,
            "Holding terminal workout projection until set-result storage is readable",
            unavailable);
      }
    }
    return WorkoutSessionTerminalDeliveryPolicy.canProject(command, readable, pendingCount);
  }

  private void handlePhoneSessionAcknowledgement(byte[] payload) {
    try {
      WorkoutSessionProtocol.Command acknowledgement =
          WorkoutSessionProtocol.decodeCommand(payload);
      WorkoutSessionProtocol.Command pending = pendingPhoneProjection;
      if (pending == null
          || acknowledgement.getActor() != WorkoutSessionProtocol.Actor.WATCH
          || !pending.getSessionId().equals(acknowledgement.getSessionId())
          || !pending.getCommandId().equals(acknowledgement.getCommandId())
          || pending.getSequence() != acknowledgement.getSequence()) return;
      if (!sessionEventStore.acknowledge(
          pending.getSessionId(), pending.getSequence(), pending.getCommandId())) return;
      handler.removeCallbacks(pendingProjectionRetry);
      pendingProjectionAttempts = 0;
      if (pending.getAction() == WorkoutSessionProtocol.Action.START) {
        preparedCountdownDeadline.set(pending.getPlannedStartElapsedRealtimeMs());
      } else if (pending.getAction() == WorkoutSessionProtocol.Action.CANCEL) {
        preparedCountdownDeadline.set(null);
      }
      WorkoutSessionEventJournal journal = sessionEventStore.load(pending.getSessionId());
      pendingPhoneProjection =
          journal == null || journal.pendingEvents().isEmpty()
              ? null
              : journal.pendingEvents().get(0);
      if (pendingPhoneProjection != null) retryPendingPhoneProjection();
    } catch (java.io.IOException malformed) {
      Log.w(TAG, "Ignoring malformed phone session acknowledgment", malformed);
    }
  }

  private void handlePhoneSessionRejection(byte[] payload) {
    try {
      WorkoutSessionProtocol.Command rejection = WorkoutSessionProtocol.decodeCommand(payload);
      WorkoutSessionProtocol.Command pending = pendingPhoneProjection;
      WorkoutSessionProtocol.Command latest = lastCommittedSessionCommand;
      if (latest == null
          || latest.getAction() != WorkoutSessionProtocol.Action.START
          || rejection.getActor() != WorkoutSessionProtocol.Actor.WATCH
          || !latest.getSessionId().equals(rejection.getSessionId())
          || !latest.getCommandId().equals(rejection.getCommandId())
          || latest.getSequence() != rejection.getSequence()
          || (pending != null
              && (!pending.getSessionId().equals(rejection.getSessionId())
                  || !pending.getCommandId().equals(rejection.getCommandId())
                  || pending.getSequence() != rejection.getSequence()))
          || sessionEventStore.rejectUnstartedStart(
                  rejection.getSessionId(), rejection.getCommandId())
              != WorkoutSessionEventJournalStore.RejectionRollbackResult.ROLLED_BACK) return;
      if (pending != null) {
        handler.removeCallbacks(pendingProjectionRetry);
        pendingPhoneProjection = null;
        pendingProjectionAttempts = 0;
      }
      preparedCountdownDeadline.set(null);
      workoutSessionState =
          WorkoutSessionProtocol.SessionState.ready(
              workoutSessionId, WorkoutSessionProtocol.Actor.WATCH);
      workoutCommandSequence = 0L;
      lastCommittedSessionCommand = null;
    } catch (java.io.IOException | IllegalArgumentException malformed) {
      Log.w(TAG, "Ignoring malformed session rejection", malformed);
    }
  }

  public Long getPreparedCountdownDeadlineElapsedRealtimeMs() {
    return preparedCountdownDeadline.get();
  }

  public void registerPreparedCountdownListener(ValueModel.ChangeListener<Long> listener) {
    preparedCountdownDeadline.registerChangeListener(listener);
  }

  public void unregisterPreparedCountdownListener(ValueModel.ChangeListener<Long> listener) {
    preparedCountdownDeadline.unregisterChangeListener(listener);
  }
}
