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
import android.app.Activity;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public interface HRProvider {

    public interface HRClient {
        public void onOpenResult(boolean ok);

        public void onScanResult(HRDeviceRef device);

        public void onConnectResult(boolean connectOK);

        public void onDisconnectResult(boolean disconnectOK);

        public void onCloseResult(boolean closeOK);

        public void log(HRProvider src, String msg);
    }

    public abstract String getName(); // For display

    public abstract String getProviderName(); // For internal usage

    public abstract boolean isEnabled();

    public abstract boolean startEnableIntent(Activity activity, int requestCode);

    public abstract void open(Handler handler, HRClient hrClient);

    public abstract void close();

    public abstract boolean isBondingDevice();

    public abstract boolean isScanning();

    public abstract boolean isConnected();

    public abstract boolean isConnecting();

    public abstract void startScan();

    public abstract void stopScan();

    public abstract void connect(HRDeviceRef ref);

    public abstract void disconnect();

    public abstract int getHRValue();

    public abstract long getHRValueTimestamp();

    public abstract int getBatteryLevel();
}
