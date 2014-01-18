package org.runnerup.hr;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public interface HRProvider {

	public interface HRClient {
		public void onOpenResult(boolean ok);
		public void onScanResult(String name, BluetoothDevice device);
		public void onConnectResult(boolean connectOK);
		public void onDisconnectResult(boolean disconnectOK);
		public void onCloseResult(boolean closeOK);
	};
	
	public abstract String getName();         // For display
	public abstract String getProviderName(); // For internal usage
	
	public abstract void open(Handler handler, HRClient hrClient);
	public abstract void close();

	public abstract boolean isBondingDevice();
	
	public abstract boolean isScanning();
	public abstract boolean isConnected();
	public abstract boolean isConnecting();

	public abstract void startScan();
	public abstract void stopScan();

	public abstract void connect(BluetoothDevice _btDevice, String btDeviceName);
	public abstract void disconnect();

	public abstract int getHRValue();
	public abstract long getHRValueTimestamp();
}
