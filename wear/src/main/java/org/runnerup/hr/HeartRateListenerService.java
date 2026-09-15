/*
 * Copyright (C) 2014 robert.jonsson75@gmail.com
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

package org.runnerup.hr;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import org.runnerup.common.util.Constants;

/**
 * A {@link WearableListenerService} that listens for messages from a connected phone to control
 * heart rate monitoring on the wearable device.
 *
 * <p>It receives messages via the Wearable Data Layer. Specifically, it handles:
 *
 * <ul>
 *   <li>{@link Constants.Wear.Path#MSG_CMD_HR_START}: Starts the {@link HeartRateService}, passing
 *       the source node ID of the phone as an extra.
 *   <li>{@link Constants.Wear.Path#MSG_CMD_HR_STOP}: Stops the {@link HeartRateService}.
 * </ul>
 */
public class HeartRateListenerService extends WearableListenerService {
  private static final String TAG = "HeartRateListenerService";

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy");
  }

  @Override
  public void onMessageReceived(@NonNull MessageEvent messageEvent) {
    String path = messageEvent.getPath();
    String sourceNodeId = messageEvent.getSourceNodeId(); // ID of the phone that sent the message
    Log.d(TAG, "onMessageReceived: " + path + " from " + sourceNodeId);

    Intent serviceIntent = new Intent(this, HeartRateService.class);

    if (Constants.Wear.Path.MSG_CMD_HR_START.equals(path)) {
      serviceIntent.putExtra(Constants.Intents.EXTRA_SOURCE_NODE_ID, sourceNodeId);
      startService(serviceIntent);
    } else if (Constants.Wear.Path.MSG_CMD_HR_STOP.equals(path)) {
      stopService(serviceIntent);
    }
  }
}
