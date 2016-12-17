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

import java.util.HashMap;

interface WorkoutComponent {
    /**
     * Called before workout begins
     */
    void onInit(Workout s);

    /**
     * Called at least once before onStart Can be called later if orientation
     * changes
     */
    void onBind(Workout s, HashMap<String, Object> bindValues);

    /**
     * Called before onStart
     */
    void onRepeat(int current, int limit);

    /**
     * Called when *what* starts
     */
    void onStart(Scope what, Workout s);

    /**
     * Called when user press PauseButton after this either onResume or
     * onComplete will be called
     */
    void onPause(Workout s);

    /**
     * Called when user press StopButton after this either onResume or
     * onComplete will be called
     */
    void onStop(Workout s);

    /**
     * Called when user press ResumeButton
     */
    void onResume(Workout s);

    /**
     * Called when *what* is completed
     */
    void onComplete(Scope what, Workout s);

    /**
     * Called after workout has ended
     */
    void onEnd(Workout s);
}
