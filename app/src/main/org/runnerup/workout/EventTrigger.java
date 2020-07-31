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

public class EventTrigger extends Trigger {

    Scope scope = Scope.STEP;
    Event event = Event.STARTED;
    private int counter = 0;
    int skipCounter = 0;
    int maxCounter = Integer.MAX_VALUE;

    @Override
    public void onInit(Workout s) {
        counter = 0;
    }

    @Override
    public boolean onTick(Workout w) {
        return false;
    }

    @Override
    void fire(Workout s) {
        if (counter >= skipCounter && (counter < maxCounter)) {
            super.fire(s);
        }
        counter++;
    }

    @Override
    public void onRepeat(int current, int limit) {
        counter = 0;
    }

    @Override
    public void onStart(Scope what, Workout s) {
        if (this.scope == what && this.event == Event.STARTED) {
            fire(s);
        }
    }

    @Override
    public void onPause(Workout s) {
        if (this.event == Event.PAUSED) {
            fire(s);
        }
    }

    @Override
    public void onStop(Workout s) {
        if (this.event == Event.STOPPED) {
            fire(s);
        }
    }

    @Override
    public void onResume(Workout s) {
        if (this.event == Event.RESUMED) {
            fire(s);
        }
    }

    @Override
    public void onComplete(Scope what, Workout s) {
        if (this.scope == what && this.event == Event.COMPLETED) {
            fire(s);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "[ EventTrigger: " + this.scope + " " + this.event + " skipCounter: " + skipCounter
                + " ]";
    }
}
