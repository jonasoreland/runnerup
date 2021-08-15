package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.view.MainLayout;


public class GpsBoundState implements NotificationState {
    private final Notification notification;

    public GpsBoundState(Context context) {

        String chanId = NotificationStateManager.getChannelId(context);
        Intent i = new Intent(context, MainLayout.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        int intentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, intentFlags);

        Intent startIntent = new Intent()
                .setAction(Constants.Intents.START_ACTIVITY);
        PendingIntent pendingStart = PendingIntent.getBroadcast(
                context, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | intentFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, chanId)
                .setContentIntent(pi)
                .setContentTitle(context.getString(R.string.Activity_ready))
                .setContentText(context.getString(R.string.Ready_to_start_running))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .addAction(R.drawable.ic_av_play_arrow, context.getString(R.string.Start),
                        pendingStart);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE);
        }

        notification = builder.build();
    }

    @Override
    public Notification createNotification() {
        return notification;
    }
}
