package org.runnerup.hr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

/**
 * Base class for BT 2.0 HR providers. It has a thread for connecting with a
 * bluetooth device and a thread for performing data transmission when
 * connected.
 * 
 */
@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
public abstract class Bt20Base implements HRProvider {

	public static boolean checkLibrary(Context ctx) {

		// Don't bother if createInsecureRfcommSocketToServiceRecord isn't
		// available
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1)
			return false;

		return true;
	}

	// UUID
	public static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	private int hrValue = 0;
	private long hrTimestamp = 0;
	private BluetoothAdapter btAdapter = null;

	//private Context context = null;

	public Bt20Base(Context ctx) {
		//context = ctx;
	}

	private HRClient hrClient;
	private Handler hrClientHandler;

	@Override
	public void open(Handler handler, HRClient hrClient) {
		this.hrClient = hrClient;
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
		reset();
		btAdapter = null;
	}

	private boolean mIsConnecting;
	private boolean mIsConnected;
	private boolean mIsScanning;

	public void disconnect() {
		reset();
	}

	private void reset() {
		cancelThreads();
		mIsConnecting = false;
		mIsConnected = false;
		mIsScanning = false;
	}

	@Override
	public boolean isScanning() {
		return mIsScanning;
	}

	@Override
	public boolean isConnected() {
		return mIsConnected;
	}

	@Override
	public boolean isConnecting() {
		return mIsConnecting;
	}

	@Override
	public int getHRValue() {
		return hrValue;
	}

	@Override
	public long getHRValueTimestamp() {
		return hrTimestamp;
	}

	/**
	 * Cancels all the threads.
	 */
	private void cancelThreads() {
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}
		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}
	}

	@Override
	public boolean isBondingDevice() {
		return true;
	}
	
	@Override
	public void startScan() {
		if (btAdapter == null)
			return;

		mIsScanning = true;

		hrClientHandler.post(new Runnable() {
			@Override
			public void run() {
				Set<BluetoothDevice> list = new HashSet<BluetoothDevice>();
				list.addAll(btAdapter.getBondedDevices());
				publishDevice(list);
			}
		});
	}

	private void publishDevice(final Set<BluetoothDevice> list) {
		if (list.isEmpty()) {
			mIsScanning = false;
			return;
		}

		if (mIsScanning) {
			BluetoothDevice dev = list.iterator().next();
			list.remove(dev);
			hrClient.onScanResult(getProviderName(), dev);
			hrClientHandler.post(new Runnable() {

				@Override
				public void run() {
					publishDevice(list);
				}
			});
		}
	}

	@Override
	public void stopScan() {
		mIsScanning = false;
	}

	@Override
	public void connect(BluetoothDevice bluetoothDevice, String btDeviceName) {
		cancelThreads();

		mIsConnecting = true;
		connectThread = new ConnectThread(btAdapter.getRemoteDevice(bluetoothDevice.getAddress()));
		connectThread.start();
	}

	private synchronized void connected(final BluetoothSocket bluetoothSocket,
			BluetoothDevice bluetoothDevice) {
		cancelThreads();

		if (hrClient != null) {
			hrClientHandler.post(new Runnable() {

				@Override
				public void run() {
					if (mIsConnecting && hrClient != null) {
						mIsConnected = true;
						mIsConnecting = false;
						hrClient.onConnectResult(true);

						// Start connected thread...
						connectedThread = new ConnectedThread(bluetoothSocket);
						connectedThread.start();
					}
				}
			});
		}

	}

	/**
	 * A thread to connect to a bluetooth device.
	 */
	private class ConnectThread extends Thread {
		private BluetoothSocket bluetoothSocket;
		private final BluetoothDevice bluetoothDevice;

		public ConnectThread(BluetoothDevice device) {
			setName("ConnectThread-" + device.getName());
			this.bluetoothDevice = device;
		}

		BluetoothSocket tryConnect(final BluetoothDevice device, int i) throws IOException {
			BluetoothSocket sock = null;
			System.err.println("tryConnect(method: " + i + ")");
			
			switch(i) {
			case 0:
				sock = device.createRfcommSocketToServiceRecord(MY_UUID);
				break;
			case 1:
				sock = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
				break;
			case 2: {
				Method m;
				try {
					m = device.getClass().getMethod(
							"createInsecureRfcommSocket",
							new Class[] { int.class });
					m.setAccessible(true);
					sock = (BluetoothSocket) m.invoke(device, 1);
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
			}
				
			if (sock == null) {
				throw new IOException("Create socket failed!");
			}

			try {
				sock.connect();
				return sock;
			} catch (IOException ex) {
				try {
					sock.close();
				} catch (IOException ex2) {
				}

				throw ex;
			}
 		}
		
		@Override
		public void run() {
			if (btAdapter == null) {
				Bt20Base.this.reset();
				return;
			}
			
			for (int i = 0; i < 3; i++) {
				try {
					bluetoothSocket = tryConnect(bluetoothDevice, i);
					break;
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}

			// Cancel discovery to prevent slow down
			btAdapter.cancelDiscovery();

			if (bluetoothSocket == null) {
				System.err.println("connect failed!");
				Bt20Base.this.reset();
				return;
				
			}

			// Reset the ConnectThread since we are done
			synchronized (Bt20Base.this) {
				connectThread = null;
			}

			// Start the connected thread
			connected(bluetoothSocket, bluetoothDevice);
		}

		/**
		 * Cancels this thread.
		 */
		public void cancel() {
			try {
				if (bluetoothSocket != null)
					bluetoothSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * This thread handles data transmission when connected.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket bluetoothSSocket;
		private final InputStream inputStream;

		public ConnectedThread(BluetoothSocket bluetoothSocket) {
			this.bluetoothSSocket = bluetoothSocket;
			InputStream tmp = null;

			try {
				tmp = bluetoothSocket.getInputStream();
			} catch (IOException e) {
				System.err.println("socket.getInputStream(): " + e);
			}
			inputStream = tmp;
		}

		@Override
		public void run() {
			final int frameSize = getFrameSize();
			byte[] buffer = new byte[frameSize];
			int bytes; // bytes read
			int offset = 0;

			// Keep listening to the inputStream while connected
			while (true) {
				try {
					// Read from the inputStream
					bytes = inputStream
							.read(buffer, offset, frameSize - offset);

					if (bytes == -1) {
						throw new IOException("EOF reached.");
					}

					offset += bytes;

					if (offset < frameSize) {
						// Partial frame received. Call read again to receive
						// the rest.
						continue;
					}

					int hr = parseBuffer(buffer);
					if (hr < 0) {
						int index = findNextAlignment(buffer);
						if (index == -1) {
							offset = 0;
							continue;
						}
						offset = frameSize - index;
						System.arraycopy(buffer, index, buffer, 0, offset);
						continue;
					}

					hrValue = hr;
					hrTimestamp = System.currentTimeMillis();

					offset = 0;
				} catch (IOException e) {
					reportDisconnected(e);
					break;
				}
			}
		}

		/**
		 * Cancels this thread.
		 */
		public void cancel() {
			try {
				bluetoothSSocket.close();
			} catch (IOException e) {
			}
		}
	}

	public void reportDisconnected(IOException e) {
	}

	public abstract int getFrameSize();

	public abstract int parseBuffer(byte[] buffer);

	public abstract int findNextAlignment(byte[] buffer);

	public static class ZephyrHRM extends Bt20Base {

		static final byte ZEPHYR_HXM_BYTE_STX = 0;
		static final byte ZEPHYR_HXM_BYTE_HR = 12;
		static final byte ZEPHYR_HXM_BYTE_CRC = 58;
		static final byte ZEPHYR_HXM_BYTE_ETX = 59;

		static final byte ZEPHYR_START_BYTE = 0x02;
		static final byte ZEPHYR_END_BYTE = 0x03;
		public static final String NAME = "Zephyr";
		
		public ZephyrHRM(Context ctx) {
			super(ctx);
		}

		@Override
		public String getName() {
			return NAME;
		}

		@Override
		public String getProviderName() {
			return NAME;
		}

		@Override
		public int getFrameSize() {
			return ZEPHYR_HXM_BYTE_ETX + 1;
		}

		@Override
		public int parseBuffer(byte[] buffer) {
			// Check STX (Start of Text), ETX (End of Text) and CRC Checksum
		    boolean ok = buffer.length > ZEPHYR_HXM_BYTE_ETX
		        && getByte(buffer[ZEPHYR_HXM_BYTE_STX]) == ZEPHYR_START_BYTE
		        && getByte(buffer[ZEPHYR_HXM_BYTE_ETX]) == ZEPHYR_END_BYTE
		        && CalcCrc8(buffer, 3, 55) == getByte(buffer[ZEPHYR_HXM_BYTE_CRC]);

		    if (!ok)
		    	return -1;
		    
		    return getByte(buffer[ZEPHYR_HXM_BYTE_HR]); 
		}

		@Override
		public int findNextAlignment(byte[] buffer) {
			for (int i = 0; i < buffer.length - 1; i++) {
				if (getByte(buffer[i]) == ZEPHYR_END_BYTE &&
					getByte(buffer[i + 1]) == ZEPHYR_START_BYTE) {
					return i;
				}
			}
			return -1;
		}
	};

	public static class PolarHRM extends Bt20Base {

		public static final String NAME = "Polar WearLink";
		
		public PolarHRM(Context ctx) {
			super(ctx);
		}

		@Override
		public String getName() {
			return NAME;
		}

		@Override
		public String getProviderName() {
			return NAME;
		}

		@Override
		public int getFrameSize() {
			return 16;
		}

		private boolean startOfMessage(byte buffer[], int pos) {
			if (buffer.length < pos + 4)
				return false;

			int b0 = getByte(buffer[pos + 0]);
			int b1 = getByte(buffer[pos + 1]);
			int b2 = getByte(buffer[pos + 2]);
			int b3 = getByte(buffer[pos + 3]);
			if (b0 != 0xFE) {
				return false;
			}

			if ((0xFF - b1) != b2) {
				return false;
			}
		
			if (b3 >= 16) {
				return false;
			}
			
			return true;
		}
		
		@Override
		public int parseBuffer(byte[] buffer) {
			int val = -1;
			for (int i = 0; i < buffer.length - 8; i++) {
				if (startOfMessage(buffer, i)) {
					val = getByte(buffer[i + 5]);
				}
			}
			return val;
		}

		@Override
		public int findNextAlignment(byte[] buffer) {
			for (int i = 0; i < buffer.length; i++)
				if (startOfMessage(buffer, i))
					return i;
			return -1;
		}
	};
	
	public static byte CalcCrc8(byte buffer[], int start, int length) {
	    byte crc = 0x0;

	    for (int i = start; i < (start + length); i++) {
	      crc = (byte) (crc ^ buffer[i]);
	    }
	    return crc;
	}

	static public int getByte(byte b) {
		return b & 0xFF;
	}
}
