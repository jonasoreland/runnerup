package org.runnerup.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.runnerup.R;
import org.runnerup.gpstracker.WorkoutProvider;
import org.runnerup.util.Formatter;
import org.runnerup.view.RunActivity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutInfo;

public class OngoingState implements NotificationState {
    private final Formatter formatter;
    private final WorkoutProvider workoutProvider;
    private final Context context;

    public OngoingState(Formatter formatter, WorkoutProvider workoutProvider, Context context) {
        this.formatter = formatter;
        this.workoutProvider = workoutProvider;
        this.context = context;
    }

    @Override
    public Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Intent i = new Intent(context, RunActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

        WorkoutInfo workout = workoutProvider.getWorkoutInfo();
        if (workout == null) throw new IllegalStateException("workout is null!");

        String distance = formatter.formatDistance(Formatter.TXT_SHORT, Math.round(workout.getDistance(Scope.WORKOUT)));
        String time = formatter.formatElapsedTime(Formatter.TXT_LONG, Math.round(workout.getTime(Scope.WORKOUT)));
        String pace = formatter.formatPace(Formatter.TXT_SHORT, workout.getPace(Scope.WORKOUT));

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

    private String getDistance(Workout workout) {
        double ad = workout.getDistance(Scope.WORKOUT);
        return formatter.formatDistance(Formatter.TXT_SHORT, Math.round(ad));
    }

    private String getPace(Workout workout) {
        double ap = workout.getPace(Scope.WORKOUT);
        return formatter.formatPace(Formatter.TXT_SHORT, ap);
    }

    private String getTime(Workout workout) {
        double at = workout.getTime(Scope.WORKOUT);
        return formatter.formatElapsedTime(Formatter.TXT_LONG, Math.round(at));
    }
}
