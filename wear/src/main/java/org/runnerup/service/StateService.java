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
import java.util.Objects;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.view.MainActivity;
import org.runnerup.wear.WearableClient;

public class StateService extends Service
    implements MessageApi.MessageListener, DataApi.DataListener, ValueModel.ChangeListener<Bundle> {

  private static String TAG = "RU:StateService";
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
                    readData(/* force= */ true);
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

    Log.i(TAG, "StateService.onCreate()");
  }

  private String checkConnection() {
    if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && phoneNode != null) {
      return null;
    }
    return "mGoogleApiClient="
        + mGoogleApiClient
        + ", isConnected="
        + mGoogleApiClient.isConnected()
        + ", phoneNode="
        + phoneNode;
  }

  public void readDataIfMissing() {
    readData(/* force= */ false);
  }

  private void readData(boolean force) {
    if (force || phoneNode == null) {
      mDataClient.readData(
          Constants.Wear.Path.PHONE_NODE_ID,
          dataItem -> {
            if (dataItem != null) {
              phoneNode = dataItem.getUri().getHost();
              Log.i(TAG, "getDataItem => phoneNode:" + phoneNode);
            }
          });
    }
    if (force || trackerState.get() == null) {
      mDataClient.readData(
          Constants.Wear.Path.TRACKER_STATE,
          dataItem -> {
            if (dataItem != null) {
              TrackerState newState = getTrackerStateFromDataItem(dataItem);
              if (newState != null) {
                setTrackerState(newState);
              }
            }
          });
    }
    if (force || headers.get() == null) {
      mDataClient.readData(
          Constants.Wear.Path.HEADERS,
          dataItem -> {
            if (dataItem != null) {
              Bundle b = DataMapItem.fromDataItem(dataItem).getDataMap().toBundle();
              b.putLong(UPDATE_TIME, System.currentTimeMillis());
              headers.set(b);
            }
          });
    }
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
    Log.i(TAG, "StateService.onDestroy()");
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
    if (headersListener != null) {
      headersListener.onValueChanged(newValue);
    }
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
      Log.i(TAG, "onMessageReceived: " + messageEvent);
    }
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent ev : dataEvents) {
      String path = ev.getDataItem().getUri().getPath();
      if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
        setPhoneNode(ev);
      } else if (Constants.Wear.Path.HEADERS.contentEquals(path)) {
        setHeaders(ev);
      } else if (Constants.Wear.Path.TRACKER_STATE.contentEquals(path)) {
        setTrackerState(ev);
      } else {
        Log.i(TAG, "onDataChanged: " + ev.getDataItem().getUri());
        readDataIfMissing();
      }
    }
  }

  private void setPhoneNode(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED && Objects.requireNonNull(ev.getDataItem().getData()).length > 0) {
      phoneNode = new String(ev.getDataItem().getData());
      Log.i(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", phoneNode=" + phoneNode);
      readDataIfMissing();
    } else if (ev.getType() == DataEvent.TYPE_DELETED) {
      Log.i(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", phoneNode=null");
      phoneNode = null;
      resetState();
    }
  }

  private void setHeaders(DataEvent ev) {
    if (ev.getType() == DataEvent.TYPE_CHANGED) {
      Bundle b = DataMapItem.fromDataItem(ev.getDataItem()).getDataMap().toBundle();
      b.putLong(UPDATE_TIME, System.currentTimeMillis());
      Log.i(TAG, "setHeaders(): b=" + b);
      headers.set(b);
      readDataIfMissing();
    } else {
      Log.i(TAG, "onDataChanged: " + ev.getDataItem().getUri() + ", headers=null");
      headers.set(null);
      resetState();
    }
  }

  public static TrackerState getTrackerStateFromDataItem(DataItem dataItem) {
    if (!dataItem.isDataValid()) {
      Log.i(TAG, "onDataChanged: " + dataItem.getUri() + ", dataItem.isDataValid() == false");
      return null;
    }

    Bundle b = DataMap.fromByteArray(dataItem.getData()).toBundle();
    if (b.containsKey(Constants.Wear.TrackerState.STATE)) {
      var state = TrackerState.valueOf(b.getInt(Constants.Wear.TrackerState.STATE));
      Log.i(TAG, "onDataChanged: " + dataItem.getUri() + ", tracketState=" + state);
      return state;
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
    readDataIfMissing();
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
    var msg = checkConnection();
    if (msg != null) {
      Log.i(TAG, "sendStart => not connected: " + msg);
      return;
    }

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_START, null);
  }

  public void sendPauseResume() {
    var msg = checkConnection();
    if (msg != null) {
      Log.i(TAG, "sendPauseResume => not connected: " + msg);
      return;
    }

    if (getTrackerState() == TrackerState.STARTED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_PAUSE, null);
    } else if (getTrackerState() == TrackerState.PAUSED) {
      Wearable.MessageApi.sendMessage(
          mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_RESUME, null);
    }
  }

  public void sendNewLap() {
    var msg = checkConnection();
    if (msg != null) {
      Log.i(TAG, "sendNewLap => not connected: " + msg);
      return;
    }

    Wearable.MessageApi.sendMessage(
        mGoogleApiClient, phoneNode, Constants.Wear.Path.MSG_CMD_WORKOUT_NEW_LAP, null);
  }
}
