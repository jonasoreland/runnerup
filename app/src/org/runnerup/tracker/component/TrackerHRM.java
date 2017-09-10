/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;


public class TrackerHRM extends DefaultTrackerComponent {

    private final Handler handler = new Handler();
    private HRProvider hrProvider;

    public static final String NAME = "HRM";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, final Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        final String btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider),
                null);
        final String btDeviceName = prefs.getString(res.getString(R.string.pref_bt_name), null);

        if (btAddress == null || btProviderName == null) {
            /* no HRM is configured, return directly */
            return ResultCode.RESULT_NOT_SUPPORTED;
        }

        hrProvider = HRManager.getHRProvider(context, btProviderName);
        if (hrProvider != null) {
            hrProvider.open(handler, new HRProvider.HRClient() {
                @Override
                public void onOpenResult(boolean ok) {
                    if (!hrProvider.isEnabled()) {
                        /* no functional HRM */
                        callback.run(TrackerHRM.this, ResultCode.RESULT_NOT_ENABLED);
                        return;
                    }

                    if (!ok) {
                        /* no functional HRM */
                        callback.run(TrackerHRM.this, ResultCode.RESULT_ERROR);
                        return;
                    }

                    /* return RESULT_OK and connect in background */
                    // TODO: make it possible to make HRM mandatory i.e don't connect in background
                    callback.run(TrackerHRM.this, ResultCode.RESULT_OK);

                    hrProvider.connect(HRDeviceRef.create(btProviderName, btDeviceName, btAddress));
                }

                @Override
                public void onScanResult(HRDeviceRef device) {
                }

                @Override
                public void onConnectResult(boolean connectOK) {
                    if (connectOK) {
                        Toast.makeText(context, "Connected to HRM " + btDeviceName,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Failed to connect to HRM " + btDeviceName,
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onDisconnectResult(boolean disconnectOK) {
                }

                @Override
                public void onCloseResult(boolean closeOK) {
                }

                @Override
                public void log(HRProvider src, String msg) {
                }
            });
        }
        return ResultCode.RESULT_PENDING;
    }

    @Override
    public boolean isConnected() {
        if (hrProvider == null)
            return false;
        return hrProvider.isConnected();
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (hrProvider != null) {
            hrProvider.disconnect();
            hrProvider.close();
            hrProvider = null;
        }
        return ResultCode.RESULT_OK;
    }

    public HRProvider getHrProvider() {
        return hrProvider;
    }
}
