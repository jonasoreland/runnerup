package org.runnerup.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class ForegroundNotificationDisplayStrategy implements NotificationDisplayStrategy {
    private final Service service;

    public ForegroundNotificationDisplayStrategy(Service service) {
        this.service = service;
    }

    @Override
    public void notify(int notificationId, Notification notification) {
        service.startForeground(notificationId, notification);
    }

    @Override
    public void cancel(int notificationId) {
        service.stopForeground(true);
    }
}
