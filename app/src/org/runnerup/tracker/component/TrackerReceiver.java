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
import android.support.v4.content.LocalBroadcastManager;

import org.runnerup.common.util.Constants;
import org.runnerup.tracker.Tracker;
import org.runnerup.workout.Workout;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerReceiver extends DefaultTrackerComponent {

    private Tracker tracker;
    private Context context;
    private boolean headsetRegistered = false;

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
        registerReceivers();
        if (HeadsetButtonReceiver.getAllowStartStopFromHeadsetKey(context)) {
            headsetRegistered = true;
            HeadsetButtonReceiver.registerHeadsetListener(context);
        }
    }

    @Override
    public void onComplete(boolean discarded) {
        unregisterReceivers();
        if (headsetRegistered) {
            headsetRegistered = false;
            HeadsetButtonReceiver.unregisterHeadsetListener(context);
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TrackerReceiver.this.onReceive(context, intent);
        }
    };

    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TrackerReceiver.this.onReceive(context, intent);
        }
    };

    private void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (tracker.getState()) {
            case INIT:
            case INITIALIZING:
            case INITIALIZED:
            case CONNECTING:
            case STOPPED:
            case CLEANUP:
            case ERROR:
            case CONNECTED:
                return;
            case STARTED:
            case PAUSED:
                break;
        }

        Workout workout = tracker.getWorkout();
        if (workout == null)
            return;

        if (Constants.Intents.PAUSE_RESUME.contentEquals(action)) {
            if (workout.isPaused())
                workout.onResume(workout);
            else
                workout.onPause(workout);
            return;
        } else if (Constants.Intents.NEW_LAP.contentEquals(action)) {
            workout.onNewLapOrNextStep();
            return;
        } else if (Constants.Intents.PAUSE_WORKOUT.contentEquals(action)) {
            if (workout.isPaused())
                return;
            workout.onPause(workout);
            return;
        } else if (Constants.Intents.RESUME_WORKOUT.contentEquals(action)) {
            if (workout.isPaused())
                workout.onResume(workout);
            return;
        }
    }


    private void registerReceivers() {
        {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.Intents.PAUSE_RESUME);
            context.registerReceiver(mBroadcastReceiver, intentFilter);
        }

        {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.Intents.NEW_LAP);
            intentFilter.addAction(Constants.Intents.PAUSE_WORKOUT);
            intentFilter.addAction(Constants.Intents.RESUME_WORKOUT);
            LocalBroadcastManager.getInstance(context).registerReceiver(
                    mLocalBroadcastReceiver, intentFilter);
        }
    }

    private void unregisterReceivers() {
        try {
            context.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(mLocalBroadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
