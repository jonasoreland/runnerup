package org.runnerup.wear;

/**
 * Created by niklas.weidemann on 2014-10-12.
 */

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearMessageService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Intent startBroadcastIntent = new Intent();
        startBroadcastIntent.setAction("org.runnerup.START_STOP");
        getApplicationContext().sendBroadcast(startBroadcastIntent);
    }
}
