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

import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AutoPauseTrigger extends Trigger {
    private final float mAutoPauseAfterSeconds;
    private final float mAutoPauseMinSpeed;
    private boolean mPausedByUser;
    private long mStoppedMovingAt;
    private boolean mHasStopped;
    private boolean mIsAutoPaused;

    public AutoPauseTrigger(float autoPauseAfterSeconds, float autoPauseMinSpeed) {
        mAutoPauseAfterSeconds = autoPauseAfterSeconds;
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
        Location lastLocation = workout.getLastKnownLocation();
        double currentSpeed = workout.getSpeed(Scope.CURRENT);
        if (!workout.isEnabled(Dimension.SPEED, Scope.CURRENT)) {
            currentSpeed = 0;
        }
        if (currentSpeed < mAutoPauseMinSpeed && lastLocation != null) {
            if (!mIsAutoPaused && mHasStopped
                    && (lastLocation.getTime() - mStoppedMovingAt) > mAutoPauseAfterSeconds * 1000) {
                mIsAutoPaused = true;
                setPaused(workout, true);
            } else if (!mHasStopped && !mIsAutoPaused) {
                mHasStopped = true;
                mStoppedMovingAt = lastLocation.getTime();
            }
        } else if (mIsAutoPaused && currentSpeed > mAutoPauseMinSpeed) {
            mIsAutoPaused = false;
            mHasStopped = false;
            setPaused(workout, false);
        }
    }

    private void setPaused(Workout workout, boolean pause) {
        if (pause) {
            workout.onPause(workout);
        } else {
            workout.onResume(workout);
        }
    }

    @Override
    public void onPause(Workout s) {
        if (!mIsAutoPaused) {
            mPausedByUser = true;
            mIsAutoPaused = false;
            mHasStopped = false;
        }

    }

    @Override
    public void onResume(Workout s) {
        mPausedByUser = false;
        mIsAutoPaused = false;
        mHasStopped = false;
    }

    @Override
    public void onStop(Workout s) {

    }

    @Override
    public void onRepeat(int current, int limit) {

    }

    @Override
    public void onStart(Scope what, Workout s) {

    }

    @Override
    public void onComplete(Scope what, Workout s) {

    }
}
