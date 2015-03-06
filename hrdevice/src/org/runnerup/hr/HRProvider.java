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

/**
 * {@link HRProvider}'s provide an interface to a wireless connectivity module (Bluetooth, ANT+ etc)
 * and any heart rate devices than can be connected through them
 *
 * Instances of {@link HRProvider's} can be created through the {@link HRManager} class
 *
 * @author jonas
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public interface HRProvider {

    /**
     * An interface through which the client of the {@link HRProvider}
     * is notified of changes to the state of the {@link HRProvider}
     */
    public interface HRClient {
        public void onOpenResult(boolean ok);

        public void onScanResult(HRDeviceRef device);

        public void onConnectResult(boolean connectOK);

        public void onDisconnectResult(boolean disconnectOK);

        public void onCloseResult(boolean closeOK);

        public void log(HRProvider src, String msg);
    }

    /**
     * @return A human readable name for the {@link HRProvider}
     */
    public abstract String getName();

    /**
     * @return An internal name for a specific {@link HRProvider} instantiation
     */
    public abstract String getProviderName(); // For internal usage


    /**
     * @return true if the wireless module is enabled,
     *          false otherwise
     */
    public abstract boolean isEnabled();

    /**
     * Presents the user if the settings screen to enable the provider's protocol. When this is done,
     * 'activity' will have {@link Activity#onActivityResult(int, int, android.content.Intent)} called
     *
     * @param activity The {@link Activity} currently being displayed to the user
     * @param requestCode An arbitrary code that will be given to
     *                  {@link Activity#onActivityResult(int, int, android.content.Intent)}
     * @return true if the intent was sent
     */
    public abstract boolean startEnableIntent(Activity activity, int requestCode);

    /**
     * Initialises the wireless module, allowing device scanning/connection
     *
     * @param handler The Handler in which to run the hrClient
     * @param hrClient The object that will be notified when operations have finished
     */
    public abstract void open(Handler handler, HRClient hrClient);

    /**
     * Closes the wireless module
     */
    public abstract void close();

    /**
     * A bonding device is a wireless module that requires devices to be
     * paired before showing up on the scan (e.g. Bluetooth)
     *
     * @return true if the wireless module is a bonding device,
     *          false otherwise
     */
    public abstract boolean isBondingDevice();

    /**
     * @return true if this {@link HRProvider} is currently scanning for available devices,
     *          false otherwise
     */
    public abstract boolean isScanning();

    /**
     * @return true if this {@link HRProvider} is connected to a heart rate device,
     *          false otherwise
     */
    public abstract boolean isConnected();

    /**
     * @return true if this {@link HRProvider} is currently connecting to a heart rate device,
     *          false otherwise
     */
    public abstract boolean isConnecting();

    /**
     * Starts scanning for available heart rate devices. Results will be passed to the HRClient
     * supplied in {@link #open(android.os.Handler, org.runnerup.hr.HRProvider.HRClient)}
     */
    public abstract void startScan();

    /**
     * Stops scanning for available heart rate devices. When done, the {@link HRClient} passed
     * in {@link #open(android.os.Handler, org.runnerup.hr.HRProvider.HRClient)} will be notified
     */
    public abstract void stopScan();

    /**
     * Connects to a heart rate monitor device
     * @param ref An object representing a heart rate device. Client code can get
     *            available device information through startScan()
     */
    public abstract void connect(HRDeviceRef ref);

    /**
     * Disconnects from a heart rate monitor device
     */
    public abstract void disconnect();

    /**
     * @return the most recent heart rate value supplied by the connected device.
     *          0 indicates that no device has been connected (or the user is in a very bad way)
     */
    public abstract int getHRValue();

    /**
     * @return the unix time of the last received heart rate value
     */
    public abstract long getHRValueTimestamp();

    /**
     * @return the most recent heart rate data supplied by the device. If no device has
     *          been connected, this will be null
     */
    public abstract HRData getHRData();

    /**
     * @return The battery level, in percents, of the heart rate monitor device or 0 if
     *          no device has been connected or the device doesn't supply battery information
     */
    public abstract int getBatteryLevel();
}
