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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

public class StateService extends Service {

    public static final String ACTION = "ACTION";
    public static final String UPDATE_TIME = "UPDATE_TIME";

    private final IBinder mBinder = new LocalBinder();
    private long updateTime;
    private Bundle data;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                mBroadcastReceiver,
                new IntentFilter(ACTION));
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                mBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            data = intent.getExtras();
            updateTime = System.currentTimeMillis();
        }
    };

    public Bundle getData(long lastUpdateTime) {
        if (lastUpdateTime >= updateTime)
            return null;
        if (data == null)
            return null;
        Bundle b = new Bundle();
        b.putAll(data);
        b.putLong(UPDATE_TIME, updateTime);
        return b;
    }

    public class LocalBinder extends android.os.Binder {
        public StateService getService() {
            return StateService.this;
        }
    }
}
