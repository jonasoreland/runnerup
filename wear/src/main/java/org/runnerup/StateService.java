package org.runnerup;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by niklas.weidemann on 2014-10-17.
 */
public class StateService extends IntentService {
    private Bundle currentInfo = null;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public StateService(String name) {
        super(name);
    }

    public StateService() {
        super("StateService");
    }


    public class LocalBinder extends android.os.Binder {
        public StateService getService() {
            return StateService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        currentInfo = intent.getExtras();
        Log.d("StateService", "onHandleIntent:" + currentInfo.getString("Activity Time"));
    }

    public Bundle getCurrentInfo(){
        return currentInfo;
    }
}
