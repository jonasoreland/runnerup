/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.tracker.component;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.workout.WorkoutInfo;

@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerWear extends DefaultTrackerComponent implements WorkoutObserver {

    public static final String NAME = "WEAR";

    public TrackerWear(Tracker tracker) {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TrackerComponent.ResultCode onInit(final Callback callback, Context context) {
        return TrackerComponent.ResultCode.RESULT_NOT_SUPPORTED;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void workoutEvent(WorkoutInfo workoutInfo, int type) {
    }
}
