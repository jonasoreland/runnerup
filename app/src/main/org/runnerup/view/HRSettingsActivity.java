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

import android.annotation.SuppressLint;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.runnerup.R;
import org.runnerup.hr.HRData;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.HRProvider.HRClient;
import org.runnerup.util.Formatter;
import org.runnerup.widget.WidgetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class HRSettingsActivity extends AppCompatActivity implements HRClient {

    private final Handler handler = new Handler();
    private final StringBuffer logBuffer = new StringBuffer();

    private List<HRProvider> providers = null;
    private String btName;
    private String btAddress;
    private String btProviderName;
    private HRProvider hrProvider = null;

    private Button connectButton = null;
    private Button scanButton = null;
    private TextView tvBTName = null;
    private TextView tvHR = null;
    private TextView tvLog = null;
    private TextView tvBatteryLevel = null;

    private Formatter formatter = null;
    private GraphView graphView = null;
    private LineGraphSeries<DataPoint> graphViewSeries = null;
    private final ArrayList<DataPoint> graphViewListData = new ArrayList<>();
    private DataPoint[] graphViewArrayData = new DataPoint[0];
    private static final int GRAPH_HISTORY_SECONDS = 180;

    private DeviceAdapter deviceAdapter = null;
    private boolean mIsScanning = false;

    private final OnClickListener hrZonesClick = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            startActivity(new Intent(HRSettingsActivity.this, HRZonesActivity.class));
        }
    };
    
    private final OnClickListener scanButtonClick = new OnClickListener() {
        public void onClick(View v) {
            clear();
            stopTimer();

            close();
            mIsScanning = true;
            log("select HR-provider");
            selectProvider();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hr_settings);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        providers = HRManager.getHRProviderList(this);
        deviceAdapter = new DeviceAdapter(this);

        if (providers.isEmpty()) {
            notSupported();
        }

        tvLog = (TextView) findViewById(R.id.hr_log);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvBTName = (TextView) findViewById(R.id.hr_device);
        tvHR = (TextView) findViewById(R.id.hr_value);
        tvBatteryLevel = (TextView) findViewById(R.id.hr_battery);
        tvBatteryLevel.setVisibility(View.GONE);
        scanButton = (Button) findViewById(R.id.scan_button);
        scanButton.setOnClickListener(scanButtonClick);
        connectButton = (Button) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                connect();
            }
        });

        formatter = new Formatter(this);
        {
            LinearLayout graphLayout = (LinearLayout) findViewById(R.id.hr_graph_layout);
            graphView = new GraphView(this);
            graphView.setTitle(getString(R.string.Heart_rate));
            graphView.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                    if (isValueX) {
                        return formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, (long) value);
                    } else {
                        return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
                    }
                }
            });
            graphLayout.addView(graphView);
        }

        load();
        open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        close();
        stopTimer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.hrsettings_menu, menu);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Resources res = getResources();
        boolean isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_experimental), false);
        MenuItem item = menu.findItem(R.id.menu_hrdevice_expermental);
        item.setChecked(isChecked);
        isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_mock), false);
        item = menu.findItem(R.id.menu_hrdevice_mock);
        item.setChecked(isChecked);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_hrsettings_clear:
                clearHRSettings();
                return true;
            case R.id.menu_hrzones:
                hrZonesClick.onClick(null);
                return true;
            case R.id.menu_hrdevice_expermental:
            case R.id.menu_hrdevice_mock:
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                Resources res = getResources();
                SharedPreferences.Editor editor = prefs.edit();
                int key;
                if (item.getItemId() == R.id.menu_hrdevice_expermental) {
                    key = R.string.pref_bt_experimental;
                } else {
                    key = R.string.pref_bt_mock;
                }
                editor.putBoolean(res.getString(key), isChecked);
                editor.apply();
                providers = HRManager.getHRProviderList(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (!hrProvider.isEnabled()) {
                log("Bluetooth not enabled!");
                scanButton.setEnabled(false);
                connectButton.setEnabled(false);
                return;
            }
            load();
            open();
            return;
        }
        if (requestCode == 123) {
            startScan();
        }
    }
    
    private int lineNo = 0;

    private void log(String msg) {
        logBuffer.insert(0, Integer.toString(++lineNo) + ": " + msg + "\n");
        if (logBuffer.length() > 5000) {
            logBuffer.setLength(5000);
        }
        tvLog.setText(logBuffer.toString());
    }

    private void clearHRSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Clear_HR_settings))
                .setMessage(getString(R.string.Are_you_sure))
                .setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                doClear();
            }
        })

                .setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.show();
    }

    private void load() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        btName = prefs.getString(res.getString(R.string.pref_bt_name), null);
        btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
        Log.e(getClass().getName(), "btName: " + btName);
        Log.e(getClass().getName(), "btAddress: " + btAddress);
        Log.e(getClass().getName(), "btProviderName: " + btProviderName);

        if (btProviderName != null) {
            log("HRManager.get(" + btProviderName + ")");
            hrProvider = HRManager.getHRProvider(this, btProviderName);
        }
    }

    private void open() {
        if (hrProvider != null && !hrProvider.isEnabled()) {
            if (hrProvider.startEnableIntent(this, 0)) {
                return;
            }
            hrProvider = null;
        }
        if (hrProvider != null) {
            log(hrProvider.getProviderName() + ".open(this)");
            hrProvider.open(handler, this);
        } else {
            updateView();
        }
    }

    private void close() {
        if (hrProvider != null) {
            log(hrProvider.getProviderName() + ".close()");
            hrProvider.disconnect();
            hrProvider.close();
            hrProvider = null;
        }
    }

    private void notSupported() {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Heart_rate_monitor_is_not_supported_for_your_device))
                .setNegativeButton(getString(R.string.ok_rats), listener);
        builder.show();
    }

    private void clear() {
        btAddress = null;
        btName = null;
        btProviderName = null;
        clearGraph();
    }

    private void clearGraph() {
        graphView.removeAllSeries();
        graphViewSeries = null;
        graphViewListData.clear();
        graphViewArrayData = new DataPoint[0];
    }

    private void updateView() {
        if (hrProvider == null) {
            scanButton.setEnabled(true);
            connectButton.setEnabled(false);
            connectButton.setText(getString(R.string.Connect));
            tvBTName.setText("");
            tvHR.setText("");
            return;
        }

        if (btName != null) {
            tvBTName.setText(btName);
        } else {
            tvBTName.setText("");
            tvHR.setText("");
        }

        if (hrProvider.isConnected()) {
            connectButton.setText(getString(R.string.Disconnect));
            connectButton.setEnabled(true);
        } else if (hrProvider.isConnecting()) {
            connectButton.setEnabled(false);
            connectButton.setText(getString(R.string.Connecting));
        } else {
            connectButton.setEnabled(btName != null);
            connectButton.setText(getString(R.string.Connect));
        }
    }

    private void selectProvider() {
        final CharSequence items[] = new CharSequence[providers.size()];
        final CharSequence itemNames[] = new CharSequence[providers.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = providers.get(i).getProviderName();
            itemNames[i] = providers.get(i).getName();
        }
        
        hrProvider = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Select_type_of_Bluetooth_device))
                .setPositiveButton(getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int which) {
                        if (hrProvider == null && items.length > 0) {
                            // Select the first in the list
                            hrProvider = HRManager.getHRProvider(HRSettingsActivity.this,
                                    items[0].toString());
                        }
                        log("hrProvider = " + (hrProvider == null ? "null" : hrProvider.getProviderName()));
                        open();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mIsScanning = false;
                        hrProvider = null;
                        load();
                        open();
                        dialog.dismiss();
                    }

                })
                .setSingleChoiceItems(itemNames, 0,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        hrProvider = HRManager.getHRProvider(HRSettingsActivity.this,
                                items[arg1].toString());
                        log("hrProvider = " + (hrProvider == null ? "null" : hrProvider.getProviderName()));
                    }
                });
        builder.show();
    }

    private void startScan() {
        log(hrProvider.getProviderName() + ".startScan()");
        updateView();
        deviceAdapter.deviceList.clear();
        hrProvider.startScan();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Scanning))
                .setPositiveButton(getString(R.string.Connect),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        log(hrProvider.getProviderName() + ".stopScan()");
                        hrProvider.stopScan();
                        connect();
                        updateView();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        log(hrProvider.getProviderName() + ".stopScan()");
                        hrProvider.stopScan();
                        load();
                        open();
                        dialog.dismiss();
                        updateView();
                    }
                })
                .setSingleChoiceItems(deviceAdapter, -1,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        HRDeviceRef hrDevice = deviceAdapter.deviceList.get(arg1);
                        btAddress = hrDevice.getAddress();
                        btName = hrDevice.getName();
                    }
                });
        if (hrProvider.isBondingDevice()) {
            builder.setNeutralButton("Pairing", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivityForResult(i, 123);
                }
            });
        }
        builder.show();
    }

    private void connect() {
        stopTimer();
        if (hrProvider == null || btName == null || btAddress == null) {
            updateView();
            return;
        }
        if (hrProvider.isConnecting() || hrProvider.isConnected()) {
            log(hrProvider.getProviderName() + ".disconnect()");
            hrProvider.disconnect();
            hrProvider.close();
            updateView();
            return;
        }

        tvBTName.setText(getName());
        tvHR.setText("?");
        String name = btName;
        if (name == null || name.length() == 0) {
            name = btAddress;
        }
        log(hrProvider.getProviderName() + ".connect(" + name + ")");
        hrProvider.connect(HRDeviceRef.create(btProviderName, btName, btAddress));
        updateView();
    }

    private void save() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor ed = prefs.edit()
                .putString(res.getString(R.string.pref_bt_name), btName)
                .putString(res.getString(R.string.pref_bt_address), btAddress)
                .putString(res.getString(R.string.pref_bt_provider), hrProvider.getProviderName());
        ed.apply();
    }

    private void doClear() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor ed = prefs.edit()
                .remove(res.getString(R.string.pref_bt_name))
                .remove(res.getString(R.string.pref_bt_address))
                .remove(res.getString(R.string.pref_bt_provider));
        ed.apply();
    }

    private CharSequence getName() {
        if (btName != null && btName.length() > 0)
            return btName;
        return btAddress;
    }

    private Timer hrReader = null;

    private void startTimer() {
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

    private void stopTimer() {
        if (hrReader == null)
            return;

        hrReader.cancel();
        hrReader.purge();
        hrReader = null;
    }

    private long lastTimestamp = 0;
    private long timerStartTime = 0;

    private void readHR() {
        if (hrProvider != null) {
            HRData data = hrProvider.getHRData();
            if(data != null) {
                long age = data.timestamp;
                long hrValue = 0;
                if(data.hasHeartRate)
                    hrValue = data.hrValue;

                tvHR.setText(String.format(Locale.getDefault(), "%d", hrValue));

                if (age != lastTimestamp) {
                    if (graphViewSeries == null) {
                        timerStartTime = System.currentTimeMillis();
                        DataPoint empty[] = {};
                        graphViewSeries = new LineGraphSeries<>(empty);
                        graphView.addSeries(graphViewSeries);
                        graphView.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
                            @Override
                            public String formatLabel(double value, boolean isValueX) {
                                if (isValueX) {
                                    return formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) value);
                                } else {
                                    return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
                                }
                            }
                        });
                    }

                    graphViewListData.add(new DataPoint((age - timerStartTime) / 1000, hrValue));
                    while (graphViewListData.size() > GRAPH_HISTORY_SECONDS) {
                        graphViewListData.remove(0);
                    }
                    graphViewArrayData = graphViewListData.toArray(graphViewArrayData);
                    graphViewSeries.resetData(graphViewArrayData);
                    lastTimestamp = age;
                }
            }
        }
    }

    @Override
    public void onOpenResult(boolean ok) {
        log(hrProvider.getProviderName() + "::onOpenResult(" + ok + ")");
        if (mIsScanning) {
            mIsScanning = false;
            startScan();
            return;
        }

        updateView();
    }

    @Override
    public void onScanResult(HRDeviceRef device) {
        log(hrProvider.getProviderName() + "::onScanResult(" + device.getAddress() + ", "
                + device.getName() + ")");
        deviceAdapter.deviceList.add(device);
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConnectResult(boolean connectOK) {
        log(hrProvider.getProviderName() + "::onConnectResult(" + connectOK + ")");
        if (connectOK) {
            save();
            if (hrProvider.getBatteryLevel() > 0) {
                tvBatteryLevel.setVisibility(View.VISIBLE);
                tvBatteryLevel.setText(String.format(Locale.getDefault(), "%s: %d%%",
                        getResources().getText(R.string.Battery_level), hrProvider.getBatteryLevel()));
            }
            startTimer();
        }
        updateView();
    }

    @Override
    public void onDisconnectResult(boolean disconnectOK) {
        log(hrProvider.getProviderName() + "::onDisconnectResult(" + disconnectOK + ")");
    }

    @Override
    public void onCloseResult(boolean closeOK) {
        log(hrProvider.getProviderName() + "::onCloseResult(" + closeOK + ")");
    }

    @Override
    public void log(HRProvider src, String msg) {
        log(src.getProviderName() + ": " + msg);
    }

    @SuppressLint("InflateParams")
    class DeviceAdapter extends BaseAdapter {

        final ArrayList<HRDeviceRef> deviceList = new ArrayList<>();
        LayoutInflater inflater = null;
        // --Commented out by Inspection (2017-08-11 13:06):Resources resources = null;

        DeviceAdapter(Context ctx) {
            inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //resources = ctx.getResources();
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
                //Note: Parent is AlertDialog so parent in inflate must be null
                row = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
            } else {
                row = convertView;
            }
            TextView tv = (TextView) row.findViewById(android.R.id.text1);
            //tv.setTextColor(resources.getColor(R.color.black));

            HRDeviceRef btDevice = deviceList.get(position);
            tv.setTag(btDevice);
            tv.setText(btDevice.getName());

            return tv;
        }
    }

}
