package org.runnerup.hr;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public interface HRProvider {

	public interface OnOpenCallback {
		public void onInitResult(boolean ok);
	}

	public interface OnScanResultCallback {
		public void onScanResult(String name, BluetoothDevice device);
	}

	public interface OnConnectCallback {
		public void onConnectResult(boolean connectOK);
	}
	
	public abstract String getProviderName();
	
	public abstract void open(Handler handler, OnOpenCallback cb);

	public abstract void close();

	public abstract boolean isScanning();

	public abstract void startScan(Handler handler,
			OnScanResultCallback callback);

	public abstract void stopScan();

	public abstract boolean isConnected();

	public abstract boolean isConnecting();

	public abstract void connect(Handler handler, BluetoothDevice _btDevice,
			String btDeviceName, OnConnectCallback connectCallback);

	public abstract void disconnect();

	public abstract int getHRValue();

	public abstract long getHRValueTimestamp();

}