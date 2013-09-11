package org.runnerup.gpstracker.hr;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;

public interface HRProvider {

	public interface OnOpenCallback {
		public void onInitResult(boolean ok);
	}

	public interface OnScanResultCallback {
		public void onScanResult(String src, BluetoothDevice device);
	}

	public interface OnConnectCallback {
		public void onConnectResult(boolean connectOK);
	}
	
	public abstract void open(OnOpenCallback cb);

	public abstract void close();

	public abstract boolean isScanning();

	public abstract void startScan(Handler handler,
			OnScanResultCallback callback);

	public abstract void stopScan();

	public abstract boolean isConnected();

	public abstract boolean isConnecting();

	public abstract void connect(Handler handler, String _btDevice,
			OnConnectCallback connectCallback);

	public abstract void disconnect();

	public abstract int getHRValue();

	public abstract long getHRValueTimestamp();

}