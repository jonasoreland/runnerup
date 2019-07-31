package org.runnerup.notification;

import android.app.Notification;


interface NotificationDisplayStrategy {
    void notify(int notificationId, Notification notification);

    void cancel(int notificationId);
}
