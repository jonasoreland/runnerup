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

import java.util.UUID;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

@TargetApi(18)
public class AndroidBLEHRProvider implements HRProvider {

	public static boolean checkLibrary(Context ctx) {
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
			return false;
		
		if (!ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			return false;
		}
		
		return true;
	}	
	
	static final String NAME = "AndroidBLE";
	static final String DISPLAY_NAME = "Bluetooth SMART (BLE)";
    static final UUID HRP_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
	static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    static final UUID HEART_RATE_MEASUREMENT_CHARAC = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    static final UUID[] SCAN_UUIDS = { HRP_SERVICE };

	private Context context;
	private BluetoothAdapter btAdapter = null;
	private BluetoothGatt btGatt = null;
	private BluetoothDevice btDevice = null;
	private int hrValue = 0;
	private long hrTimestamp = 0;
	
	private HRClient hrClient;
	private Handler hrClientHandler;
	
	public AndroidBLEHRProvider(Context ctx) {
		context = ctx;
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
		this.hrClient =  hrClient;
		this.hrClientHandler = handler;
		
		if (btAdapter == null) {
			btAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		if (btAdapter == null) {
			hrClient.onOpenResult(false);
			return;
		}

		hrClient.onOpenResult(true);
	}
	
	@Override
	public void close() {
		stopScan();
		disconnect();

		if (btGatt != null) {
			btGatt.close();
			btGatt = null;
		}
			
		if (btAdapter == null) {
			btAdapter = null;
			return;
		}
	}
	
	private BluetoothGattCallback btGattCallbacks = new BluetoothGattCallback() {

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic arg0) {
			if (gatt != btGatt) {
				return;
			}
			
			if (!arg0.getUuid().equals(HEART_RATE_MEASUREMENT_CHARAC)) {
				System.err.println("onCharacteristicChanged("+arg0+") != HEART_RATE ??");
				return;
			}

            int length = arg0.getValue().length;
            if (length == 0) {
            	System.err.println("length = 0");
            	return;
            }

            if (isHeartRateInUINT16(arg0.getValue()[0])) {
                hrValue = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            } else {
                hrValue = arg0.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
            }
            hrTimestamp = System.currentTimeMillis();
            
            if (mIsConnecting) {
				reportConnected(true);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic arg0, int status) {
			System.out.println("onCharacteristicRead(): " + gatt + ", char: " + arg0 + ", status: " + status);
			
			if (gatt != btGatt)
				return;
			
			UUID charUuid = arg0.getUuid();
            if (charUuid.equals(FIRMWARE_REVISON_UUID)) {
    			System.out.println(" => startHR()");
            	// triggered from DummyReadForSecLevelCheck
            	startHR();
            	return;
            }
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicWrite(gatt, characteristic, status);
		}

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {

			System.err.println("onConnectionStateChange: " + gatt + ", status: " + status + ", newState: " + newState);
			
			if (gatt != btGatt)
				return;

			if (newState == BluetoothProfile.STATE_CONNECTED) {
				System.err.println("discoverServices()");
				btGatt.discoverServices();
			}
			
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            	reportDisconnected();
            }
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor arg0, int status) {

			BluetoothGattCharacteristic mHRMcharac = arg0.getCharacteristic();
            if (!enableNotification(true, mHRMcharac)) {
            	reportConnectFailed("Failed to enable notification in onDescriptorRead");
            }
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorWrite(gatt, descriptor, status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
		}

		@Override
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			System.out.println("onServicesDiscoverd(): " + gatt + ", status: " + status);
			
			if (gatt != btGatt)
				return;
			
			System.out.println(" => DummyRead");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				DummyReadForSecLevelCheck(gatt);
				// continue in onCharacteristicRead
			} else {
				DummyReadForSecLevelCheck(gatt); 
//				reportConnectFailed("onServicesDiscovered(" + gatt + ", " + status + ")");
			}
		}

	    /*
	     * from Samsung HRPService.java
	     */
		private void DummyReadForSecLevelCheck(BluetoothGatt btGatt) {
			if (btGatt == null)
				return;
			
			BluetoothGattService disService = btGatt.getService(DIS_UUID);
			if (disService == null) {
				reportConnectFailed("Dis service not found");
				return;
			}
			BluetoothGattCharacteristic firmwareIdCharc = disService.getCharacteristic(FIRMWARE_REVISON_UUID);
			if (firmwareIdCharc == null) {
				reportConnectFailed("firmware revison charateristic not found!");
				return;
			}

			if (btGatt.readCharacteristic(firmwareIdCharc) == false) {
				reportConnectFailed("firmware revison reading is failed!");
			}
			// continue in onCharacteristicRead
		}
		
		private boolean isHeartRateInUINT16(byte b) {
	        if ((b & 1) != 0)
	            return true;
			return false;
		}

