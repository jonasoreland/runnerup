package org.runnerup.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public interface NotificationDisplayStrategy {
    void notify(int notificationId, Notification notification);

    void cancel(int notificationId);
}
