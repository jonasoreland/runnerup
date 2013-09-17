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

import org.runnerup.R;
import org.runnerup.gpstracker.hr.HRManager;
import org.runnerup.gpstracker.hr.HRProvider;
import org.runnerup.gpstracker.hr.HRProvider.OnConnectCallback;
import org.runnerup.gpstracker.hr.HRProvider.OnOpenCallback;
import org.runnerup.gpstracker.hr.HRProvider.OnScanResultCallback;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRSettingsActivity extends Activity {

	private Handler handler = new Handler();
	
	List<HRProvider> providers = null;
	String btName;
	String btAddress;
	String btProviderName;
	HRProvider hrProvider = null;
	BluetoothDevice hrDevice = null;
	
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

		Resources res = getResources();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		btName = prefs.getString(res.getString(R.string.pref_bt_name), null);
		btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
		btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
		
		if (btProviderName != null) {
			hrProvider = HRManager.getHRProvider(this, btProviderName);
		}

		scanButton = (Button) findViewById(R.id.scanButton);
		scanButton.setOnClickListener(scanButtonClick);
		connectButton = (Button) findViewById(R.id.connectButton);
		connectButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				connect();
			}});
		tvBTName = (TextView) findViewById(R.id.btDevice);
		tvHR = (TextView) findViewById(R.id.hrValueText); 
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (scanProvider != null) {
			scanProvider.stopScan();
			scanProvider.close();
		}
		
		if (hrProvider != null) {
			hrProvider.disconnect();
			hrProvider.close();
		}
	}
	
	HRProvider scanProvider = null;
	BluetoothDevice scanBtDevice;

	OnClickListener scanButtonClick = new OnClickListener() {
		public void onClick(View v) {
			if (providers.size() > 1) {
				selectProvider();
			} else {
				scanProvider = providers.get(0);
				scanProvider.open(handler, new OnOpenCallback(){
					@Override
					public void onInitResult(boolean ok) {
						if (ok) {
							startScan();
						} else {
							scanProvider = null;
							Toast.makeText(HRSettingsActivity.this, "Failed to init HRM", Toast.LENGTH_SHORT).show();
						}
					}});
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
						if (scanProvider != null) {
							scanProvider.open(handler, new OnOpenCallback(){
								@Override
								public void onInitResult(boolean ok) {
									if (ok) {
										startScan();
									} else {
										scanProvider = null;
										Toast.makeText(HRSettingsActivity.this, "Failed to init HRM", Toast.LENGTH_SHORT).show();
									}
									dialog.dismiss();
								}});
						}
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
						scanProvider = HRManager.getHRProvider(HRSettingsActivity.this, items[arg1].toString());
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
		scanProvider.startScan(handler, new OnScanResultCallback(){
   			@Override
			public void onScanResult(String name, BluetoothDevice device) {
   				deviceAdapter.deviceList.add(device);
   				deviceAdapter.notifyDataSetChanged();
			}});
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Scanning");
		builder.setPositiveButton("Select",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						scanProvider.stopScan();
						if (scanBtDevice != null) {
							if (hrProvider != null) {
								hrProvider.disconnect();
							}
							hrProvider = scanProvider;
							hrDevice = scanBtDevice;
							connect();
						}
						scanProvider = null;
						dialog.dismiss();
					}
				});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						scanProvider.stopScan();
						scanProvider = null;
						dialog.dismiss();
					}

				});
		builder.setSingleChoiceItems(deviceAdapter, -1, 
				new  DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						scanBtDevice = deviceAdapter.deviceList.get(arg1);
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
		hrProvider.connect(handler, hrDevice, connectCallback);
		connectButton.setText("Connecting");
	}

	private OnConnectCallback connectCallback = new OnConnectCallback() {

		@Override
		public void onConnectResult(boolean connectOK) {
			if (connectOK) {
				connectButton.setText("Disconnect");
				startTimer();
			} else {
				
			}
		}
		
	};
	
	private CharSequence getName(BluetoothDevice dev) {
		if (dev.getName() != null && dev.getName().length() > 0)
			return dev.getName();
		return dev.getAddress();
	}

	protected void startTimer() {
		// TODO Auto-generated method stub
		
	}
}
