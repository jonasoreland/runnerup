/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

/**
 * Created by jonas on 11/1/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public abstract class BtHRBase implements HRProvider {
    static final UUID HRP_SERVICE = UUID
            .fromString("0000180D-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_SERVICE = UUID
            .fromString("0000180f-0000-1000-8000-00805f9b34fb");
    static final UUID FIRMWARE_REVISON_UUID = UUID
            .fromString("00002a26-0000-1000-8000-00805f9b34fb");
    static final UUID DIS_UUID = UUID
            .fromString("0000180a-0000-1000-8000-00805f9b34fb");
    static final UUID HEART_RATE_MEASUREMENT_CHARAC = UUID
            .fromString("00002A37-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_LEVEL_CHARAC = UUID
            .fromString("00002A19-0000-1000-8000-00805f9b34fb");
    static final UUID CCC = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    protected HRProvider.HRClient hrClient;
    protected Handler hrClientHandler;

    protected void log (final String msg) {
        if (hrClient != null) {
            if(Looper.myLooper() == Looper.getMainLooper()) {
                hrClient.log(this, msg);
            } else {
                hrClientHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (hrClient != null)
                            hrClient.log(BtHRBase.this, msg);
                    }
                });
            }
        }
        else
            System.err.println(msg);
    }
}
