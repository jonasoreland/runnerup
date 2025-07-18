/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
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

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class WearHRProvider implements HRProvider {
    private static final String TAG = "WearHRProvider";
    public static final String NAME = "Wear OS";

    private final Context context;
    private HRClient hrClient;
    private Handler hrClientHandler;
    private boolean isScanning;

    public WearHRProvider(Context context) {
        Log.d(TAG, "WearHRProvider: context=" + context);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getProviderName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean startEnableIntent(AppCompatActivity activity, int requestCode) {
        return false;  // Assume watch is already paired/connected to phone
    }

    @Override
    public void open(Handler handler, HRClient hrClient) {
        Log.d(TAG, "open: handler=" + handler + ", hrClient=" + hrClient);

        this.hrClient = hrClient;
        this.hrClientHandler = handler;

        hrClient.onOpenResult(true);
    }

    @Override
    public void close(String from) {}

    @Override
    public boolean includePairingBLE() {
        return false;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void startScan() {
        Log.d(TAG, "startScan");

        if (isScanning) {
            Log.d(TAG, "startScan: Scan already in progress.");
            return;
        }

        isScanning = true;
    }

    @Override
    public void stopScan() {
        Log.d(TAG, "stopScan");
        isScanning = false;
    }

    @Override
    public void connect(HRDeviceRef ref) {

    }

    @Override
    public void disconnect() {

    }

    @Override
    public int getHRValue() {
        return 0;
    }

    @Override
    public long getHRValueTimestamp() {
        return 0;
    }

    @Override
    public long getHRValueElapsedRealtime() {
        return 0;
    }

    @Override
    public HRData getHRData() {
        return null;
    }

    @Override
    public int getBatteryLevel() {
        return HRProvider.BATTERY_LEVEL_UNAVAILABLE;
    }
}
