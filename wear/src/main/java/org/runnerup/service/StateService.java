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
package org.runnerup.service;


import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.view.MainActivity;
import org.runnerup.wear.WearableClient;

import java.util.Objects;

public class StateService extends Service
    implements MessageApi.MessageListener, DataApi.DataListener, ValueModel.ChangeListener<Bundle> {

  public static final String UPDATE_TIME = "UPDATE_TIME";

  private final IBinder mBinder = new LocalBinder();
  private GoogleApiClient mGoogleApiClient;
  private WearableClient mDataClient;
  private String phoneNode;

  private Bundle data;
  private final ValueModel<TrackerState> trackerState = new ValueModel<>();
  private final ValueModel<Bundle> headers = new ValueModel<>();
  private MainActivity headersListener;

  @Override
  public void onCreate() {
    super.onCreate();

    mDataClient = new WearableClient(getApplicationContext());
    mGoogleApiClient =
        new GoogleApiClient.Builder(getApplicationContext())
            .addConnectionCallbacks(
                new GoogleApiClient.ConnectionCallbacks() {
                  @Override
                  public void onConnected(Bundle connectionHint) {

                    /* set "our" data */
                    setData();

                    Wearable.MessageApi.addListener(mGoogleApiClient, StateService.this);
                    Wearable.DataApi.addListener(mGoogleApiClient, StateService.this);

                    /* read already existing data */
                    readData();
                  }

                  @Override
                  public void onConnectionSuspended(int cause) {}
                })
            .addOnConnectionFailedListener(
                new GoogleApiClient.OnConnectionFailedListener() {
                  @Override
                  public void onConnectionFailed(@NonNull ConnectionResult result) {}
                })
            .addApi(Wearable.API)
            .build();
    mGoogleApiClient.connect();
    this.headers.registerChangeListener(this);

    Log.d(getClass().getName(), "StateService.onCreate()");
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean checkConnection() {
    return mGoogleApiClient != null && mGoogleApiClient.isConnected() && phoneNode != null;
  }

  private void readData() {
    mDataClient.readData(Constants.Wear.Path.PHONE_NODE_ID, dataItem -> {
        if (dataItem != null ) {
          phoneNode = dataItem.getUri().getHost();
          Log.d(getClass().getName(), "getDataItem => phoneNode:" + phoneNode);
        }
      });
    mDataClient.readData(Constants.Wear.Path.TRACKER_STATE, dataItem -> {
        if (dataItem != null) {
          TrackerState newState = getTrackerStateFromDataItem(dataItem);
          if (newState != null) {
            setTrackerState(newState);
          }
        }
      });
    mDataClient.readData(Constants.Wear.Path.HEADERS, dataItem -> {
        if (dataItem != null) {
          Bundle b = DataMapItem.fromDataItem(dataItem).getDataMap().toBundle();
          b.putLong(UPDATE_TIME, System.currentTimeMillis());
          headers.set(b);
        }
      });
  }

  private void setData() {
    /* set our node id */
    mDataClient.putData(Constants.Wear.Path.WEAR_NODE_ID);
  }

  private void clearData() {
    /* delete our node id */
    mDataClient.deleteData(Constants.Wear.Path.WEAR_NODE_ID);
  }

  @Override
  public void onDestroy() {
    Log.d(getClass().getName(), "StateService.onDestroy()");
    trackerState.clearListeners();
    if (mGoogleApiClient != null) {
      if (mGoogleApiClient.isConnected()) {
        phoneNode = null;

        clearData();
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
      }
      mGoogleApiClient.disconnect();
      mGoogleApiClient = null;
    }

    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public void onValueChanged(ValueModel<Bundle> instance, Bundle oldValue, Bundle newValue) {
    if (headersListener != null) headersListener.onValueChanged(newValue);
  }

  public class LocalBinder extends android.os.Binder {
    public StateService getService() {
      return StateService.this;
    }
  }

  private Bundle getBundle(Bundle src, long lastUpdateTime) {
    if (src == null) return null;

    long updateTime = src.getLong(UPDATE_TIME, 0);
    if (lastUpdateTime >= updateTime) return null;

    Bundle b = new Bundle();
    b.putAll(src);
    return b;
  }

  public Bundle getHeaders(long lastUpdateTime) {
    return getBundle(headers.get(), lastUpdateTime);
  }

  public Bundle getData(long lastUpdateTime) {
    return getBundle(data, lastUpdateTime);
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    if (Constants.Wear.Path.MSG_WORKOUT_EVENT.contentEquals(messageEvent.getPath())) {
      data = DataMap.fromByteArray(messageEvent.getData()).toBundle();
      data.putLong(UPDATE_TIME, System.currentTimeMillis());
    } else {
      Log.d(getClass().getName(), "onMessageReceived: " + messageEvent);
    }
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      Log.d(getClass().getName(), "onDataChanged: " + ev.getDataItem().getUri());
      String path = ev.getDataItem().getUri().getPath();
      if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
        setPhoneNode(ev);
      } else if (Constants.Wear.Path.HEADERS.contentEquals(path)) {
        setHeaders(ev);
      } else if (Constants.Wear.Path.TRACKER_STATE.contentEquals(path)) {
        setTrackerState(ev);
      }
    }
  }

  private void setPhoneNode(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED && Objects.requireNonNull(ev.getDataItem().getData()).length > 0) {
      phoneNode = new String(ev.getDataItem().getData());
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      phoneNode = null;
      resetState();
    }
  }

  private void setHeaders(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      Bundle b = DataMapItem.fromDataItem(ev.getDataItem()).getDataMap().toBundle();
      b.putLong(UPDATE_TIME, System.currentTimeMillis());
      Log.d(getClass().getName(), "setHeaders(): b=" + b);
      headers.set(b);
    } else {
      headers.set(null);
      resetState();
    }
  }

  public static TrackerState getTrackerStateFromDataItem(DataItem dataItem) {
    if (!dataItem.isDataValid()) return null;

    Bundle b = DataMap.fromByteArray(dataItem.getData()).toBundle();
    if (b.containsKey(Constants.Wear.TrackerState.STATE)) {
      return TrackerState.valueOf(b.getInt(Constants.Wear.TrackerState.STATE));
    }
    return null;
  }

  private void setTrackerState(DataEvent ev) {
    TrackerState newVal = null;
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      newVal = getTrackerStateFromDataItem(ev.getDataItem());
      if (newVal == null) {
        // This is weird. TrackerState is set to a invalid value...skip out
        return;
      }
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      // trackerState being deleted
      newVal = null;
      resetState();
    }
    setTrackerState(newVal);
  }

  private void resetState() {
    data = null;
    headers.set(null);
  }

  private void setTrackerState(TrackerState newVal) {
    trackerState.set(newVal);
  }

  public TrackerState getTrackerState() {
    return trackerState.get();
  }

  public void registerTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    trackerState.registerChangeListener(listener);
  }

  public void unregisterTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
    trackerState.unregisterChangeListener(listener);
  }

  public void registerHeadersListener(MainActivity listener) {
    this.headersListener = listener;
  }

  public void unregisterHeadersListener(MainActivity listener) {
    this.headersListener = null;
  }

  public void sendStart() {
    if (!checkConnection()) return;

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_START, null);
  }

  public void sendPauseResume() {
    if (!checkConnection()) return;

    if (getTrackerState() == TrackerState.STARTED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_PAUSE, null);
    } else if (getTrackerState() == TrackerState.PAUSED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_RESUME, null);
    }
  }

  public void sendNewLap() {
    if (!checkConnection()) return;

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_NEW_LAP, null);
  }
}
