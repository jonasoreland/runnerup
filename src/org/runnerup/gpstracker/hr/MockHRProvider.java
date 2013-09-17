package org.runnerup.gpstracker.hr;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MockHRProvider implements HRProvider {

	protected static final String NAME = "MockHR";

	@Override
	public String getProviderName() {
		return "MockHR";
	}

	@Override
	public void open(Handler handler, OnOpenCallback cb) {
		cb.onInitResult(true);
	}

	@Override
	public void close() {
	}

	boolean mIsScanning = false;
	Handler scanHandler = null;
	OnScanResultCallback onScanResultCallback = null;

	@Override
	public boolean isScanning() {
		return mIsScanning;
	}

	Runnable fakeScanResult = new Runnable() {
		@Override
		public void run() {
			if (mIsScanning) {
				String dev = "00:43:A8:23:10:"+String.format("%02X", System.currentTimeMillis() % 256);
				onScanResultCallback.onScanResult(NAME, BluetoothAdapter.getDefaultAdapter().getRemoteDevice(dev));
				scanHandler.postDelayed(fakeScanResult, 3000);
			}
		}
	};
	
	@Override
	public void startScan(Handler handler, OnScanResultCallback callback) {
		mIsScanning = true;
		scanHandler = handler;
		onScanResultCallback  = callback;
		scanHandler.postDelayed(fakeScanResult, 3000);
	}

	@Override
	public void stopScan() {
		mIsScanning = false;
		scanHandler = null;
		onScanResultCallback = null;
	}

	boolean mIsConnecting = false;
	boolean mIsConnected = false;
	Handler connectHandler = null;
	OnConnectCallback onConnectCallback = null;
	
	@Override
	public boolean isConnected() {
		return mIsConnected;
	}

	@Override
	public boolean isConnecting() {
		return mIsConnecting;
	}

	@Override
	public void connect(Handler handler, BluetoothDevice _btDevice,
			OnConnectCallback connectCallback) {
		connectHandler = handler;
		onConnectCallback = connectCallback;

		if (mIsConnected)
			return;
		
		if (mIsConnecting)
			return;
		
		mIsConnecting = true;
	}

	@Override
	public void disconnect() {
		mIsConnecting = false;
		mIsConnected = false;
		connectHandler = null;
		onConnectCallback = null;
	}

	@Override
	public int getHRValue() {
		return 0;
	}

	@Override
	public long getHRValueTimestamp() {
		// TODO Auto-generated method stub
		return 0;
	}
}
