/*
 * Copyright (C) 2014 weides@gmail.com
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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TrackerWear extends DefaultTrackerComponent
        implements Constants, TrackerComponent, WorkoutObserver, NodeApi.NodeListener,
        MessageApi.MessageListener, DataApi.DataListener, WorkoutStepListener, ValueModel.ChangeListener<TrackerState> {

    public static final String NAME = "WEAR";
    private Tracker tracker;
    private Context context;
    private GoogleApiClient mGoogleApiClient;
    private Formatter formatter;
    private HashSet<Node> connectedNodes = new HashSet<Node>();
    private String wearNode;

    private final Handler handler = new Handler();
    private Bundle lastCreatedWorkoutEvent;
    private Bundle lastSentWorkoutEvent;
    private long tickFrequency = 1000;
    private long tickFrequencyPause = 500; // so that seconds does show "slowly"
    private boolean mWorkoutSenderRunning = false;

    private List<Pair<Scope, Dimension>> items = new ArrayList<Pair<Scope, Dimension>>(3);
    private Step currentStep;
    private boolean pauseStep;

    public TrackerWear(Tracker tracker) {
        this.tracker = tracker;
        items.add(new Pair<Scope, Dimension>(Scope.ACTIVITY, Dimension.TIME));
        items.add(new Pair<Scope, Dimension>(Scope.ACTIVITY, Dimension.DISTANCE));
        items.add(new Pair<Scope, Dimension>(Scope.LAP, Dimension.PACE));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TrackerComponent.ResultCode onInit(final Callback callback, Context context) {
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) !=
                ConnectionResult.SUCCESS) {
            return ResultCode.RESULT_NOT_SUPPORTED;
        }

        try {
            context.getPackageManager().getPackageInfo("com.google.android.wearable.app",
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // android wear app is not installed => can't be paired
            return ResultCode.RESULT_NOT_SUPPORTED;
        }

        tracker.registerTrackerStateListener(this);
        this.context = context;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        callback.run(TrackerWear.this, ResultCode.RESULT_OK);

                        /* set "our" data */
                        setData();

                        Wearable.MessageApi.addListener(mGoogleApiClient, TrackerWear.this);
                        Wearable.NodeApi.addListener(mGoogleApiClient, TrackerWear.this);
                        Wearable.DataApi.addListener(mGoogleApiClient, TrackerWear.this);

                        /* read already existing data */
                        readData();

                        /** get info about connected nodes in background */
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).

                                setResultCallback(
                                        new ResultCallback<NodeApi.GetConnectedNodesResult>() {

                                            @Override
                                            public void onResult (NodeApi.GetConnectedNodesResult nodes){
                                                for (Node node : nodes.getNodes()) {
                                                    onPeerConnected(node);
                                                }
                                            }
                                        }

                                );
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        callback.run(TrackerWear.this, ResultCode.RESULT_ERROR);
                    }
                })
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
        return ResultCode.RESULT_PENDING;
    }

    private void readData() {
        Wearable.DataApi.getDataItems(mGoogleApiClient, new Uri.Builder()
                .scheme(WEAR_URI_SCHEME).path(Wear.Path.WEAR_NODE_ID).build())
                .setResultCallback(
                        new ResultCallback<DataItemBuffer>() {
                            @Override
                            public void onResult(DataItemBuffer dataItems) {
                                for (DataItem dataItem : dataItems) {
                                    wearNode = dataItem.getUri().getHost();
                                    System.err.println("getDataItem => wearNode:" + wearNode);
                                }
                                dataItems.release();
                            }
                        });
    }

    private void setData() {
        Wearable.DataApi.putDataItem(mGoogleApiClient,
                PutDataRequest.create(Constants.Wear.Path.PHONE_NODE_ID));
    }

    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
    }

    @Override
    public void onStart() {
        {
            Bundle b = new Bundle();
            int i = 0;
            for (Pair<Scope, Dimension> item : items) {
                b.putString(Wear.RunInfo.HEADER + i, context.getString(item.second.getTextId()));
                i++;
            }
            setData(Wear.Path.HEADERS, b);
        }

        setTrackerState(tracker.getState());
        tracker.getWorkout().registerWorkoutStepListener(this);
    }

    private void setTrackerState(TrackerState val) {
        System.err.println("setTrackerState("+val+")");
        Bundle b = new Bundle();
        b.putInt(Wear.TrackerState.STATE, val.getValue());
        setData(Wear.Path.TRACKER_STATE, b);
    }

    private void setData(String path, Bundle b) {
        Wearable.DataApi.putDataItem(mGoogleApiClient,
                PutDataRequest.create(path).setData(DataMap.fromBundle(b).toByteArray()))
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            System.err.println("TrackerWear: ERROR: failed to putDataItem, " +
                                    "status code: " + dataItemResult.getStatus().getStatusCode());
                        }
                    }
                });
    }

    @Override
    public void workoutEvent(WorkoutInfo workoutInfo, int type) {
        switch (type) {
            case DB.LOCATION.TYPE_START:
            case DB.LOCATION.TYPE_RESUME:
                setTrackerState(TrackerState.STARTED);
                break;
            case DB.LOCATION.TYPE_PAUSE:
                setTrackerState(TrackerState.PAUSED);
            case DB.LOCATION.TYPE_END:
                break;
        }

        Bundle b = new Bundle();
        {
            int i = 0;
            for (Pair<Scope, Dimension> item : items) {
                b.putString(Wear.RunInfo.DATA + i, formatter.format(Formatter.TXT_SHORT,
                        item.second, workoutInfo.get(item.first, item.second)));
                i++;
            }
        }

        lastCreatedWorkoutEvent = b;
    }

    private void sendWorkoutEvent() {
        if (!isConnected())
            return;

        /* special handling of pauseStep */
        if (pauseStep) {
            if (lastCreatedWorkoutEvent == null)
                lastCreatedWorkoutEvent = lastSentWorkoutEvent;

            if (lastCreatedWorkoutEvent == null) {
                lastCreatedWorkoutEvent = new Bundle();
            }

            Dimension dim = currentStep.getDurationType();
            if (dim != null) {
                double remaining = tracker.getWorkout().getRemaining(Scope.STEP, dim);
                if (remaining < 0) {
                    remaining = 0;
                }
                lastCreatedWorkoutEvent.putString(Wear.RunInfo.COUNTDOWN,
                        formatter.formatRemaining(Formatter.TXT_SHORT, dim, remaining));
            }
        }

        if (lastCreatedWorkoutEvent != null) {
            Wearable.MessageApi.sendMessage(mGoogleApiClient, wearNode, Wear.Path.MSG_WORKOUT_EVENT,
                    DataMap.fromBundle(lastCreatedWorkoutEvent).toByteArray());
            lastSentWorkoutEvent = lastCreatedWorkoutEvent;
            lastCreatedWorkoutEvent = null;
        }
    }

    private Runnable workoutEventSender = new Runnable() {
        @Override
        public void run() {
            sendWorkoutEvent();
            mWorkoutSenderRunning = false;

            if (!isConnected())
                return;

            if (currentStep == null)
                return;

            mWorkoutSenderRunning = true;
            handler.postDelayed(workoutEventSender,
                    pauseStep ? tickFrequencyPause : tickFrequency);
        }
    };

    @Override
    public void onStepChanged(Step oldStep, Step newStep) {
        currentStep = newStep;

        if (!mWorkoutSenderRunning) {
            // this starts workout sender
            workoutEventSender.run();
        }

        if (currentStep == null) {
            return; // this is end
        }

        // Update headers
        {
            Bundle b = new Bundle();
            int i = 0;
            for (Pair<Scope, Dimension> item : items) {
                b.putString(Wear.RunInfo.HEADER + i, context.getString(item.second.getTextId()));
                i++;
            }

            pauseStep = false;
            if (currentStep != null && currentStep.isPauseStep()) {
                pauseStep = true;
                b.putBoolean(Wear.RunInfo.PAUSE_STEP, true);
            }

            setData(Wear.Path.HEADERS, b);
        }
    }

    @Override
    public void onComplete(boolean discarded) {
        tracker.getWorkout().unregisterWorkoutStepListener(this);
        currentStep = null;

        clearData(/* don't clear own node id */ false);
    }

    @Override
    public boolean isConnected() {
        if (mGoogleApiClient == null)
            return false;

        if (!mGoogleApiClient.isConnected())
            return false;

        return wearNode != null;
    }

    @Override
    public void onPeerConnected(Node node) {
        connectedNodes.add(node);
    }

    @Override
    public void onPeerDisconnected(Node node) {
        connectedNodes.remove(node);
        if (wearNode != null && node.getId().contentEquals(wearNode))
            wearNode = null;
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        System.err.println("onMessageReceived: " + messageEvent);
        //note: skip state checking, do that in receiver instead
        if (Wear.Path.MSG_CMD_WORKOUT_PAUSE.contentEquals(messageEvent.getPath())) {
            sendLocalBroadcast(Intents.PAUSE_WORKOUT);
            return;
        } else if (Wear.Path.MSG_CMD_WORKOUT_RESUME.contentEquals(messageEvent.getPath())) {
            sendLocalBroadcast(Intents.RESUME_WORKOUT);
            return;
        } else if (Wear.Path.MSG_CMD_WORKOUT_NEW_LAP.contentEquals(messageEvent.getPath())) {
            sendLocalBroadcast(Intents.NEW_LAP);
            return;
        } else if (Wear.Path.MSG_CMD_WORKOUT_START.contentEquals(messageEvent.getPath())) {
            /* send broadcast to StartActivity */
            Intent startBroadcastIntent = new Intent();
            startBroadcastIntent.setAction(Intents.START_WORKOUT);
            context.sendBroadcast(startBroadcastIntent);
            return;
        }
    }

    private void sendLocalBroadcast(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected()) {
                clearData(true);
                wearNode = null;

                Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                Wearable.NodeApi.removeListener(mGoogleApiClient, this);
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                connectedNodes.clear();
            }
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
        tracker.unregisterTrackerStateListener(this);
        return ResultCode.RESULT_OK;
    }

    private void clearData(boolean self) {
        if (self) {
        /* clear our node id */
            Wearable.DataApi.deleteDataItems(mGoogleApiClient,
                    new Uri.Builder().scheme(WEAR_URI_SCHEME).path(
                            Wear.Path.PHONE_NODE_ID).build());
        }

        /* clear HEADERS */
        Wearable.DataApi.deleteDataItems(mGoogleApiClient,
                new Uri.Builder().scheme(WEAR_URI_SCHEME).path(
                        Wear.Path.HEADERS).build());

        /* clear WORKOUT PLAN */
        Wearable.DataApi.deleteDataItems(mGoogleApiClient,
                new Uri.Builder().scheme(WEAR_URI_SCHEME).path(
                        Wear.Path.WORKOUT_PLAN).build());

        /* clear TRACKER_STATE */
        Wearable.DataApi.deleteDataItems(mGoogleApiClient,
                new Uri.Builder().scheme(WEAR_URI_SCHEME).path(
                        Wear.Path.TRACKER_STATE).build());
    }

    @Override
    public void onDataChanged(final DataEventBuffer dataEvents) {
        for (DataEvent ev : dataEvents) {
            System.err.println("onDataChanged: " + ev.getDataItem().getUri());
            String path = ev.getDataItem().getUri().getPath();
            if (Constants.Wear.Path.WEAR_NODE_ID.contentEquals(path)) {
                setWearNode(ev);
            }
        }
    }

    private void setWearNode(DataEvent ev) {
        if (ev.getType() == DataEvent.TYPE_CHANGED) {
            wearNode = ev.getDataItem().getUri().getHost();
            if (lastCreatedWorkoutEvent == null) {
                lastCreatedWorkoutEvent = lastSentWorkoutEvent;
            }
            if (!mWorkoutSenderRunning)
                workoutEventSender.run();
            else
                sendWorkoutEvent();
        } else if (ev.getType() == DataEvent.TYPE_DELETED) {
            wearNode = null;
        }
    }

    @Override
    public void onValueChanged(ValueModel<TrackerState> instance,
                               TrackerState oldValue, TrackerState newValue) {
        setTrackerState(newValue);
    }
}
