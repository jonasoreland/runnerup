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

import java.util.ArrayList;


public class ListTrigger extends Trigger {

    private final Scope scope;
    private final Dimension dimension;

    private int pos;
    private final ArrayList<Double> triggerTimes;

    ListTrigger(Dimension d, Scope s, ArrayList<Double> triggerTimes){
        this.dimension = d;
        this.scope = s;

        if (triggerTimes == null) {
            triggerTimes = new ArrayList<>();
        }
        this.triggerTimes = triggerTimes;
        pos = 0;
    }

    @Override
    public boolean onTick(Workout w) {
        // add a bit of margin, NOTE: less than 0.5s
        // For distance 4:00 /km is just over 4 m/s
        final double margin = dimension == Dimension.TIME ? 0.4d : 2d;

        double now = w.getRemaining(scope, dimension) - margin;
        if (pos < triggerTimes.size() && now <= triggerTimes.get(pos)) {
            scheduleNext(w, now);
            fire(w);
        }
        return false;
    }

    private void scheduleNext(Workout w, double now) {
        while (pos < triggerTimes.size() && now <= triggerTimes.get(pos)) {
            pos++;
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
