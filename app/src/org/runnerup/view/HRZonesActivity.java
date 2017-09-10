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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.util.HRZoneCalculator;
import org.runnerup.util.HRZones;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;

import java.util.Locale;
import java.util.Vector;


public class HRZonesActivity extends AppCompatActivity implements Constants {

    private TitleSpinner ageSpinner;
    private TitleSpinner sexSpinner;
    private TitleSpinner maxHRSpinner;
    private HRZones hrZones;
    private HRZoneCalculator hrZoneCalculator;

    private final Vector<EditText> zones = new Vector<>();
    private boolean skipSave = false;

    private View addZoneRow(LayoutInflater inflator, ViewGroup root, int zone) {
        @SuppressLint("InflateParams") TableRow row = (TableRow) inflator.inflate(R.layout.heartratezonerow, null);
        TextView tv = (TextView) row.findViewById(R.id.zonetext);
        EditText lo = (EditText) row.findViewById(R.id.zonelo);
        EditText hi = (EditText) row.findViewById(R.id.zonehi);
        Pair<Integer, Integer> lim = hrZoneCalculator.getZoneLimits(zone);
        tv.setText(String.format(Locale.getDefault(), "%s %d %d%% - %d%%", getString(R.string.Zone), zone, lim.first, lim.second));
        lo.setTag("zone" + zone + "lo");
        hi.setTag("zone" + zone + "hi");
        zones.add(lo);
        zones.add(hi);

        return row;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.heartratezones);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        hrZones = new HRZones(this);
        hrZoneCalculator = new HRZoneCalculator(this);
        ageSpinner = (TitleSpinner) findViewById(R.id.hrz_age);
        sexSpinner = (TitleSpinner) findViewById(R.id.hrz_sex);
        maxHRSpinner = (TitleSpinner) findViewById(R.id.hrz_mhr);
        TableLayout zonesTable = (TableLayout) findViewById(R.id.zones_table);
        {
            int zoneCount = hrZoneCalculator.getZoneCount();
            LayoutInflater inflator = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            zones.clear();
            for (int i = 0; i < zoneCount; i++) {
                View row = addZoneRow(inflator, zonesTable, i + 1);
                zonesTable.addView(row);
            }
        }
        ageSpinner.setOnCloseDialogListener(new TitleSpinner.OnCloseDialogListener() {

            @Override
            public void onClose(TitleSpinner spinner, boolean ok) {
                if (ok) {
                    recomputeMaxHR();
                }
            }
        });

        sexSpinner.setOnCloseDialogListener(new TitleSpinner.OnCloseDialogListener() {

            @Override
            public void onClose(TitleSpinner spinner, boolean ok) {
                if (ok) {
                    recomputeMaxHR();
                }
            }
        });

        maxHRSpinner.setOnCloseDialogListener(new TitleSpinner.OnCloseDialogListener() {

            @Override
            public void onClose(TitleSpinner spinner, boolean ok) {
                if (ok) {
                    recomputeZones();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.hrzonessettings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_hrzonessettings_clear:
                clearHRSettings();
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hrZones.isConfigured()) {
            load();
        } else {
            recomputeZones();
        }
    }

    private void load() {
        for (int zone = 0; zone < zones.size() / 2; zone++) {
            Pair<Integer, Integer> values = hrZones.getHRValues(zone + 1);
            if (values != null) {
                EditText lo = zones.get(2 * zone /*+ 0*/);
                EditText hi = zones.get(2 * zone + 1);
                lo.setText(String.format(Locale.getDefault(), "%d", values.first));
                hi.setText(String.format(Locale.getDefault(), "%d", values.second));
                Log.e(getClass().getName(), "loaded " + (zone + 1) + " " + values.first + "-"
                        + values.second);
            }
        }
    }

    private void recomputeMaxHR() {
        new Handler().post(new Runnable() {

            @Override
            public void run() {
                try {
                    int age = Integer.parseInt(ageSpinner.getValue().toString());
                    int maxHR = HRZoneCalculator.computeMaxHR(age,
                            "Male".contentEquals(sexSpinner.getValue()));
                    maxHRSpinner.setValue(Integer.toString(maxHR));
                    recomputeZones();
                } catch (NumberFormatException ex) {

                }
            }
        });
    }

    private void recomputeZones() {
        new Handler().post(new Runnable() {

            @Override
            public void run() {
                try {
                    int zoneCount = hrZoneCalculator.getZoneCount();
                    int maxHR = Integer.parseInt(maxHRSpinner.getValue().toString());
                    for (int i = 0; i < zoneCount; i++) {
                        Pair<Integer, Integer> val = hrZoneCalculator.computeHRZone(i + 1, maxHR);
                        zones.get(2 * i /*+ 0*/).setText(String.format(Locale.getDefault(), "%d", val.first));
                        zones.get(2 * i + 1).setText(String.format(Locale.getDefault(), "%d", val.second));
                    }
                } catch (NumberFormatException ex) {
                }
            }
        });
    }

    private void saveHR() {
        try {
            Vector<Integer> vals = new Vector<>();
            System.err.print("saving: ");
            for (int i = 0; i < zones.size(); i += 2) {
                vals.add(Integer.valueOf(zones.get(i).getText().toString()));
                System.err.print(" " + vals.lastElement());
            }
            vals.add(Integer.valueOf(zones.lastElement().getText().toString()));
            Log.e(getClass().getName(), " " + vals.lastElement());
            hrZones.save(vals);
        } catch (Exception ex) {
        }
    }

    private void clearHRSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Clear_heart_rate_zone_settings));
        builder.setMessage(getString(R.string.Are_you_sure));
        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ageSpinner.clear();
                sexSpinner.clear();
                maxHRSpinner.clear();
                hrZones.clear();
                dialog.dismiss();
                skipSave = true;
                finish();
            }
        });

        builder.setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.show();
    }

    @Override
    protected void onPause() {
        if (!skipSave) {
            saveHR();
        }
        skipSave = false;
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
