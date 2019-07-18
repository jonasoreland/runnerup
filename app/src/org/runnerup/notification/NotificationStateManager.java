package org.runnerup.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import org.runnerup.R;


public class NotificationStateManager {
    private static final int NOTIFICATION_ID = 1;
    private final NotificationDisplayStrategy strategy;

    /**
     * Android 8.0 notification channel
     * @param context
     * @return
     */
    public static String getChannelId(Context context) {
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
