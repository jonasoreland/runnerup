package org.runnerup.hr;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MockHRProvider implements HRProvider {

	Handler handler = new Handler();
	protected static final String NAME = "MockHR";

	public MockHRProvider(Context ctx) {
	}

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
		int count = 0;
		@Override
		public void run() {
			if (mIsScanning) {
				String dev = "00:43:A8:23:10:"+String.format("%02X", System.currentTimeMillis() % 256);
				onScanResultCallback.onScanResult(NAME, BluetoothAdapter.getDefaultAdapter().getRemoteDevice(dev));
				if (++count < 3) {
					scanHandler.postDelayed(fakeScanResult, 3000);
					return;
				}
			}
			count = 0;
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
	public void connect(final Handler cHandler, BluetoothDevice _btDevice,
			OnConnectCallback connectCallback) {
		connectHandler = cHandler;
		onConnectCallback = connectCallback;

		if (mIsConnected)
			return;
		
		if (mIsConnecting)
			return;
		
		mIsConnecting = true;
		connectHandler.postDelayed(new Runnable(){
			@Override
			public void run() {
				if (mIsConnecting) {
					mIsConnected = true;
					mIsConnecting = false;
					onConnectCallback.onConnectResult(true);
					handler.postDelayed(hrUpdate, 750);
				}
			}}, 3000);
	}

	Runnable hrUpdate = new Runnable() {
		@Override
		public void run() {
			hrValue = (int) (150 + 40 * Math.random());
			hrTimestamp = System.currentTimeMillis();
			if (mIsConnected == true) {
				handler.postDelayed(hrUpdate, 750);
			}
		}
	};
	
	@Override
	public void disconnect() {
		mIsConnecting = false;
		mIsConnected = false;
		connectHandler = null;
		onConnectCallback = null;
	}

	int hrValue = 0;
	long hrTimestamp = 0;
	
	@Override
	public int getHRValue() {
		return hrValue;
	}

	@Override
	public long getHRValueTimestamp() {
		return hrTimestamp;
	}
}
