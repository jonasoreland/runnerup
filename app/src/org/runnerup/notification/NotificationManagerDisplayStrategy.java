package org.runnerup.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class NotificationManagerDisplayStrategy implements NotificationDisplayStrategy {
    private final NotificationManager notificationManager;

    public NotificationManagerDisplayStrategy(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    @Override
    public void notify(int notificationId, Notification notification) {
        notificationManager.notify(notificationId, notification);
    }

    @Override
    public void cancel(int notificationId) {
        notificationManager.cancel(notificationId);
    }
}
