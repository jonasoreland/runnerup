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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import org.runnerup.R;
import org.runnerup.hr.HRData;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.HRProvider.HRClient;
import org.runnerup.util.Formatter;
import org.runnerup.util.ViewUtil;
import org.runnerup.widget.WidgetUtil;

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
  private static final int GRAPH_HISTORY_SIZE = 180;
  private static final double xInterval = 60;

  private static final int REQUEST_BLUETOOTH_SETTINGS = 123;
  private static final int REQUEST_BLUETOOTH_ENABLE = 3002;
  private static final int REQUEST_BLUETOOTH_PERM = 3001;

  private DeviceAdapter deviceAdapter = null;
  private boolean mIsScanning = false;

  private final OnClickListener hrZonesClick =
      arg0 -> startActivity(new Intent(HRSettingsActivity.this, HRZonesActivity.class));

  private final OnClickListener scanButtonClick =
      v -> {
        clear();
        stopTimer();

        close();
        mIsScanning = true;
        log("select HR-provider");
        selectProvider();
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

    tvLog = findViewById(R.id.hr_log);
    tvLog.setMovementMethod(new ScrollingMovementMethod());
    tvBTName = findViewById(R.id.hr_device);
    tvHR = findViewById(R.id.hr_value);
    tvBatteryLevel = findViewById(R.id.hr_battery);
    tvBatteryLevel.setVisibility(View.GONE);
    scanButton = findViewById(R.id.scan_button);
    scanButton.setOnClickListener(scanButtonClick);
    connectButton = findViewById(R.id.connect_button);
    connectButton.setOnClickListener(arg0 -> connect());
    ViewUtil.Insets(findViewById(R.id.hr_settings_view), true);

    formatter = new Formatter(this);
    {
      graphView = new GraphView(this);
      graphView.setTitle(getString(org.runnerup.common.R.string.Heart_rate));
      DataPoint[] empty = {};
      graphViewSeries = new LineGraphSeries<>(empty);
      graphView.addSeries(graphViewSeries);
      graphView.getViewport().setXAxisBoundsManual(true);
      graphView.getViewport().setMinX(0);
      graphView.getViewport().setMaxX(xInterval);
      graphView.getViewport().setYAxisBoundsManual(true);
      graphView.getViewport().setMinY(40);
      graphView.getViewport().setMaxY(200);
      graphView
          .getGridLabelRenderer()
          .setLabelFormatter(
              new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                  if (isValueX) {
                    return formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, (long) value);
                  } else {
                    return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
                  }
                }
              });

      LinearLayout graphLayout = findViewById(R.id.hr_graph_layout);
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
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    Resources res = getResources();

    boolean isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_paired_ble), false);
    MenuItem item = menu.findItem(R.id.menu_hrdevice_paired_ble);
    item.setChecked(isChecked);

    isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_experimental), false);
    item = menu.findItem(R.id.menu_hrdevice_experimental);
    item.setChecked(isChecked);

    isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_mock), false);
    item = menu.findItem(R.id.menu_hrdevice_mock);
    item.setChecked(isChecked);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.menu_hrsettings_clear) {
      clearHRSettings();
      return true;
    } else if (id == R.id.menu_hrzones) {
      hrZonesClick.onClick(null);
      return true;
    } else if (id == R.id.menu_hrdevice_paired_ble
        || id == R.id.menu_hrdevice_experimental
        || id == R.id.menu_hrdevice_mock) {
      boolean isChecked = !item.isChecked();
      item.setChecked(isChecked);
      SharedPreferences prefs =
          PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      Resources res = getResources();
      Editor editor = prefs.edit();
      int key;
      if (id == R.id.menu_hrdevice_paired_ble) {
        key = R.string.pref_bt_paired_ble;
      } else if (id == R.id.menu_hrdevice_experimental) {
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
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_BLUETOOTH_ENABLE) {
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
    if (requestCode == REQUEST_BLUETOOTH_SETTINGS) {
      startScan();
    }
  }

  private int lineNo = 0;

  private void log(String msg) {
    logBuffer.insert(0, ++lineNo + ": " + msg + "\n");
    if (logBuffer.length() > 5000) {
      logBuffer.setLength(5000);
    }
    tvLog.setText(logBuffer.toString());
  }

  private void clearHRSettings() {
    new AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Clear_HR_settings)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> doClear())
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            // Do nothing but close the dialog
            (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void load() {
    Resources res = getResources();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    btName = prefs.getString(res.getString(R.string.pref_bt_name), null);
    btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
    btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
    Log.i(
        getClass().getName(),
        "btName: " + btName + "btAddress: " + btAddress + "btProviderName: " + btProviderName);

    if (btProviderName != null) {
      log("HRManager.get(" + btProviderName + ")");
      hrProvider = HRManager.getHRProvider(this, btProviderName);
    }
  }

  private void open() {
    if (hrProvider != null && !hrProvider.isEnabled()) {
      // For at least Pixel, BLUETOOTH_CONNECT is required to request starting BT
      if (checkPermissions()) {
        // User have to click again
        return;
      }

      if (hrProvider.startEnableIntent(this, REQUEST_BLUETOOTH_ENABLE)) {
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
    DialogInterface.OnClickListener listener = (dialog, which) -> dialog.dismiss();

    new AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Heart_rate_monitor_is_not_supported_for_your_device)
        .setNegativeButton(org.runnerup.common.R.string.Cancel, listener)
        .show();
  }

  private void clear() {
    btAddress = null;
    btName = null;
    btProviderName = null;
    clearGraph();
  }

  private void clearGraph() {
    DataPoint[] empty = {};
    graphViewSeries.resetData(empty);
    timerStartTime = 0;
  }

  private void updateView() {
    if (hrProvider == null) {
      scanButton.setEnabled(true);
      connectButton.setEnabled(false);
      connectButton.setText(org.runnerup.common.R.string.Connect);
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
      connectButton.setText(org.runnerup.common.R.string.Disconnect);
      connectButton.setEnabled(true);
    } else if (hrProvider.isConnecting()) {
      connectButton.setEnabled(false);
      connectButton.setText(org.runnerup.common.R.string.Connecting);
    } else {
      connectButton.setEnabled(btName != null);
      connectButton.setText(org.runnerup.common.R.string.Connect);
    }
  }

  private void selectProvider() {
    if (providers.size() == 0) {
      return;
    }

    if (providers.size() == 1) {
      hrProvider =
          HRManager.getHRProvider(HRSettingsActivity.this, providers.get(0).getProviderName());
      open();
      return;
    }

    final CharSequence[] items = new CharSequence[providers.size()];
    final CharSequence[] itemNames = new CharSequence[providers.size()];
    for (int i = 0; i < items.length; i++) {
      items[i] = providers.get(i).getProviderName();
      itemNames[i] = providers.get(i).getName();
    }

    hrProvider = null;
    new AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Select_type_of_Bluetooth_device)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              if (hrProvider == null && items.length > 0) {
                // Select the first in the list
                hrProvider = HRManager.getHRProvider(HRSettingsActivity.this, items[0].toString());
              }
              log("hrProvider = " + (hrProvider == null ? "null" : hrProvider.getProviderName()));
              open();
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            (dialog, which) -> {
              mIsScanning = false;
              hrProvider = null;
              load();
              open();
              dialog.dismiss();
            })
        .setSingleChoiceItems(
            itemNames,
            0,
            (arg0, arg1) -> {
              hrProvider = HRManager.getHRProvider(HRSettingsActivity.this, items[arg1].toString());
              log("hrProvider = " + (hrProvider == null ? "null" : hrProvider.getProviderName()));
            })
        .show();
  }

  private boolean checkPermissions() {
    List<String> requiredPerms = new ArrayList<>();
    List<String> requestPerms = new ArrayList<>();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      // No extra BT permissions required
      return false;
    }

    // Connect for paired, Scan for BLE
    requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT);
    requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN);
    boolean isDeniedPermission = false;

    for (final String perm : requiredPerms) {
      if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
          // A denied permission, show motivation in a popup
          String s = "Permission " + perm + " is explicitly denied";
          Log.i(getClass().getName(), s);
          isDeniedPermission = true;
        } else {
          requestPerms.add(perm);
        }
      }
    }

    if (requestPerms.size() == 0 && !isDeniedPermission) {
      return false;
    }
    final String[] permissions = new String[requestPerms.size()];
    requestPerms.toArray(permissions);

    AlertDialog.Builder builder =
        new AlertDialog.Builder(HRSettingsActivity.this)
            .setTitle(org.runnerup.common.R.string.Bluetooth_permission_required)
            .setMessage(getString(org.runnerup.common.R.string.Request_permission_text))
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss());
    if (requestPerms.size() > 0) {
      // Let Android request the permissions
      // Note that the result is not used, the user is dropped back to initial view when a request
      // is done.
      builder.setPositiveButton(
          org.runnerup.common.R.string.OK,
          (dialog, id) ->
              ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERM));
    } else if (isDeniedPermission) {
      // Open settings for the app (no direct shortcut to permissions)
      Intent intent =
          new Intent()
              .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
              .setData(Uri.fromParts("package", getPackageName(), null));
      builder.setPositiveButton(
          org.runnerup.common.R.string.OK, (dialog, id) -> startActivity(intent));
    }
    builder.show();

    return true;
  }

  private void startScan() {
    if (hrProvider == null) {
      log("hrProvider null in .startScan(), aborting");
      updateView();
      return;
    }

    log(hrProvider.getProviderName() + ".startScan()");
    updateView();
    deviceAdapter.deviceList.clear();

    if (checkPermissions()) {
      // User have to click again
      return;
    }

    hrProvider.startScan();

    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(org.runnerup.common.R.string.Scanning)
            .setPositiveButton(
                org.runnerup.common.R.string.Connect,
                (dialog, which) -> {
                  log(hrProvider.getProviderName() + ".stopScan()");
                  hrProvider.stopScan();
                  connect();
                  updateView();
                  dialog.dismiss();
                })
            .setNegativeButton(
                org.runnerup.common.R.string.Cancel,
                (dialog, which) -> {
                  log(hrProvider.getProviderName() + ".stopScan()");
                  hrProvider.stopScan();
                  load();
                  open();
                  dialog.dismiss();
                  updateView();
                })
            .setSingleChoiceItems(
                deviceAdapter,
                -1,
                (arg0, arg1) -> {
                  HRDeviceRef hrDevice = deviceAdapter.deviceList.get(arg1);
                  btAddress = hrDevice.getAddress();
                  btName = hrDevice.getName();
                });
    if (hrProvider.includePairingBLE()) {
      builder.setNeutralButton(
          "Pairing",
          (dialog, which) -> {
            dialog.cancel();
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(i);
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

    if (checkPermissions()) {
      // User have to click again
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
    Editor ed =
        prefs
            .edit()
            .putString(res.getString(R.string.pref_bt_name), btName)
            .putString(res.getString(R.string.pref_bt_address), btAddress)
            .putString(res.getString(R.string.pref_bt_provider), hrProvider.getProviderName());
    ed.apply();
  }

  private void doClear() {
    Resources res = getResources();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    Editor ed =
        prefs
            .edit()
            .remove(res.getString(R.string.pref_bt_name))
            .remove(res.getString(R.string.pref_bt_address))
            .remove(res.getString(R.string.pref_bt_provider));
    ed.apply();
  }

  private CharSequence getName() {
    if (btName != null && btName.length() > 0) return btName;
    return btAddress;
  }

  private Timer hrReader = null;

  private void startTimer() {
    hrReader = new Timer();
    hrReader.schedule(
        new TimerTask() {
          @Override
          public void run() {
            handler.post(() -> readHR());
          }
        },
        0,
        500);
  }

  private void stopTimer() {
    if (hrReader == null) return;

    hrReader.cancel();
    hrReader.purge();
    hrReader = null;
  }

  private long lastTimestamp = 0;
  private long timerStartTime = 0;

  private void readHR() {
    if (hrProvider == null) {
      return;
    }

    HRData data = hrProvider.getHRData();
    if (data == null || !data.hasHeartRate) {
      return;
    }

    long age = data.timestamp;
    long hrValue = data.hrValue;
    if (timerStartTime == 0) {
      timerStartTime = age;
      DataPoint[] empty = {};
      graphViewSeries.resetData(empty);
    }

    tvHR.setText(String.format(Locale.getDefault(), "%d", hrValue));
    if (age != lastTimestamp) {
      double x = (age - timerStartTime) / 1000.0;
      graphViewSeries.appendData(new DataPoint(x, hrValue), true, GRAPH_HISTORY_SIZE);
      lastTimestamp = age;

      // graphView works weird with live data
      graphView.getViewport().setMinY(graphViewSeries.getLowestValueY());
      graphView.getViewport().setMaxY(graphViewSeries.getHighestValueY());
      if (x > xInterval) {
        graphView.getViewport().setMinX(x - xInterval);
        graphView.getViewport().setMaxX(x);
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
    log(
        hrProvider.getProviderName()
            + "::onScanResult("
            + device.getAddress()
            + ", "
            + device.getName()
            + ")");
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
        tvBatteryLevel.setText(
            String.format(
                Locale.getDefault(),
                "%s: %d%%",
                getResources().getText(org.runnerup.common.R.string.Battery_level),
                hrProvider.getBatteryLevel()));
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
    final LayoutInflater inflater;

    // --Commented out by Inspection (2017-08-11 13:06):Resources resources = null;

    DeviceAdapter(Context ctx) {
      inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      // resources = ctx.getResources();
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
        // Note: Parent is AlertDialog so parent in inflate must be null
        row = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
      } else {
        row = convertView;
      }
      TextView tv = row.findViewById(android.R.id.text1);
      // tv.setTextColor(ContextCompat.getColor(this, R.color.black));

      HRDeviceRef btDevice = deviceList.get(position);
      tv.setTag(btDevice);
      tv.setText(btDevice.getName());

      return tv;
    }
  }
}
