package org.runnerup.notification;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;


public class ForegroundNotificationDisplayStrategy implements NotificationDisplayStrategy {
    private final Service service;

    public ForegroundNotificationDisplayStrategy(Service service) {
        this.service = service;
    }

    @Override
    public void notify(int notificationId, Notification notification) {
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(service, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && ContextCompat.checkSelfPermission(service, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
            }
        }
        ServiceCompat.startForeground(service, notificationId, notification, type);
    }

    @Override
    public void cancel(int notificationId) {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }
}
