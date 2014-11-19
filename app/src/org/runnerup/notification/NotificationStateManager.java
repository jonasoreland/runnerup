package org.runnerup.notification;

import android.app.Notification;

public class NotificationStateManager {
    private static final int NOTIFICATION_ID = 1;
    private final NotificationDisplayStrategy strategy;

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
