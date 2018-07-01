package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.util.SupportWrapper;
import org.runnerup.view.MainLayout;


public class GpsBoundState implements NotificationState {
    private final Notification notification;

    public GpsBoundState(Context context) {

        String chanId = NotificationStateManager.getChannelId(context);
        NotificationCompat.Builder builder = SupportWrapper.Builder(context, chanId);
        Intent i = new Intent(context, MainLayout.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        builder.setContentIntent(pi);
        builder.setContentTitle(context.getString(R.string.Activity_ready));
        builder.setContentText(context.getString(R.string.Ready_to_start_running));
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setOnlyAlertOnce(true);
        org.runnerup.util.NotificationCompat.setLocalOnly(builder);
        org.runnerup.util.NotificationCompat.setVisibility(builder);
        org.runnerup.util.NotificationCompat.setCategory(builder);

        notification = builder.build();
    }

    @Override
    public Notification createNotification() {
        return notification;
    }
}
