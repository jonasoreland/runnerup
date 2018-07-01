package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.util.SupportWrapper;
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
        builder = SupportWrapper.Builder(context, chanId);
        Intent i = new Intent(context, MainLayout.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        builder.setContentIntent(pi);
        builder.setContentTitle(context.getString(R.string.Searching_for_GPS));
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setOnlyAlertOnce(true);
        org.runnerup.util.NotificationCompat.setLocalOnly(builder);
        org.runnerup.util.NotificationCompat.setVisibility(builder);
        org.runnerup.util.NotificationCompat.setCategory(builder);
    }

    @Override
    public Notification createNotification() {
        builder.setContentText(String.format(Locale.getDefault(), "%s: %d/%d%s",
                context.getString(R.string.GPS_satellites),
                gpsInformation.getSatellitesFixed(), gpsInformation.getSatellitesAvailable(),
                gpsInformation.getGpsAccuracy()));

        return builder.build();
    }
}
