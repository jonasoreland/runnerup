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

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.wearable.WearableListenerService;

@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class ListenerService extends WearableListenerService {

    @Override
    public void onCreate() {
        super.onCreate();
        System.err.println("ListenerService.onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.err.println("ListenerService.onDestroy()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.err.println("ListenerService.onStart()");
        return super.onStartCommand(intent, flags, startId);
    }
}
