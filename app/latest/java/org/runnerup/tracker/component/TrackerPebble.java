/*
 * Copyright (C) 2015 weides@gmail.com
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutInfo;
import org.runnerup.workout.WorkoutStepListener;

import java.text.DecimalFormat;
import java.util.HashMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class TrackerPebble extends DefaultTrackerComponent implements WorkoutObserver, WorkoutStepListener, ValueModel.ChangeListener<TrackerState> {
    public static final String NAME = "PEBBLE";
    private Context context;
    private PebbleKit.PebbleDataReceiver sportsDataHandler = null;
    private Formatter formatter;
    private boolean isMetric;
    private boolean bMetricSent;
    private Tracker tracker;

    public TrackerPebble(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TrackerComponent.ResultCode onInit(final Callback callback, final Context context) {
        this.context = context;
        if (!isConnected() || !PebbleKit.areAppMessagesSupported(context)) {
            return ResultCode.RESULT_NOT_SUPPORTED;
        }
        customizeWatchApp();
        PebbleKit.startAppOnPebble(context, Constants.SPORTS_UUID);

        sportsDataHandler = new PebbleKit.PebbleDataReceiver(Constants.SPORTS_UUID) {
            @Override
            public void receiveData(final Context pebbleContext, final int transactionId, final PebbleDictionary data) {
                try {
                    int newState = data.getUnsignedIntegerAsLong(Constants.SPORTS_STATE_KEY).intValue();
                    PebbleKit.sendAckToPebble(context, transactionId);
                    if (newState == Constants.SPORTS_STATE_PAUSED || newState == Constants.SPORTS_STATE_RUNNING) {
                        if (tracker.getWorkout() == null) {
                            Intent startBroadcastIntent = new Intent();
                            startBroadcastIntent.setAction(org.runnerup.common.util.Constants.Intents.START_WORKOUT);
                            context.sendBroadcast(startBroadcastIntent);
                        } else if (tracker.getWorkout().isPaused()) {
                            sendLocalBroadcast(org.runnerup.common.util.Constants.Intents.RESUME_WORKOUT);
                        } else {
                            sendLocalBroadcast(org.runnerup.common.util.Constants.Intents.PAUSE_WORKOUT);
                        }
                    }
                } catch (Exception ex) {
                    Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show();
                }
            }
        };
        PebbleKit.registerReceivedDataHandler(context, sportsDataHandler);
        return ResultCode.RESULT_OK;
    }

    private void sendLocalBroadcast(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
        this.isMetric = Formatter.getUseKilometers(context.getResources(), PreferenceManager.getDefaultSharedPreferences(context), null);
    }

    @Override
    public boolean isConnected() {
        return PebbleKit.isWatchConnected(context);
    }

    @Override
    public void workoutEvent(WorkoutInfo workoutInfo, int type) {
        if (!isConnected())
            return;

        PebbleDictionary data = new PebbleDictionary();
        data.addString(Constants.SPORTS_TIME_KEY, formatter.format(Formatter.TXT_SHORT, Dimension.TIME, workoutInfo.get(Scope.ACTIVITY, Dimension.TIME)));
        data.addString(Constants.SPORTS_DISTANCE_KEY, formatter.format(Formatter.TXT, Dimension.DISTANCE, workoutInfo.get(Scope.ACTIVITY, Dimension.DISTANCE)));
        data.addString(Constants.SPORTS_DATA_KEY, formatter.format(Formatter.TXT_SHORT, Dimension.PACE, workoutInfo.get(Scope.ACTIVITY, Dimension.PACE)));
        data.addUint8(Constants.SPORTS_LABEL_KEY, (byte) Constants.SPORTS_DATA_PACE);
        data.addUint8(Constants.SPORTS_UNITS_KEY, isMetric ? (byte) Constants.SPORTS_UNITS_METRIC : (byte) Constants.SPORTS_UNITS_IMPERIAL);

        PebbleKit.sendDataToPebble(context, Constants.SPORTS_UUID, data);
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (isConnected()) {
            PebbleKit.closeAppOnPebble(context, Constants.SPORTS_UUID);
            if (sportsDataHandler != null) {
                context.unregisterReceiver(sportsDataHandler);
                sportsDataHandler = null;
            }
        }
        return ResultCode.RESULT_OK;
    }

    @Override
    public void onValueChanged(ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {

    }

    @Override
    public void onStepChanged(Step oldStep, Step newStep) {

    }

    public void customizeWatchApp() {
        try {
            final String customAppName = "RunnerUp";
            final Bitmap customIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_icon_runnerup30x30);

            PebbleKit.customizeWatchApp(
                    context, Constants.PebbleAppType.SPORTS, customAppName, customIcon);
        } catch (Exception ex) {
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show();
        }
    }
}
