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

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;

public class AntPlus implements HRProvider {

	static final String NAME = "AntPlus";
	static final String DISPLAY_NAME = "Ant+";
	
	public static boolean checkLibrary(Context ctx) {
		// TODO Auto-generated method stub
		return false;
	}
	public AntPlus(Context ctx) {
	}

	@Override
	public String getName() {
		return DISPLAY_NAME;
	}

	@Override
	public String getProviderName() {
		return NAME;
	}

	@Override
	public void open(Handler handler, HRClient hrClient) {
		// TODO Auto-generated method stub

	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isBondingDevice() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isScanning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isConnecting() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void startScan() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stopScan() {
		// TODO Auto-generated method stub

	}

	@Override
	public void connect(BluetoothDevice _btDevice, String btDeviceName) {
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub

	}

	@Override
	public int getHRValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getHRValueTimestamp() {
		// TODO Auto-generated method stub
		return 0;
	}
}
