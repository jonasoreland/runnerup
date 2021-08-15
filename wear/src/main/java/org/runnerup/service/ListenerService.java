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

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.view.MainActivity;


public class ListenerService extends WearableListenerService {

    private final int notificationId = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        System.err.println("ListenerService.onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.err.println("ListenerService.onDestroy()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.err.println("ListenerService.onStart()");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent ev : dataEvents) {
            System.err.println("onDataChanged: " + ev.getDataItem().getUri());
            String path = ev.getDataItem().getUri().getPath();
            if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
                handleNotification(ev);
            }
        }
    }

    private void handleNotification(DataEvent ev) {
        if (ev.getType() == DataEvent.TYPE_CHANGED) {
            showNotification();
        } else if (ev.getType() == DataEvent.TYPE_DELETED) {
            dismissNotification();
        }
    }

    private static NotificationChannel mChannel;
    /**
     * Android 8.0 notification channel
     * @param context
     * @return
     */
    public static String getChannelId(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mChannel == null) {
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                String id = "runnerup_ongoing";
                CharSequence name = context.getString(R.string.app_name);
                String description = context.getString(R.string.channel_notification_ongoing);
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
        Intent viewIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingViewIntent = PendingIntent.getActivity(this, 0, viewIntent, PendingIntent.FLAG_IMMUTABLE);
        String chanId = getChannelId(this);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, chanId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.Start))
                .setContentIntent(pendingViewIntent)
                .setOngoing(true)
                .setLocalOnly(true);

        Notification notification = builder.build();
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(notificationId, notification);
    }

    private void dismissNotification() {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.cancel(notificationId);
    }
}