		private void startHR() {
	        BluetoothGattService mHRP = btGatt.getService(HRP_SERVICE);
	        if (mHRP == null) {
	        	reportConnectFailed("HRP service not found!");
	            return;
	        }

	        BluetoothGattCharacteristic mHRMcharac = mHRP.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
	        if (mHRMcharac == null) {
	            reportConnectFailed("HEART RATE MEASUREMENT charateristic not found!");
	            return;
	        }
	        BluetoothGattDescriptor mHRMccc = mHRMcharac.getDescriptor(CCC);
	        if (mHRMccc == null) {
	            reportConnectFailed("CCC for HEART RATE MEASUREMENT charateristic not found!");
	            return;
	        }
	        if (btGatt.readDescriptor(mHRMccc) == false) {
	        	reportConnectFailed("readDescriptor() is failed");
	            return;
	        }
	        // Continue in onDescriptorRead
		}

	};

	private boolean enableNotification(boolean onoff, BluetoothGattCharacteristic charac) {
		if (btGatt == null)
			return false;

		if (!btGatt.setCharacteristicNotification(charac,  onoff)) {
			System.err.println("btGatt.setCharacteristicNotification() failed");
			return false;
		}
		
        BluetoothGattDescriptor clientConfig = charac.getDescriptor(CCC);
        if (clientConfig == null) {
			System.err.println("clientConfig == null");
            return false;
        }
        
        if (onoff) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return btGatt.writeDescriptor(clientConfig);
    }

	private boolean mIsScanning = false;

	@Override
	public boolean isScanning() {
		return mIsScanning;
	}

	@Override
	public void startScan() {
       	if (mIsScanning)
       		return;

       	mIsScanning = true;
		btAdapter.startLeScan(SCAN_UUIDS,
				new BluetoothAdapter.LeScanCallback() {
					@Override
					public void onLeScan(final BluetoothDevice device,
							int rssi, byte[] scanRecord) {

						if (hrClient == null)
							return;

						if (hrClientHandler == null)
							return;

						hrClientHandler.post(new Runnable() {
							@Override
							public void run() {
								if (mIsScanning) { // NOTE: mIsScanning in user-thread
									hrClient.onScanResult(NAME, device);
								}
							}
						});
					}
				});
	}

	@Override
	public void stopScan() {
		if (mIsScanning) {
			mIsScanning = false;
			btAdapter.stopLeScan(new BluetoothAdapter.LeScanCallback(){
				
				@Override
				public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
				}
			});
		}
	}

	private boolean mIsConnected = false;
	private boolean mIsConnecting = false;

	@Override
	public boolean isConnected() {
		return mIsConnected;
	}

	@Override
	public boolean isConnecting() {
		return mIsConnecting;
	}

	@Override
	public void connect(BluetoothDevice dev, String btDeviceName) {
		stopScan();

		if (mIsConnected)
			return;

		if (mIsConnecting)
			return;
		
		mIsConnecting = true;
		btDevice = dev;
		btGatt = btDevice.connectGatt(context, true, btGattCallbacks);
		if (btGatt == null) {
			reportConnectFailed("connectGatt returned null");
		}
	}

	private void reportConnected(final boolean b) {
		if (hrClientHandler != null) {
			hrClientHandler.post(new Runnable(){
				@Override
				public void run() {
					if (mIsConnecting && hrClient != null) {
						mIsConnected = b;
						mIsConnecting = false;
						hrClient.onConnectResult(b);
					}
				}});
		}
	}

	private void reportConnectFailed(String string) {
		System.err.println("reportConnectFailed("+string+")");
		if (btGatt != null) {
			btGatt.close();
			btGatt = null;
		}
		btDevice = null;
		reportConnected(false);
	}

	@Override
	public void disconnect() {
		if (btGatt == null)
			return;
		
		if (btDevice == null) {
			return;
		}

		if (mIsConnecting == false && mIsConnected == false)
			return;

		mIsConnected = false;
		mIsConnecting = false;
		
		do {
			BluetoothGattService mHRP = btGatt.getService(HRP_SERVICE);
			if (mHRP == null) {
				reportDisconnectFailed("HRP service not found!");
				break;
			}

			BluetoothGattCharacteristic mHRMcharac = mHRP.getCharacteristic(HEART_RATE_MEASUREMENT_CHARAC);
			if (mHRMcharac == null) {
				reportDisconnectFailed("HEART RATE MEASUREMENT charateristic not found!");
				break;
			}
        
			if (!enableNotification(false, mHRMcharac)) {
				reportDisconnectFailed("disableNotfication");
				break;
			}
		} while (false);

		btGatt.close();
		btGatt = null;
		btDevice = null;
	}

	private void reportDisconnectFailed(String string) {
		System.err.println("disconnect failed: " + string);
	}

	private void reportDisconnected() {
	}

	@Override
	public int getHRValue() {
		return this.hrValue;
	}

	@Override
	public long getHRValueTimestamp() {
		return this.hrTimestamp;
	}
}
