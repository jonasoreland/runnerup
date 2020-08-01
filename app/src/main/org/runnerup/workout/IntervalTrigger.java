/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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


import androidx.annotation.NonNull;

public class IntervalTrigger extends Trigger {

    Scope scope = Scope.ACTIVITY;
    Dimension dimension = Dimension.TIME;

    double first = 120;
    double interval = 120;

    private double next = 0;

    @Override
    public boolean onTick(Workout w) {
        if (next != 0) {
            double now = w.get(scope, dimension);
            if (now >= next) {
                fire(w);
                scheduleNext(w, now);
            }
        }
        return false;
    }

    private void scheduleNext(Workout w, double now) {
        if (interval == 0) {
            // last occurrence (maybe first)
            next = 0;
        } else {
            while (next <= now) {
                next += interval;
            }
            //int count = 0; //endless
            //if (count != 0 && (next > (first + interval * (count - 1)))) {
            //    // no more occurrences
            //    next = 0;
            //}
        }
        if (next == 0) {
            for (Feedback f : triggerAction) {
                f.onEnd(w);
            }
        }
    }

    @Override
    public void onRepeat(int current, int limit) {
    }

    @Override
    public void onStart(Scope what, Workout s) {
        if (this.scope == what) {
            next = first;
            for (Feedback f : triggerAction) {
                f.onStart(s);
            }
        }
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

    @NonNull
    @Override
    public String toString() {
        return "[ IntervalTrigger: " + this.scope + " " + this.dimension + " first: " + first
                + " interval: " + interval + " ]";
    }
}
