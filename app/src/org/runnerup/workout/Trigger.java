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

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;


public abstract class Trigger implements TickComponent {

    ArrayList<Feedback> triggerAction = new ArrayList<>();
    final ArrayList<TriggerSuppression> triggerSuppression = new ArrayList<>();

    @Override
    public void onInit(Workout s) {
        for (Feedback f : triggerAction) {
            f.onInit(s);
        }
    }

    @Override
    public void onBind(Workout s, HashMap<String, Object> bindValues) {
        for (Feedback f : triggerAction) {
            f.onBind(s, bindValues);
        }
    }

    @Override
    public void onEnd(Workout s) {
        for (Feedback f : triggerAction) {
            f.onEnd(s);
        }
    }

    void fire(Workout w) {
        for (TriggerSuppression s : triggerSuppression) {
            if (s.suppress(this, w)) {
                Log.e(getClass().getName(), "trigger: " + this + "suppressed by: " + s);
                return;
            }
        }
        for (Feedback f : triggerAction) {
            w.addFeedback(f);
        }
    }
}
