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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import org.runnerup.common.util.Constants;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TrackerWear extends DefaultTrackerComponent
        implements Constants, TrackerComponent, WorkoutObserver, NodeApi.NodeListener {

    private Context context;
    private GoogleApiClient googleApiClient;
    private Formatter formatter;

    public static final String NAME = "WEAR";
    private HashSet<Node> connectedNodes;

    private List<Pair<Scope, Dimension>> items = new ArrayList<Pair<Scope, Dimension>>(3);

    public TrackerWear() {
        items.add(new Pair<Scope, Dimension>(Scope.WORKOUT, Dimension.TIME));
        items.add(new Pair<Scope, Dimension>(Scope.WORKOUT, Dimension.DISTANCE));
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

        this.context = context;
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        callback.run(TrackerWear.this, ResultCode.RESULT_OK);

                        /** get info about connected nodes in background */
                        connectedNodes = new HashSet<Node>();
                        Wearable.NodeApi.addListener(googleApiClient, TrackerWear.this);
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(
                                new ResultCallback<NodeApi.GetConnectedNodesResult>() {

                                    @Override
                                    public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                                        connectedNodes.addAll(nodes.getNodes());
                                    }
                                });
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
        googleApiClient.connect();
        return ResultCode.RESULT_PENDING;
    }

    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
    }

    @Override
    public void onStart() {
    }

    @Override
    public void workoutEvent(WorkoutInfo workoutInfo, int type) {

        if (googleApiClient == null)
            return;

        if (!googleApiClient.isConnected()) {
            return;
        }

        Bundle b = new Bundle();
        {
            int i = 1;
            for (Pair<Scope, Dimension> item : items) {
                b.putString(Wear.RunInfo.HEADER + i, context.getString(item.second.getTextId()));
                b.putString(Wear.RunInfo.DATA + i, formatter.format(Formatter.TXT_SHORT,
                        item.second, workoutInfo.get(item.first, item.second)));
                i++;
            }
        }

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(Wear.Path.EVENT);
        dataMapRequest.getDataMap().putAll(DataMap.fromBundle(b));
        Wearable.DataApi.putDataItem(googleApiClient, dataMapRequest.asPutDataRequest())
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
    public void onComplete(boolean discarded) {
    }

    @Override
    public boolean isConnected() {
        if (googleApiClient == null)
            return false;

        if (!googleApiClient.isConnected())
            return false;

        if (connectedNodes == null)
            return false;

        return !connectedNodes.isEmpty();
    }

    @Override
    public void onPeerConnected(Node node) {
        connectedNodes.add(node);
    }

    @Override
    public void onPeerDisconnected(Node node) {
        connectedNodes.remove(node);
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (googleApiClient != null) {
            if (connectedNodes != null) {
                Wearable.NodeApi.removeListener(googleApiClient, this);
                connectedNodes = null;
            }
            googleApiClient.disconnect();
            googleApiClient = null;
        }
        return ResultCode.RESULT_OK;
    }
}
