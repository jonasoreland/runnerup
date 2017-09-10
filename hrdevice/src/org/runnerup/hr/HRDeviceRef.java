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

package org.runnerup.hr;

/**
 * This interface is used instead as a device handle. (since Ant+ doesn't used
 * BluetoothDevice as a device representation)
 * 
 * @author jonas
 */

public class HRDeviceRef {

    private final String provider;
    public final String deviceName;
    public final String deviceAddress;

    private HRDeviceRef(final String provider, final String name, final String address) {
        this.provider = provider;
        this.deviceName = name;
        this.deviceAddress = address;
    }

    public static HRDeviceRef create(String providerName, String deviceName, String deviceAddress) {
        return new HRDeviceRef(providerName, deviceName, deviceAddress);
    }

    public String getProvider() {
        return provider;
    }

    public String getName() {
        if (deviceName != null && !"".contentEquals(deviceName))
            return deviceName;
        return deviceAddress;
    }

    public String getAddress() {
        return deviceAddress;
    }
}
