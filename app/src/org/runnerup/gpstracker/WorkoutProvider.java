package org.runnerup.gpstracker;

import android.location.Location;

import org.runnerup.workout.WorkoutInfo;

public interface WorkoutProvider {
    WorkoutInfo getWorkoutInfo();

    Location getLastKnownLocation();
}
