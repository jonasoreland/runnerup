package org.runnerup.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import org.runnerup.R;


public class NotificationStateManager {
    private static final int NOTIFICATION_ID = 1;
    private final NotificationDisplayStrategy strategy;

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

    public NotificationStateManager(NotificationDisplayStrategy strategy) {
        this.strategy = strategy;
    }

    public void displayNotificationState(NotificationState state) {
        if (state == null) throw new IllegalArgumentException("state is null");

        Notification notification = state.createNotification();
        strategy.notify(NOTIFICATION_ID, notification);
    }

    public void cancelNotification() {
        strategy.cancel(NOTIFICATION_ID);
    }
}
