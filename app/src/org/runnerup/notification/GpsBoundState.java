package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.util.Constants;
import org.runnerup.view.MainLayout;
import org.runnerup.view.StartActivity;

public class GpsBoundState implements NotificationState {
    private final Context context;

    public GpsBoundState(Context context) {
        this.context = context;
    }

    @Override
    public Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Intent i = new Intent(context, MainLayout.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        builder.setContentIntent(pi);
        builder.setContentTitle("Activity ready");
        builder.setContentText("Ready to start running!");
        builder.setSmallIcon(R.drawable.icon);

        return builder.build();
    }
}
