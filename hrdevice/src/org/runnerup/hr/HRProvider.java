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

import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

/**
 * {@link HRProvider}'s provide an interface to a wireless connectivity module (Bluetooth, ANT+ etc)
 * and any heart rate devices than can be connected through them
 *
 * Instances of {@link HRProvider's} can be created through the {@link HRManager} class
 *
 * @author jonas
 */

public interface HRProvider {

    /**
     * An interface through which the client of the {@link HRProvider}
     * is notified of changes to the state of the {@link HRProvider}
     */
    interface HRClient {
        void onOpenResult(boolean ok);

        void onScanResult(HRDeviceRef device);

        void onConnectResult(boolean connectOK);

        void onDisconnectResult(boolean disconnectOK);

        void onCloseResult(boolean closeOK);

        void log(HRProvider src, String msg);
    }

    /**
     * @return A human readable name for the {@link HRProvider}
     */
    String getName();

    /**
     * @return An internal name for a specific {@link HRProvider} instantiation
     */
    String getProviderName(); // For internal usage


    /**
     * @return true if the wireless module is enabled,
     *          false otherwise
     */
    boolean isEnabled();

    /**
     * Presents the user if the settings screen to enable the provider's protocol. When this is done,
     * 'activity' will have  called
     *
     * @param activity The {@link AppCompatActivity} currently being displayed to the user
     * @param requestCode An arbitrary code that will be given to
     *
     * @return true if the intent was sent
     */
    boolean startEnableIntent(AppCompatActivity activity, int requestCode);

    /**
     * Initialises the wireless module, allowing device scanning/connection
     *
     * @param handler The Handler in which to run the hrClient
     * @param hrClient The object that will be notified when operations have finished
     */
    void open(Handler handler, HRClient hrClient);

    /**
     * Closes the wireless module
     */
    void close();

    /**
     * A bonding device is a wireless module that requires devices to be
     * paired before showing up on the scan (e.g. Bluetooth)
     *
     * @return true if the wireless module is a bonding device,
     *          false otherwise
     */
    boolean isBondingDevice();

    /**
     * @return true if this {@link HRProvider} is currently scanning for available devices,
     *          false otherwise
     */
    boolean isScanning();

    /**
     * @return true if this {@link HRProvider} is connected to a heart rate device,
     *          false otherwise
     */
    boolean isConnected();

    /**
     * @return true if this {@link HRProvider} is currently connecting to a heart rate device,
     *          false otherwise
     */
    boolean isConnecting();

    /**
     * Starts scanning for available heart rate devices. Results will be passed to the HRClient
     * supplied in {@link #open(android.os.Handler, org.runnerup.hr.HRProvider.HRClient)}
     */
    void startScan();

    /**
     * Stops scanning for available heart rate devices. When done, the {@link HRClient} passed
     * in {@link #open(android.os.Handler, org.runnerup.hr.HRProvider.HRClient)} will be notified
     */
    void stopScan();

    /**
     * Connects to a heart rate monitor device
     * @param ref An object representing a heart rate device. Client code can get
     *            available device information through startScan()
     */
    void connect(HRDeviceRef ref);

    /**
     * Disconnects from a heart rate monitor device
     */
    void disconnect();

    /**
     * @return the most recent heart rate value supplied by the connected device.
     *          0 indicates that no device has been connected (or the user is in a very bad way)
     */
    int getHRValue();

    /**
     * @return the unix time of the last received heart rate value
     */
    long getHRValueTimestamp();

    /**
     * Get the time for the sensor, comparable with other sources as getTime()
     * differs for system vs GPS time
     *
     * @return the elapsed time sinc boot in nano sec for last received value
     */
    long getHRValueElapsedRealtime();

    /**
     * @return the most recent heart rate data supplied by the device. If no device has
     *          been connected, this will be null
     */
    HRData getHRData();

    /**
     * @return The battery level, in percents, of the heart rate monitor device or 0 if
     *          no device has been connected or the device doesn't supply battery information
     */
    int getBatteryLevel();
}
