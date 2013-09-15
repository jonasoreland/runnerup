package org.runnerup.gpstracker.hr;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MockHRProvider implements HRProvider {

	@Override
	public String getProviderName() {
		return "MockHR";
	}

	@Override
	public void open(OnOpenCallback cb) {
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

	@Override
	public void startScan(Handler handler, OnScanResultCallback callback) {
		mIsScanning = true;
		scanHandler = handler;
		onScanResultCallback  = callback;
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
	public void connect(Handler handler, String _btDevice,
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
