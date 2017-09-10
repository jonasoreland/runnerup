/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

package org.runnerup.workout.feedback;

import android.content.Context;

import org.runnerup.workout.Feedback;
import org.runnerup.workout.Workout;


public class VibrationFeedback extends Feedback {

    @Override
    public boolean equals(Feedback _other) {
        return _other instanceof VibrationFeedback;
    }

    @Override
    public void emit(Workout s, Context ctx) {
        // TODO Auto-generated method stub

    }
}
