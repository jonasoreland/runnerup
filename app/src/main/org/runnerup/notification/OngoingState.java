package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.util.Formatter;
import org.runnerup.view.RunActivity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Sport;
import org.runnerup.workout.WorkoutInfo;


public class OngoingState implements NotificationState {
    private final Formatter formatter;
    private final WorkoutInfo workoutInfo;
    private final Context context;
    private final NotificationCompat.Builder builder;

    public OngoingState(Formatter formatter, WorkoutInfo workoutInfo, Context context) {
        this.formatter = formatter;
        this.workoutInfo = workoutInfo;
        this.context = context;

        String chanId = NotificationStateManager.getChannelId(context);

        Intent i = new Intent(context, RunActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        int intentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, intentFlags);

        Intent lapIntent = new Intent()
                .setAction(Constants.Intents.NEW_LAP);
        PendingIntent pendingLap = PendingIntent.getBroadcast(
                context, 0, lapIntent, PendingIntent.FLAG_UPDATE_CURRENT | intentFlags);

        Intent pauseIntent = new Intent()
                .setAction(Constants.Intents.PAUSE_RESUME);
        PendingIntent pendingPause = PendingIntent.getBroadcast(
                context, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | intentFlags);

        builder = new NotificationCompat.Builder(context, chanId)
                .setTicker(context.getString(R.string.RunnerUp_activity_started))
                .setContentIntent(pi)
                .setContentTitle(Sport.textOf(context.getResources(), workoutInfo.getSport()))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.ic_av_newlap, context.getString(R.string.Lap), pendingLap)
                .addAction(R.drawable.ic_av_pause, context.getString(R.string.Pause), pendingPause);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE);
        }
    }

    @Override
    public Notification createNotification() {
        String distance = formatter.formatDistance(Formatter.Format.TXT_SHORT,
                Math.round(workoutInfo.getDistance(Scope.ACTIVITY)));
        String time = formatter.formatElapsedTime(Formatter.Format.TXT_LONG,
                Math.round(workoutInfo.getTime(Scope.ACTIVITY)));
        String pace = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT,
                workoutInfo.getSpeed(Scope.ACTIVITY));

        String content = String.format("%s: %s %s: %s %s: %s",
                context.getString(R.string.distance), distance,
                context.getString(R.string.time), time,
                context.getString(R.string.pace), pace);
        builder.setContentText(content);

        Notification n = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (workoutInfo.isPaused()) {
                n.actions[1] = new Notification.Action(R.drawable.ic_av_play_arrow, context.getString(R.string.Resume), n.actions[1].actionIntent);
            } else {
                n.actions[1] = new Notification.Action(R.drawable.ic_av_pause, context.getString(R.string.Pause), n.actions[1].actionIntent);
            }
        }
        return n;
    }
}
