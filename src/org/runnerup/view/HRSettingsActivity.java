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
package org.runnerup.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.runnerup.R;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.HRProvider.HRClient;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRSettingsActivity extends Activity implements HRClient {

	private Handler handler = new Handler();
	
	List<HRProvider> providers = null;
	String btName;
	String btAddress;
	String btProviderName;
	HRProvider hrProvider = null;
	BluetoothDevice hrDevice = null;
	BluetoothAdapter mAdapter = null;
	
	Button connectButton = null;
	Button scanButton = null;
	TextView tvBTName = null;
	TextView tvHR = null;
	FrameLayout flHR = null;

	DeviceAdapter deviceAdapter = null;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hr_settings);

		providers = HRManager.getHRProviderList(this);
		deviceAdapter = new DeviceAdapter(this);
		
		if (providers.isEmpty()) {
			notSupported();
			return;
		}

		tvBTName = (TextView) findViewById(R.id.btDevice);
		tvHR = (TextView) findViewById(R.id.hrValueText); 
		scanButton = (Button) findViewById(R.id.scanButton);
		scanButton.setOnClickListener(scanButtonClick);
		connectButton = (Button) findViewById(R.id.connectButton);
		connectButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				connect();
			}});

		Resources res = getResources();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		btName = prefs.getString(res.getString(R.string.pref_bt_name), null);
		btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
		btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);

		System.err.println("btName: " + btName);
		System.err.println("btAddress: " + btAddress);
		System.err.println("btProviderName: " + btProviderName);

		
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mAdapter == null) {
		    notSupported();
		} else {
		    if (!mAdapter.isEnabled()) {
		    	Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	            startActivityForResult(enableIntent, 0);
	            return;
		    }
		}		

		btEnabled();
	}

	private void btEnabled() {
		if (btProviderName != null) {
			hrProvider = HRManager.getHRProvider(this, btProviderName);
		}

		open();
	}
	
	private void open() {
		if (hrProvider != null) {
			hrProvider.open(handler, this);
		}
	}

	public void notSupported() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Heart rate monitor is not supported for your device...try again later");
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				finish();
			}
		};
		builder.setNegativeButton("ok, rats",  listener);
		builder.show();
		return;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (hrProvider != null) {
			hrProvider.close();
		}

		stopTimer();
	}
	
	private void clear() {
		btAddress = null;
		btName = null;
		btProviderName = null;
		hrDevice = null;
	}
	
	OnClickListener scanButtonClick = new OnClickListener() {
		public void onClick(View v) {
			clear();
			
			if (hrProvider != null) {
				hrProvider.close();
				hrProvider = null;
			}
			
			if (providers.size() > 1) {
				selectProvider();
			} else {
				startScan();
			}
		}
	};

	private void selectProvider() {
		final CharSequence items[] = new CharSequence[providers.size()];
		for (int i = 0; i < items.length; i++)
			items[i] = providers.get(i).getProviderName();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select type of Bluetooth device");
		builder.setPositiveButton("OK",
				new DialogInterface.OnClickListener() {
					public void onClick(final DialogInterface dialog, int which) {
						open();
					}
				});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}

				});
		builder.setSingleChoiceItems(items, -1, 
				new  DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						hrProvider = HRManager.getHRProvider(HRSettingsActivity.this, items[arg1].toString());
					}
				});
		builder.show();
	}

	class DeviceAdapter extends BaseAdapter {

		ArrayList<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
		LayoutInflater inflater = null;
		Resources resources = null;
		
		DeviceAdapter(Context ctx) {
			inflater = (LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			resources = ctx.getResources();
		}
		
		@Override
		public int getCount() {
			return deviceList.size();
		}

		@Override
		public Object getItem(int position) {
			return deviceList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row;
			if (convertView == null) {
				row = inflater.inflate(android.R.layout.simple_list_item_single_choice,
						null);
			} else {
				row = convertView;
			}
			TextView tv = (TextView) row.findViewById(android.R.id.text1);
			tv.setTextColor(resources.getColor(R.color.black));

			BluetoothDevice btDevice = deviceList.get(position);
			tv.setTag(btDevice);
			tv.setText(getName(btDevice));

			return tv;
		}
	};
	
	private void startScan() {
		deviceAdapter.deviceList.clear();
		hrProvider.startScan();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Scanning");
		builder.setPositiveButton("Select",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						hrProvider.stopScan();
						connect();
						dialog.dismiss();
					}
				});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						hrProvider.stopScan();
						dialog.dismiss();
					}

				});
		builder.setSingleChoiceItems(deviceAdapter, -1, 
				new  DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						hrDevice = deviceAdapter.deviceList.get(arg1);
					}
				});
		builder.show();
	}
	
	void connect() {
		connectButton.setEnabled(false);
		if (hrDevice == null) {
			tvBTName.setText("");
			return;
		}
		tvBTName.setText(getName(hrDevice));
		tvHR.setText("?");
		hrProvider.connect(hrDevice, hrDevice.getName());
		connectButton.setText("Connecting");
	}

	private void save() {
		btName = hrDevice.getName();
		btAddress = hrDevice.getAddress();
		
		Resources res = getResources();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor ed = prefs.edit();
		ed.putString(res.getString(R.string.pref_bt_name), btName);
		ed.putString(res.getString(R.string.pref_bt_address), btAddress);
		ed.putString(res.getString(R.string.pref_bt_provider), hrProvider.getProviderName());
		ed.commit();
	}
	
	private CharSequence getName(BluetoothDevice dev) {
		if (dev.getName() != null && dev.getName().length() > 0)
			return dev.getName();
		return dev.getAddress();
	}

	Timer hrReader = null;
	void startTimer() {
		hrReader = new Timer();
		hrReader.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						readHR();
					}
				});
			}
		}, 0, 500);
	}
	
	void stopTimer() {
		if (hrReader == null)
			return;
		
		hrReader.cancel();
		hrReader.purge();
		hrReader = null;
	}

	protected void readHR() {
		if (hrProvider != null) {
			long now = System.currentTimeMillis();
			long age = hrProvider.getHRValueTimestamp();
			tvHR.setText("HR: " + hrProvider.getHRValue() + ", age: " + (now - age) + "ms");
		}
	}

	@Override
	public void onOpenResult(boolean ok) {
		if (btAddress != null) {
			hrDevice = mAdapter.getRemoteDevice(btAddress);
			System.err.println("hrDevice.getName(): "
				+ hrDevice.getName() + ", btName: "
				+ btName);
			connect();
		} else {
			startScan();
		}
	}

	@Override
	public void onScanResult(String name, BluetoothDevice device) {
		deviceAdapter.deviceList.add(device);
		deviceAdapter.notifyDataSetChanged();
	}

	@Override
	public void onConnectResult(boolean connectOK) {
		if (connectOK) {
			save();
			connectButton.setText("Disconnect");
			startTimer();
		} else {
			
		}
	}

	@Override
	public void onDisconnectResult(boolean disconnectOK) {
	}

	@Override
	public void onCloseResult(boolean closeOK) {
	}
}
