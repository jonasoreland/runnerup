package org.runnerup.notification;

import android.app.Notification;

public interface NotificationDisplayStrategy {
    void notify(int notificationId, Notification notification);

    void cancel(int notificationId);
}
