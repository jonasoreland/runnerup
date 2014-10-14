package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.gpstracker.CurrentTrackerInformation;
import org.runnerup.util.Formatter;
import org.runnerup.view.RunActivity;

public class OngoingState implements NotificationState {
    private final Formatter formatter;
    private final CurrentTrackerInformation currentTrackerInformation;
    private final Context context;

    public OngoingState(Formatter formatter, CurrentTrackerInformation currentTrackerInformation, Context context) {
        this.formatter = formatter;
        this.currentTrackerInformation = currentTrackerInformation;
        this.context = context;
    }

    @Override
    public Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Intent i = new Intent(context, RunActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        String distance = getDistance();
        String time = getTime();
        String pace = getPace();

        builder.setTicker("RunnerUp activity started");
        builder.setContentIntent(pi);
        builder.setContentTitle("Activity ongoing");
        String content = String.format("Dist: %s, Time: %s, Pace: %s", distance, time, pace);
        builder.setContentText(content);
        builder.setSmallIcon(R.drawable.icon);
        builder.setOngoing(true);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle(builder);
        bigTextStyle.setBigContentTitle("Activity ongoing");
        bigTextStyle.bigText(String.format("Distance: %s,\nTime: %s\nPace: %s", distance, time, pace));
        builder.setStyle(bigTextStyle);

        return builder.build();
    }

    private String getDistance() {
        Double ad = currentTrackerInformation.getDistance();
        if(ad == null) return "0";

        return formatter.formatDistance(Formatter.TXT_SHORT, Math.round(ad));
    }

    private String getPace() {
        Double ap = currentTrackerInformation.getCurrentPace();
        if(ap == null) return "0";

        return formatter.formatPace(Formatter.TXT_SHORT, ap);
    }

    private String getTime() {
        Double at = currentTrackerInformation.getTime();
        if(at == null) return "0";

        return formatter.formatElapsedTime(Formatter.TXT_LONG, Math.round(at));
    }
}
