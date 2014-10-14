package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.gpstracker.GpsInformation;
import org.runnerup.view.StartActivity;

public class GpsSearchingState implements NotificationState {
    private final Context context;
    private final GpsInformation gpsInformation;

    public GpsSearchingState(Context context, GpsInformation gpsInformation) {
        this.context = context;
        this.gpsInformation = gpsInformation;
    }

    @Override
    public Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Intent i = new Intent(context, StartActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        builder.setContentIntent(pi);
        builder.setContentTitle("Searching for GPS");
        builder.setContentText(String.format("GPS Satellites: %d/%d%s",
                gpsInformation.getSatellitesFixed(), gpsInformation.getSatellitesAvailable(),
                gpsInformation.getGpsAccuracy()));
        builder.setSmallIcon(R.drawable.icon);

        return builder.build();
    }
}
