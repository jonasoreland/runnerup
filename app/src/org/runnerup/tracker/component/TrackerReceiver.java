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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.tracker.Tracker;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerReceiver extends DefaultTrackerComponent {

    private Tracker tracker;
    private Context context;

    public static final String NAME = "Receiver";

    public TrackerReceiver(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResultCode onInit(final Callback callback, final Context context) {
        this.context = context;
        return ResultCode.RESULT_OK;
    }

    @Override
    public void onStart() {
        registerWorkoutBroadcastsListener();
    }

    @Override
    public void onComplete(boolean discarded) {
        unregisterWorkoutBroadcastsListener();
    }

    private final BroadcastReceiver mWorkoutBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Constants.Intents.PAUSE_RESUME)) {
                if (tracker.getState() == TrackerState.PAUSED)
                    tracker.getWorkout().onResume(tracker.getWorkout());
                else if (tracker.getState() == TrackerState.STARTED)
                    tracker.getWorkout().onPause(tracker.getWorkout());
            } else if (action.equals(Constants.Intents.NEW_LAP)) {
                tracker.getWorkout().onNewLap();
            }
        }
    };

    private void registerWorkoutBroadcastsListener() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.Intents.PAUSE_RESUME);
        context.registerReceiver(mWorkoutBroadcastReceiver, intentFilter);
    }

    private void unregisterWorkoutBroadcastsListener() {
        try {
            context.unregisterReceiver(mWorkoutBroadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
