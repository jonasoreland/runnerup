/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
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

import org.runnerup.BuildConfig;


public class PauseStep extends Step {

    private long elapsedTime = 0;
    private long lastTime = 0;
    private double saveDurationValue = 0;

    @Override
    public void onInit(Workout s) {
        super.onInit(s);
        if (BuildConfig.DEBUG && (getIntensity() != Intensity.RESTING || getDurationType() != Dimension.TIME)) {
            throw new AssertionError(String.format("PauseStep with intensity %s and duration %s",
                    getIntensity(),
                    getDurationType())); }
        saveDurationValue = super.durationValue;
    }

    @Override
    public void onStart(Scope what, Workout s) {
        if (what == Scope.STEP) {
            s.tracker.pause();
            elapsedTime = 0;
            lastTime = android.os.SystemClock.elapsedRealtime();
            for (Trigger t : triggers) {
                t.onStart(what, s);
            }
        } else {
            super.onStart(what, s);
        }
    }

    @Override
    public void onComplete(Scope what, Workout s) {
        if (what == Scope.STEP) {
            super.durationValue = saveDurationValue;
        }
        super.onComplete(what, s);
    }

    private void sample(boolean paused) {
        long now = android.os.SystemClock.elapsedRealtime();
        long diff = now - lastTime;
        lastTime = now;
        elapsedTime += diff;
        if (paused) {
            /*
             * to make sure that actual pause time is save...we increase the
             * durationValue every time elapsedTime is increased if we're
             * currently paused to handle repeats, this is later restored in
             * onComplete()
             */
            super.durationValue += ((double) diff) / 1000.0;
        }
    }

    @Override
    public void onPause(Workout s) {
        sample(true);

        for (Trigger t : triggers) {
            t.onPause(s);
        }
    }

    @Override
    public boolean onTick(Workout s) {
        sample(s.isPaused());
        return super.onTick(s);
    }

    @Override
    public void onResume(Workout s) {
        sample(false);

        for (Trigger t : triggers) {
            t.onResume(s);
        }
    }

    @Override
    public double getTime(Workout w, Scope s) {
        sample(w.isPaused());
        switch (s) {
            case STEP:
            case LAP:
                return elapsedTime / 1000.0;
            case ACTIVITY:
            case CURRENT:
                break;
        }
        return super.getTime(w, s);
    }

    @Override
    public boolean isPauseStep() { return true; }
}
