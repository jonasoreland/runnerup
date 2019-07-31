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

import android.content.Context;

import java.util.HashMap;


public abstract class Feedback {

    @SuppressWarnings("EmptyMethod")
    public void onInit(Workout s) {
    }

    public void onBind(Workout s, HashMap<String, Object> bindValues) {
    }

    public void onStart(Workout s) {
    }

    public void onEnd(Workout s) {
    }

    /**
     * compare feedback to another feedback so that same information isn't
     * emitted twice (or more) during one tick i.e different triggers can have
     * same feedback
     * 
     * @param other
     */
    public abstract boolean equals(Feedback other);

    /**
     * Emit the feedback
     */
    public abstract void emit(Workout s, Context ctx);
}
