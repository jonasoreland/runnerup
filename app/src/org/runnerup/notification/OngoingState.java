package org.runnerup.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.util.Formatter;
import org.runnerup.view.RunActivity;
import org.runnerup.workout.WorkoutInfo;
import org.runnerup.workout.Scope;

@TargetApi(Build.VERSION_CODES.FROYO)
public class OngoingState implements NotificationState {
    private final Formatter formatter;
    private final WorkoutInfo workoutInfo;
    private final Context context;
    private final NotificationCompat.Builder builder;

    public OngoingState(Formatter formatter, WorkoutInfo workoutInfo, Context context) {
        this.formatter = formatter;
        this.workoutInfo = workoutInfo;
        this.context = context;

        builder = new NotificationCompat.Builder(context);
        Intent i = new Intent(context, RunActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(Constants.Intents.FROM_NOTIFICATION, true);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        builder.setTicker(context.getString(R.string.runnerup_activity_started));
        builder.setContentIntent(pi);
        builder.setContentTitle(context.getString(R.string.activity_ongoing));
        builder.setSmallIcon(R.drawable.icon);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);
        builder.setLocalOnly(true);
    }

    @Override
    public Notification createNotification() {
        String distance = formatter.formatDistance(Formatter.TXT_SHORT,
                Math.round(workoutInfo.getDistance(Scope.WORKOUT)));
        String time = formatter.formatElapsedTime(Formatter.TXT_LONG,
                Math.round(workoutInfo.getTime(Scope.WORKOUT)));
        String pace = formatter.formatPace(Formatter.TXT_SHORT,
                workoutInfo.getPace(Scope.WORKOUT));

        String content = String.format("%s: %s %s: %s %s: %s",
                context.getString(R.string.distance), distance,
                context.getString(R.string.time), time,
                context.getString(R.string.pace), pace);
        builder.setContentText(content);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle(builder);
        bigTextStyle.setBigContentTitle(context.getString(R.string.activity_ongoing));
        bigTextStyle.bigText(String.format("%s: %s,\n%s: %s\n%s: %s",
                context.getString(R.string.distance), distance,
                context.getString(R.string.time), time,
                context.getString(R.string.pace), pace));
        builder.setStyle(bigTextStyle);

        return builder.build();
    }
}
