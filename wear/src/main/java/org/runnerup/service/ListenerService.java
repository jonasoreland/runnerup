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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;
import androidx.wear.ongoing.Status.TextPart;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import java.io.IOException;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.wear.GpsPreparationProtocol;
import org.runnerup.common.wear.StrengthSessionAuthorityProtocol;
import org.runnerup.common.wear.WearSessionProtocol;
import org.runnerup.common.wear.WorkoutPreparationProtocol;
import org.runnerup.view.MainActivity;
import org.runnerup.wear.WearableClient;

public class ListenerService extends WearableListenerService {

  private static String TAG = "RU:ListenerService";

  private final int notificationId = 10;

  private WearableClient mGoogleApiClient = null;
  private Notification notification = null;
  private TrackerState trackerState = null;
  private Boolean phoneRunning = null; // TrackerWear
  private Boolean mainActivityRunning = null;
  private Boolean phoneApp = null; // StartActivity
  private String phoneNodeId;
  private final DeferredStrengthAuthorityCommand deferredStrengthAuthorityCommand =
      new DeferredStrengthAuthorityCommand();
  private final Handler authorityRetryHandler = new Handler(Looper.getMainLooper());
  private boolean authorityProjectionRetryScheduled;
  private int authorityProjectionRetryAttempt;
  private final Runnable authorityProjectionRetry =
      () -> {
        authorityProjectionRetryScheduled = false;
        retryPendingStrengthAuthorityProjection();
      };

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "ListenerService.onCreate()");
    mGoogleApiClient = new WearableClient(getApplicationContext());
    mGoogleApiClient.readData(
        Constants.Wear.Path.WEAR_APP,
        dataItem -> {
          mainActivityRunning = (dataItem != null);
          maybeShowNotification();
        });
    mGoogleApiClient.readData(
        Constants.Wear.Path.PHONE_NODE_ID,
        dataItem -> {
          phoneRunning = (dataItem != null);
          phoneNodeId = dataItem == null ? null : dataItem.getUri().getHost();
          processDeferredStrengthAuthorityCommand();
          retryPendingStrengthAuthorityProjection();
          reconcileGpsReadiness();
          maybeShowNotification();
        });
    mGoogleApiClient.readData(
        Constants.Wear.Path.TRACKER_STATE,
        dataItem -> {
          if (dataItem != null) {
            trackerState = StateService.getTrackerStateFromDataItem(dataItem);
          } else {
            trackerState = null;
          }
          maybeShowNotification();
        });
    mGoogleApiClient.readData(
        Constants.Wear.Path.PHONE_APP,
        dataItem -> {
          phoneApp = (dataItem != null);
          maybeShowNotification();
        });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "ListenerService.onDestroy()");
    if (mGoogleApiClient != null) {
      mGoogleApiClient = null;
    }
    authorityRetryHandler.removeCallbacks(authorityProjectionRetry);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "ListenerService.onStart()");
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      Log.i(TAG, "onDataChanged: " + ev.getDataItem().getUri());
      var type = ev.getType();
      String path = ev.getDataItem().getUri().getPath();
      if (!(type == DataEvent.TYPE_DELETED || type == DataEvent.TYPE_CHANGED)) {
        // skip
        continue;
      }
      boolean deleted = (type == DataEvent.TYPE_DELETED);
      if (path != null && path.startsWith(Constants.Wear.Path.GPS_READINESS_PREFIX + "/")) {
        handleGpsReadinessDataItem(ev.getDataItem(), deleted);
        continue;
      } else if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
        phoneRunning = !deleted;
        phoneNodeId = deleted ? null : ev.getDataItem().getUri().getHost();
        processDeferredStrengthAuthorityCommand();
        retryPendingStrengthAuthorityProjection();
        reconcileGpsReadiness();
      } else if (Constants.Wear.Path.WEAR_APP.contentEquals(path)) {
        mainActivityRunning = !deleted;
      } else if (Constants.Wear.Path.TRACKER_STATE.contentEquals(path)) {
        if (deleted) {
          trackerState = null;
          phoneRunning = false;
        } else {
          trackerState = StateService.getTrackerStateFromDataItem(ev.getDataItem());
          phoneRunning = true;
        }
      } else if (Constants.Wear.Path.PHONE_APP.contentEquals(path)) {
        phoneApp = !deleted;
      } else {
        // other path
        continue;
      }
      maybeShowNotification();
    }
  }

  @Override
  public void onMessageReceived(com.google.android.gms.wearable.MessageEvent messageEvent) {
    super.onMessageReceived(messageEvent);
    if (Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_COMMAND.equals(messageEvent.getPath())) {
      if (phoneNodeId == null) {
        if (!deferredStrengthAuthorityCommand.defer(
            messageEvent.getSourceNodeId(), messageEvent.getData())) {
          Log.w(TAG, "Could not defer strength authority command while phone identity loads");
        }
      } else {
        handleStrengthAuthorityCommand(messageEvent.getSourceNodeId(), messageEvent.getData());
      }
      return;
    }
    if (Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_ACK.equals(messageEvent.getPath())) {
      if (phoneNodeId != null && phoneNodeId.equals(messageEvent.getSourceNodeId())) {
        handleStrengthAuthorityAcknowledgement(messageEvent.getData());
      }
      return;
    }
    if (Constants.Wear.Path.MSG_SESSION_PING.equals(messageEvent.getPath())) {
      try {
        byte[] response = WearSessionProtocol.respondToPing(messageEvent.getData());
        com.google.android.gms.wearable.Wearable.getMessageClient(this)
            .sendMessage(
                messageEvent.getSourceNodeId(), Constants.Wear.Path.MSG_SESSION_PONG, response)
            .addOnFailureListener(error -> Log.w(TAG, "Unable to acknowledge session ping", error));
      } catch (IOException error) {
        Log.w(TAG, "Ignoring invalid session handshake payload", error);
      }
      return;
    }
    if (Constants.Wear.Path.MSG_WORKOUT_CANCEL.equals(messageEvent.getPath())) {
      String cancelledSessionId = null;
      try {
        cancelledSessionId =
            WorkoutPreparationProtocol.decodeCancellation(messageEvent.getData()).getSessionId();
      } catch (IOException malformed) {
        Log.w(TAG, "Ignoring malformed workout cancellation", malformed);
      }
      PreparedWorkoutReceiver receiver =
          new PreparedWorkoutReceiver(new PreparedWorkoutStore(this));
      if (receiver.cancel(messageEvent.getData())
          && new PreparedWorkoutStore(this).load() == null) {
        if (cancelledSessionId != null) {
          new PreparedGpsReadinessStore(this).clear(cancelledSessionId);
        }
        NotificationManagerCompat.from(this).cancel(11);
      }
      return;
    }
    if (Constants.Wear.Path.MSG_STRENGTH_CANCEL.equals(messageEvent.getPath())) {
      PreparedStrengthSessionReceiver receiver =
          new PreparedStrengthSessionReceiver(new PreparedStrengthSessionStore(this));
      if (receiver.cancel(messageEvent.getData())) {
        NotificationManagerCompat.from(this).cancel(12);
      }
      return;
    }
    if (Constants.Wear.Path.MSG_STRENGTH_PREPARE.equals(messageEvent.getPath())) {
      byte[] payload = messageEvent.getData();
      PreparedStrengthSessionReceiver receiver =
          new PreparedStrengthSessionReceiver(new PreparedStrengthSessionStore(this));
      byte[] response = receiver.receive(payload);
      if (response == null) {
        Log.w(TAG, "Ignoring invalid strength session preparation payload");
        return;
      }
      try {
        var acknowledgement =
            org.runnerup.common.wear.StrengthExecutionProtocol.decodeAcknowledgement(response);
        if (acknowledgement.isAccepted()) {
          var preparation =
              org.runnerup.common.wear.StrengthExecutionProtocol.decodePrepare(payload);
          var saved =
              org.runnerup.relven.strength.StrengthSessionCodec.INSTANCE.decode(
                  preparation.getSessionJson());
          if (!new WearStrengthExecutionStore(this).savePreparedSession(saved.getFirst())) {
            response =
                org.runnerup.common.wear.StrengthExecutionProtocol.encodeAcknowledgement(
                    preparation.getSessionId(), preparation.getRequestId(), false);
            Wearable.getMessageClient(this)
                .sendMessage(
                    messageEvent.getSourceNodeId(),
                    Constants.Wear.Path.MSG_STRENGTH_PREPARED,
                    response)
                .addOnFailureListener(
                    error -> Log.w(TAG, "Unable to reject strength preparation", error));
            return;
          }
          notifyPreparedStrengthSession(saved.getFirst().getWorkout().name());
        }
      } catch (IOException invalidStoredSession) {
        Log.w(TAG, "Could not read stored strength session", invalidStoredSession);
      }
      com.google.android.gms.wearable.Wearable.getMessageClient(this)
          .sendMessage(
              messageEvent.getSourceNodeId(), Constants.Wear.Path.MSG_STRENGTH_PREPARED, response)
          .addOnFailureListener(
              error -> Log.w(TAG, "Unable to acknowledge prepared strength session", error));
      return;
    }
    if (!Constants.Wear.Path.MSG_WORKOUT_PREPARE.equals(messageEvent.getPath())) return;

    byte[] payload = messageEvent.getData();
    PreparedWorkoutReceiver receiver = new PreparedWorkoutReceiver(new PreparedWorkoutStore(this));
    byte[] response = receiver.receive(payload);
    if (response == null) {
      Log.w(TAG, "Ignoring invalid workout preparation payload");
      return;
    }
    try {
      var acknowledgement =
          org.runnerup.common.wear.WorkoutPreparationProtocol.decodeAcknowledgement(response);
      var preparation = org.runnerup.common.wear.WorkoutPreparationProtocol.decodePrepare(payload);
      if (acknowledgement.isAccepted()) notifyPreparedWorkout(preparation.getWorkoutName());
      if (acknowledgement.isAccepted()) {
        new PreparedGpsReadinessStore(this).clearForDifferentSession(preparation.getSessionId());
        reconcileGpsReadiness();
      }
    } catch (IOException impossibleAfterReceiverValidation) {
      Log.w(TAG, "Could not read stored workout preparation", impossibleAfterReceiverValidation);
    }
    com.google.android.gms.wearable.Wearable.getMessageClient(this)
        .sendMessage(
            messageEvent.getSourceNodeId(), Constants.Wear.Path.MSG_WORKOUT_PREPARED, response)
        .addOnFailureListener(error -> Log.w(TAG, "Unable to acknowledge prepared workout", error));
  }

  private void handleGpsReadinessDataItem(DataItem dataItem, boolean deleted) {
    String sourceNodeId = dataItem.getUri().getHost();
    if (phoneNodeId == null || !phoneNodeId.equals(sourceNodeId)) return;
    String sessionId =
        GpsPreparationProtocol.sessionIdFromDataItemPath(dataItem.getUri().getPath());
    if (sessionId == null) return;
    PreparedGpsReadinessStore store = new PreparedGpsReadinessStore(this);
    if (deleted) {
      store.clear(sessionId);
      return;
    }
    try {
      byte[] payload =
          DataMapItem.fromDataItem(dataItem)
              .getDataMap()
              .getByteArray(GpsPreparationProtocol.DATA_KEY);
      GpsPreparationProtocol.Readiness readiness = GpsPreparationProtocol.decode(payload);
      if (!sessionId.equals(readiness.getSessionId())) return;
      if (store.save(payload)) store.clearForDifferentSession(sessionId);
    } catch (IOException | RuntimeException invalid) {
      Log.w(TAG, "Ignoring invalid or unrelated GPS readiness", invalid);
    }
  }

  /** Recovers the latest matching value when the Wear process or transport reconnects. */
  private void reconcileGpsReadiness() {
    if (phoneNodeId == null) return;
    byte[] preparedPayload = new PreparedWorkoutStore(this).load();
    if (preparedPayload == null) return;
    try {
      WorkoutPreparationProtocol.PrepareMessage prepared =
          WorkoutPreparationProtocol.decodePrepare(preparedPayload);
      android.net.Uri uri =
          new android.net.Uri.Builder()
              .scheme(PutDataRequest.WEAR_URI_SCHEME)
              .path(GpsPreparationProtocol.dataItemPath(prepared.getSessionId()))
              .build();
      Wearable.getDataClient(this)
          .getDataItem(uri)
          .addOnSuccessListener(
              item -> {
                if (item != null) handleGpsReadinessDataItem(item, false);
              })
          .addOnFailureListener(error -> Log.w(TAG, "Unable to recover GPS readiness", error));
    } catch (IOException | RuntimeException invalidPreparedWorkout) {
      Log.w(TAG, "Cannot recover GPS readiness for an invalid workout", invalidPreparedWorkout);
    }
  }

  private void processDeferredStrengthAuthorityCommand() {
    if (phoneNodeId == null) return;
    DeferredStrengthAuthorityCommand.Pending pending =
        deferredStrengthAuthorityCommand.takeFor(phoneNodeId);
    if (pending != null) {
      handleStrengthAuthorityCommand(pending.getSourceNodeId(), pending.getPayload());
    }
  }

  private void handleStrengthAuthorityCommand(String sourceNodeId, byte[] payload) {
    if (phoneNodeId == null || !phoneNodeId.equals(sourceNodeId)) return;
    final StrengthSessionAuthorityProtocol.Command command;
    try {
      command = StrengthSessionAuthorityProtocol.decodeCommand(payload);
    } catch (IllegalArgumentException malformed) {
      Log.w(TAG, "Ignoring malformed strength authority command", malformed);
      return;
    }
    WearStrengthExecutionStore store = new WearStrengthExecutionStore(this);
    org.runnerup.relven.strength.StrengthSession current = store.load();
    if (current == null || !current.getExecutionId().equals(command.getSessionId())) {
      sendStrengthAuthorityResponse(
          sourceNodeId,
          new StrengthSessionAuthorityProtocol.Acknowledgement(
              command.getSessionId(),
              command.getRequestId(),
              0L,
              StrengthSessionAuthorityProtocol.Outcome.CONFLICT));
      return;
    }
    StrengthSessionAuthorityProtocol.Transition transition =
        StrengthSessionAuthorityProtocol.applyAtWatch(
            current, command, SystemClock.elapsedRealtime());
    if (transition.getOutcome() == StrengthSessionAuthorityProtocol.Outcome.COMMITTED) {
      WearStrengthExecutionStore.CommitResult commit = store.commit(transition.getSession());
      if (commit != WearStrengthExecutionStore.CommitResult.COMMITTED) {
        sendStrengthAuthorityResponse(
            sourceNodeId,
            new StrengthSessionAuthorityProtocol.Acknowledgement(
                command.getSessionId(),
                command.getRequestId(),
                current.getAuthorityRevision(),
                StrengthSessionAuthorityProtocol.Outcome.CONFLICT));
        return;
      }
    } else if (transition.getOutcome() != StrengthSessionAuthorityProtocol.Outcome.DUPLICATE) {
      sendStrengthAuthorityResponse(
          sourceNodeId,
          new StrengthSessionAuthorityProtocol.Acknowledgement(
              command.getSessionId(),
              command.getRequestId(),
              current.getAuthorityRevision(),
              transition.getOutcome()));
      return;
    }
    retryPendingStrengthAuthorityProjection();
  }

  private void handleStrengthAuthorityAcknowledgement(byte[] payload) {
    try {
      var acknowledgement = StrengthSessionAuthorityProtocol.decodeAcknowledgement(payload);
      if (new WearStrengthExecutionStore(this).acknowledge(acknowledgement)) {
        authorityRetryHandler.removeCallbacks(authorityProjectionRetry);
        authorityProjectionRetryScheduled = false;
        authorityProjectionRetryAttempt = 0;
      }
    } catch (RuntimeException invalid) {
      Log.w(TAG, "Ignoring invalid strength authority acknowledgement", invalid);
    }
  }

  private void sendStrengthAuthorityResponse(
      String nodeId, StrengthSessionAuthorityProtocol.Acknowledgement acknowledgement) {
    try {
      Wearable.getMessageClient(this)
          .sendMessage(
              nodeId,
              Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_RESPONSE,
              StrengthSessionAuthorityProtocol.encodeAcknowledgement(acknowledgement))
          .addOnFailureListener(error -> Log.w(TAG, "Strength rejection response failed", error));
    } catch (RuntimeException error) {
      Log.w(TAG, "Could not send strength authority response", error);
    }
  }

  private void notifyPreparedWorkout(String workoutName) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;

    String channelId = "runnerup_prepared_workout";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager.getNotificationChannel(channelId) == null) {
        manager.createNotificationChannel(
            new NotificationChannel(
                channelId, "Förberedda träningspass", NotificationManager.IMPORTANCE_DEFAULT));
      }
    }
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(
            this, 11, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification =
        new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_ongoing_notification)
            .setContentTitle("Redo att starta")
            .setContentText(workoutName)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build();
    NotificationManagerCompat.from(this).notify(11, notification);
  }

  private void notifyPreparedStrengthSession(String workoutName) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;

    String channelId = "relven_strength_prepared";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager.getNotificationChannel(channelId) == null) {
        manager.createNotificationChannel(
            new NotificationChannel(
                channelId, "Förberedda styrkepass", NotificationManager.IMPORTANCE_DEFAULT));
      }
    }
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(
            this, 12, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification =
        new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_ongoing_notification)
            .setContentTitle("Redo att starta")
            .setContentText(workoutName)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build();
    NotificationManagerCompat.from(this).notify(12, notification);
  }

  @Override
  public void onPeerConnected(Node peer) {
    super.onPeerConnected(peer);
    if (BuildConfig.DEBUG) {
      Log.i(TAG, "ListenerService.onPeerConnected: " + peer.getId());
    }
    if (phoneNodeId == null || !phoneNodeId.equals(peer.getId())) return;
    retryPendingStrengthAuthorityProjection();
  }

  private void retryPendingStrengthAuthorityProjection() {
    if (phoneNodeId == null) return;
    byte[] pending = new WearStrengthExecutionStore(this).pendingProjection();
    if (pending == null) {
      authorityRetryHandler.removeCallbacks(authorityProjectionRetry);
      authorityProjectionRetryScheduled = false;
      authorityProjectionRetryAttempt = 0;
      return;
    }
    Wearable.getMessageClient(this)
        .sendMessage(phoneNodeId, Constants.Wear.Path.MSG_STRENGTH_AUTHORITY_PROJECTION, pending)
        .addOnFailureListener(error -> Log.w(TAG, "Strength projection replay failed", error));
    if (!authorityProjectionRetryScheduled && authorityProjectionRetryAttempt < 3) {
      long[] delaysMillis = {2_000L, 5_000L, 12_000L};
      authorityProjectionRetryAttempt++;
      authorityProjectionRetryScheduled = true;
      authorityRetryHandler.postDelayed(
          authorityProjectionRetry, delaysMillis[authorityProjectionRetryAttempt - 1]);
    }
  }

  @Override
  public void onPeerDisconnected(Node peer) {
    if (BuildConfig.DEBUG) {
      Log.i(TAG, "ListenerService.onPeerDisconnected: " + peer.getId());
    }
  }

  private void maybeShowNotification() {
    Log.i(
        TAG,
        "mainActivityRunning="
            + mainActivityRunning
            + ", phoneApp="
            + phoneApp
            + " ,phoneRunning="
            + phoneRunning
            + " ,trackerState="
            + trackerState);
    if (phoneRunning == Boolean.FALSE && phoneApp == Boolean.FALSE) {
      dismissNotification();
      return;
    }

    if (phoneApp == Boolean.TRUE) {
      showNotification();
      return;
    }

    if (mainActivityRunning == null || phoneRunning == null || trackerState == null) {
      Log.i(TAG, "wait for read");
      return;
    }
    showNotification();
  }

  private static NotificationChannel mChannel;

  /**
   * Android 8.0 notification channel
   *
   * @param context
   * @return
   */
  public static String getChannelId(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (mChannel == null) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = "runnerup_ongoing";
        CharSequence name = context.getString(org.runnerup.common.R.string.app_name);
        String description =
            context.getString(org.runnerup.common.R.string.channel_notification_ongoing);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        mChannel = new NotificationChannel(id, name, importance);
        mChannel.setDescription(description);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        mChannel.setBypassDnd(true);
        notificationManager.createNotificationChannel(mChannel);
      }
      return mChannel.getId();
    }
    return "unused prior to Oreo";
  }

  private void showNotification() {
    // this intent will open the activity when the user taps the "open" action on the notification
    if (notification != null) {
      updateNotification();
      return;
    }

    // check if we have permission to post notification
    // note: permission request is initiated by MainActivity; this service won't ask
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      return;
    }

    Log.i(TAG, ": create notification");

    Intent viewIntent = new Intent(this, MainActivity.class);
    PendingIntent pendingViewIntent =
        PendingIntent.getActivity(
            this, 0, viewIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    String chanId = getChannelId(this);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(this, chanId)
            .setSmallIcon(R.drawable.ic_ongoing_notification)
            .setContentTitle(getString(org.runnerup.common.R.string.app_name))
            .setContentText(getString(org.runnerup.common.R.string.Start))
            .setContentIntent(pendingViewIntent)
            .setOngoing(true)
            .setLocalOnly(true);

    OngoingActivity ongoingActivity =
        new OngoingActivity.Builder(this, notificationId, builder)
            // Sets the animated icon that will appear on the watch face in
            // active mode.
            // If it isn't set, the watch face will use the static icon in
            // active mode.
            // .setAnimatedIcon(R.drawable.ic_walk)
            // Sets the icon that will appear on the watch face in ambient mode.
            // Falls back to Notification's smallIcon if not set.
            // If neither is set, an Exception is thrown.
            .setStaticIcon(R.drawable.ic_ongoing_notification)
            // Sets the tap/touch event so users can re-enter your app from the
            // other surfaces.
            // Falls back to Notification's contentIntent if not set.
            // If neither is set, an Exception is thrown.
            .setTouchIntent(pendingViewIntent)
            // Here, sets the text used for the Ongoing Activity (more
            // options are available for timers and stopwatches).
            .setStatus(
                new Status.Builder().addPart("Status", new TextPart(getStatusString())).build())
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build();
    ongoingActivity.apply(this);

    this.notification = builder.build();
    NotificationManagerCompat.from(this).notify(notificationId, this.notification);
  }

  private void updateNotification() {
    var ongoingActivity = OngoingActivity.recoverOngoingActivity(this);
    Log.i(TAG, ": update ongoingActivity: " + ongoingActivity);
    if (ongoingActivity == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      return;
    }

    ongoingActivity.update(
        this, new Status.Builder().addPart("Status", new TextPart(getStatusString())).build());
  }

  private void dismissNotification() {
    Log.i(TAG, ": dismissNotification");
    this.notification = null;
    NotificationManagerCompat.from(this).cancel(notificationId);
  }

  private String getStatusString() {
    if (trackerState != null) {
      switch (trackerState) {
        case INIT:
        case INITIALIZING:
        case CLEANUP:
        case ERROR:
          return getString(org.runnerup.common.R.string.Waiting_for_phone);
        case INITIALIZED:
        case CONNECTED:
          return getString(org.runnerup.common.R.string.Start_activity);
        case CONNECTING:
          return getString(org.runnerup.common.R.string.Waiting_for_GPS);
        case STARTED:
          return getString(org.runnerup.common.R.string.Activity_ongoing);
        case PAUSED:
          return getString(org.runnerup.common.R.string.Activity_paused);
        case STOPPED:
          return getString(org.runnerup.common.R.string.Activity_stopped);
      }
    }
    return getString(org.runnerup.common.R.string.Waiting_for_phone);
  }
}
