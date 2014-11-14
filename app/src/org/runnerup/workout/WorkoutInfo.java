package org.runnerup.workout;

import java.util.List;

/**
 * Created by niklas.weidemann on 2014-11-14.
 */
public interface WorkoutInfo {
    double get(Scope scope, Dimension d);

    double getDistance(Scope scope);

    double getTime(Scope scope);

    double getSpeed(Scope scope);

    double getPace(Scope scope);

    double getDuration(Scope scope, Dimension dimension);

    double getRemaining(Scope scope, Dimension dimension);

    double getHeartRate(Scope scope);

    int getSport();

    int getStepCount();

    boolean isLastStep();

    List<Workout.StepListEntry> getSteps();

    Step getCurrentStep();
}
