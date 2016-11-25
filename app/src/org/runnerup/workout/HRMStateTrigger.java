package org.runnerup.workout;

import android.util.Log;

public class HRMStateTrigger extends Trigger {
    private boolean isConnected;

    @Override
    public boolean onTick(Workout w) {
        boolean newState = getState(w);
        if (newState != isConnected) {
            isConnected = newState;
            fire(w);
        }
        return false;
    }

    @Override
    public void onRepeat(int current, int limit) {

    }

    private boolean getState(Workout s) {
        return (s.getHeartRate(Scope.CURRENT) != 0);
    }

    @Override
    public void onStart(Scope what, Workout s) {
        isConnected = getState(s);
    }

    @Override
    public void onPause(Workout s) {

    }

    @Override
    public void onStop(Workout s) {

    }

    @Override
    public void onResume(Workout s) {

    }

    @Override
    public void onComplete(Scope what, Workout s) {

    }
}
