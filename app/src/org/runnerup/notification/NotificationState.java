package org.runnerup.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public interface NotificationState {
    Notification createNotification();
}
