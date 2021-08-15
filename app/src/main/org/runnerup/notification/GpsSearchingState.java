package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.view.MainLayout;

import java.util.Locale;


public class GpsSearchingState implements NotificationState {
    private final Context context;
    private final GpsInformation gpsInformation;
    private final NotificationCompat.Builder builder;

    public GpsSearchingState(Context context, GpsInformation gpsInformation) {
        this.context = context;
        this.gpsInformation = gpsInformation;

        String chanId = NotificationStateManager.getChannelId(context);
        Intent i = new Intent(context, MainLayout.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        int intentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, intentFlags);

        builder = new NotificationCompat.Builder(context, chanId)
                .setContentIntent(pi)
                .setContentTitle(context.getString(R.string.Searching_for_GPS))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE);
        }
    }

    @Override
    public Notification createNotification() {
        builder.setContentText(String.format(Locale.getDefault(), "%s: %d/%d (%s)",
                context.getString(R.string.GPS_satellites),
                gpsInformation.getSatellitesFixed(), gpsInformation.getSatellitesAvailable(),
                gpsInformation.getGpsAccuracy()));

        return builder.build();
    }
}
