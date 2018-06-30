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

import android.content.Context;

import java.util.HashMap;

/**
 * Created by jonas on 12/11/14.
 *
 */

public abstract class DefaultTrackerComponent implements TrackerComponent {

    /**
     * Component name
     */
    public abstract String getName();

    /**
     * Called by Tracker during initialization
     */
    @Override
    public ResultCode onInit(Callback callback, Context context) {
        return ResultCode.RESULT_UNKNOWN;
    }

    @Override
    public ResultCode onConnecting(Callback callback, Context context) {
        return ResultCode.RESULT_OK;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onConnected() {
    }

    /**
     * Called by Tracker before start
     *   Component shall populate bindValues
     *   with objects that will then be passed
     *   to workout
     */
    public void onBind(HashMap<String, Object> bindValues) {
    }

    /**
     * Called by Tracker when workout starts
     */
    @Override
    public void onStart() {
    }

    /**
     * Called by Tracker when workout is paused
     */
    @Override
    public void onPause() {
    }

    /**
     * Called by Tracker when workout is resumed
     */
    @Override
    public void onResume() {
    }

    /**
     * Called by Tracker when workout is complete
     */
    @Override
    public void onComplete(boolean discarded) {
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        return ResultCode.RESULT_OK;
    }
}
