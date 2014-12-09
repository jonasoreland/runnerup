package org.runnerup.export;

import org.runnerup.workout.WorkoutInfo;

/**
 * Created by niklas.weidemann on 2014-10-13.
 */
public interface LiveLogger {
    public void liveLog(WorkoutInfo workoutInfo, int type);
}
