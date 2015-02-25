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

import android.annotation.TargetApi;
import android.os.Build;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.FROYO)
public class ListTrigger extends Trigger {

    boolean remaining = false;
    Scope scope = Scope.ACTIVITY;
    Dimension dimension = Dimension.TIME;

    int pos = Integer.MAX_VALUE;
    ArrayList<Double> triggerTimes = new ArrayList<Double>();

    @Override
    public boolean onTick(Workout w) {
        if (pos < triggerTimes.size()) {
            if (remaining == false) {
                double now = w.get(scope, dimension);
                if (now >= triggerTimes.get(pos)) {
                    fire(w);
                    scheduleNext(w, now);
                }
            } else {
                double now = w.getRemaining(scope, dimension);
                if (now <= triggerTimes.get(pos)) {
                    fire(w);
                    scheduleNext(w, now);
                }
            }
        }
        return false;
    }

    private void scheduleNext(Workout w, double now) {
        if (remaining == false) {
            while (pos < triggerTimes.size() && now >= triggerTimes.get(pos)) {
                pos++;
            }
        } else {
            while (pos < triggerTimes.size() && now <= triggerTimes.get(pos)) {
                pos++;
            }
        }

        if (pos >= triggerTimes.size()) {
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
            pos = 0;
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
}
