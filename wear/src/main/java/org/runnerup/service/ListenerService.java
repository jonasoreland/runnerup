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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;
import androidx.wear.ongoing.Status.TextPart;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.view.MainActivity;
import org.runnerup.wear.WearableClient;

public class ListenerService extends WearableListenerService {

  private final int notificationId = 10;

  private WearableClient mGoogleApiClient = null;
  private Notification notification = null;
  private TrackerState trackerState = null;
  private Boolean phoneRunning = null;  // TrackerWear
  private Boolean mainActivityRunning = null;
  private Boolean phoneApp = null;  // StartActivity

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(getClass().getName(), "ListenerService.onCreate()");
    mGoogleApiClient = new WearableClient(getApplicationContext());
    mGoogleApiClient.readData(Constants.Wear.Path.WEAR_APP, dataItem -> {
        mainActivityRunning = (dataItem != null);
        maybeShowNotification();
      });
    mGoogleApiClient.readData(Constants.Wear.Path.PHONE_NODE_ID, dataItem -> {
        phoneRunning = (dataItem != null);
        maybeShowNotification();
      });
    mGoogleApiClient.readData(Constants.Wear.Path.TRACKER_STATE, dataItem -> {
        if (dataItem != null) {
          trackerState = StateService.getTrackerStateFromDataItem(dataItem);
        } else {
          trackerState = null;
        }
        maybeShowNotification();
      });
    mGoogleApiClient.readData(Constants.Wear.Path.PHONE_APP, dataItem -> {
        phoneApp = (dataItem != null);
        maybeShowNotification();
      });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(getClass().getName(), "ListenerService.onDestroy()");
    if (mGoogleApiClient != null) {
      mGoogleApiClient = null;
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(getClass().getName(), "ListenerService.onStart()");
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      Log.d(getClass().getName(), "onDataChanged: " + ev.getDataItem().getUri());
      var type = ev.getType();
      String path = ev.getDataItem().getUri().getPath();
      if (!(type == DataEvent.TYPE_DELETED || type == DataEvent.TYPE_CHANGED)) {
        // skip
        continue;
      }
      boolean deleted = (type == DataEvent.TYPE_DELETED);
      if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
          phoneRunning = !deleted;
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
  public void onPeerConnected(Node peer) {
    if (BuildConfig.DEBUG) {
      Log.d(getClass().getName(), "ListenerService.onPeerConnected: " + peer.getId());
    }
  }

  @Override
  public void onPeerDisconnected(Node peer) {
    if (BuildConfig.DEBUG) {
      Log.d(getClass().getName(), "ListenerService.onPeerDisconnected: " + peer.getId());
    }
  }

  private void maybeShowNotification() {
    Log.d(getClass().getName(), "mainActivityRunning=" + mainActivityRunning +
                       ", phoneApp=" + phoneApp +
                       " ,phoneRunning=" + phoneRunning +
                       " ,trackerState=" + trackerState);
    if (mainActivityRunning == Boolean.TRUE) {
      dismissNotification();
      return;
    }
    if (phoneRunning == Boolean.FALSE && phoneApp == Boolean.FALSE) {
      dismissNotification();
      return;
    }

    if (phoneApp == Boolean.TRUE) {
      showNotification();
      return;
    }

    if (mainActivityRunning == null || phoneRunning == null || trackerState == null) {
      Log.d(getClass().getName(), "wait for read");
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      return;
    }

    System.out.println("create notification");

    Intent viewIntent = new Intent(this, MainActivity.class);
    PendingIntent pendingViewIntent =
        PendingIntent.getActivity(this, 0, viewIntent,
                                  PendingIntent.FLAG_IMMUTABLE |
                                  PendingIntent.FLAG_UPDATE_CURRENT);
    String chanId = getChannelId(this);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(this, chanId)
        .setSmallIcon(R.drawable.ic_ongoing_notification)
        .setContentTitle(getString(org.runnerup.common.R.string.app_name))
        .setContentText(getString(org.runnerup.common.R.string.Start))
        .setContentIntent(pendingViewIntent)
        .setOngoing(true)
        .setLocalOnly(true);

    var status = getStatusString();

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
        .setStatus(new Status.Builder().addPart("Status",
                                                new TextPart(getStatusString())).build())
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .build();
    ongoingActivity.apply(this);

    this.notification = builder.build();
    NotificationManagerCompat.from(this).notify(notificationId, this.notification);
  }

  private void updateNotification() {
    var ongoingActivity = OngoingActivity.recoverOngoingActivity(this);
    System.out.println("update ongoingActivity: " + ongoingActivity);
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
    System.out.println("dismissNotification");
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
