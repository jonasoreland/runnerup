/*
 * Copyright (C) 2012 - 2014 jonas.oreland@gmail.com
 *  Created by @nebmo
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.workout;

import android.location.Location;
import android.os.Build;

public class AutoPauseTrigger extends Trigger {
    private final float mAutoPauseAfter;
    private final float mAutoPauseMinSpeed;
    private boolean mPausedByUser;
    private long mStoppedMovingAt;
    private boolean mIsAutoPaused;

    private static final long NANO_IN_MILLI = 1000000;

    public AutoPauseTrigger(float autoPauseAfterSeconds, float autoPauseMinSpeed) {
        //Scaling differs depending on version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mAutoPauseAfter = autoPauseAfterSeconds * 1000 * NANO_IN_MILLI;
        } else {
            mAutoPauseAfter = autoPauseAfterSeconds * 1000;
        }
        mAutoPauseMinSpeed = autoPauseMinSpeed;
    }

    @Override
    public boolean onTick(Workout w) {
        if (mPausedByUser && w.isPaused())
            return false;
        HandleAutoPause(w);
        return false;
    }

    private void HandleAutoPause(Workout workout) {
        Double currentSpeed = workout.tracker.getCurrentSpeed();
        if (currentSpeed == null || mPausedByUser) {
            return;
        }

        if (!mIsAutoPaused) {
            Location lastLocation = workout.getLastKnownLocation();
            if (currentSpeed < mAutoPauseMinSpeed && lastLocation != null) {
                long lastTime;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    lastTime = lastLocation.getElapsedRealtimeNanos();
                } else {
                    lastTime = lastLocation.getTime();
                }
                if (mStoppedMovingAt == 0) {
                    mStoppedMovingAt = lastTime;
                } else if ((lastTime - mStoppedMovingAt) >= mAutoPauseAfter) {
                    mIsAutoPaused = true;
                    workout.onPause(workout);
                }
            } else {
                mStoppedMovingAt = 0;
            }
        } else {
            if (currentSpeed > mAutoPauseMinSpeed) {
                resetPaused(mPausedByUser);
                workout.onResume(workout);
            }
        }
    }

    private void resetPaused(boolean pausedByUser) {
        mPausedByUser = pausedByUser;
        mIsAutoPaused = false;
        mStoppedMovingAt = 0;
    }

    @Override
    public void onPause(Workout s) {
        if (!mIsAutoPaused) {
            resetPaused(true);
        }
    }

    @Override
    public void onResume(Workout s) {
        resetPaused(false);
    }

    @Override
    public void onStop(Workout s) {
    }

    @Override
    public void onRepeat(int current, int limit) {
    }

    @Override
    public void onStart(Scope what, Workout s) {
        resetPaused(false);
    }

    @Override
    public void onComplete(Scope what, Workout s) {
    }
}
