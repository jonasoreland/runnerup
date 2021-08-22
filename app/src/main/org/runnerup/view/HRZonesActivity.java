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
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
        TextView tv = row.findViewById(R.id.zonetext);
        EditText lo = row.findViewById(R.id.zonelo);
        EditText hi = row.findViewById(R.id.zonehi);
        // disable setting the hi value
        hi.setKeyListener(null);
        hi.setEnabled(false);
        Pair<Integer, Integer> lim = hrZoneCalculator.getZoneLimits(zone);
        tv.setText(String.format(Locale.getDefault(), "%s %d %d%% - %d%%", getString(R.string.Zone), zone, lim.first, lim.second));
        lo.setTag("zone" + zone + "lo");
        hi.setTag("zone" + zone + "hi");
        int zoneCount = hrZoneCalculator.getZoneCount();

        // The last zone never loses focus, so needs this ugly hack
        // This is triggered when the done key is pressed i.e. on the last zone
        if (zone == zoneCount) {
            lo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                        int loZone = hrZoneCalculator.getZoneCount() - 1; /*  base 0 offset */
                        int loHR = Integer.parseInt(lo.getText().toString());
                        int maxHR = Integer.parseInt(maxHRSpinner.getValue().toString());
                        // each zone needs a range of at least 1 HR beat
                        if (loHR > maxHR - 1) {
                            loHR = maxHR - 1;
                            zones.get(2*loZone).setText(String.format(Locale.getDefault(), "%d", loHR));
                        }
                        // update the previous row's hi to the current row's lo
                        zones.get(2*loZone - 1).setText(String.format(Locale.getDefault(), "%d", loHR));
                    }
                    return false;
                }
            });
        }

        lo.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            int loZone = zone - 1; /* base 0 offset */

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // When focus is lost, check that the text field has valid values.
                if (!hasFocus) {
                    // Validate
                    int prevZone = loZone - 1;
                    int nextZone = loZone + 1;
                    int loHR = Integer.parseInt(lo.getText().toString());
                    int maxHR = Integer.parseInt(maxHRSpinner.getValue().toString());
                    int zoneCount = hrZoneCalculator.getZoneCount();
                    int zoneDiff = zoneCount - loZone;

                    // each zone should have at least one HR beat
                    if (loHR > maxHR - zoneDiff) {
                        loHR = maxHR - zoneDiff;
                        zones.get(2*loZone).setText(String.format(Locale.getDefault(), "%d", loHR));
                    }

                    if (nextZone < zoneCount) {
                        // check that the lo is less than the next lo
                        int nextLoHR = Integer.parseInt(zones.get(2*nextZone).getText().toString());
                        if (loHR >= nextLoHR) {
                            // lo's are out of order, use some default
                            loHR = nextLoHR -1;
                            lo.setText(String.format(Locale.getDefault(), "%d", loHR));
                        }
                        // update the previous row's hi to the current row's lo
                        if (loZone > 0) {
                            zones.get(2 * prevZone + 1).setText(String.format(Locale.getDefault(), "%d", loHR));
                        }
                    }

                    if (prevZone >= 0) {
                        //Check that the lo's are in increasing order
                        int prevLoHR = Integer.parseInt(zones.get(2*prevZone).getText().toString());
                        if (loHR <= prevLoHR) {
                            // lo's are out of order, use some default
                            loHR = prevLoHR +1;
                            lo.setText(String.format(Locale.getDefault(), "%d", loHR));
                        }
                        // update the previous row's hi to the current row's lo
                        zones.get(2*prevZone + 1).setText(String.format(Locale.getDefault(), "%d", loHR));
                    }
                }
            }
        });

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
        ageSpinner = findViewById(R.id.hrz_age);
        sexSpinner = findViewById(R.id.hrz_sex);
        maxHRSpinner = findViewById(R.id.hrz_mhr);
        TableLayout zonesTable = findViewById(R.id.zones_table);
        {
            int zoneCount = hrZoneCalculator.getZoneCount();
            LayoutInflater inflator = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            zones.clear();
            for (int i = 0; i < zoneCount; i++) {
                View row = addZoneRow(inflator, zonesTable, i + 1);
                zonesTable.addView(row);
            }
        }
        ageSpinner.setOnCloseDialogListener((spinner, ok) -> {
            if (ok) {
                recomputeMaxHR();
            }
        });

        sexSpinner.setOnCloseDialogListener((spinner, ok) -> {
            if (ok) {
                recomputeMaxHR();
            }
        });

        maxHRSpinner.setOnCloseDialogListener((spinner, ok) -> {
            if (ok) {
                recomputeZones();
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
        int id = item.getItemId();
        if (id == R.id.menu_hrzonessettings_clear) {
            clearHRSettings();
        }
        else if (id == android.R.id.home) {
                return super.onOptionsItemSelected(item);
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
        new Handler().post(() -> {
            try {
                int age = Integer.parseInt(ageSpinner.getValue().toString());
                int maxHR = HRZoneCalculator.computeMaxHR(age,
                        "Male".contentEquals(sexSpinner.getValue()));
                maxHRSpinner.setValue(Integer.toString(maxHR));
                recomputeZones();
            } catch (NumberFormatException ex) {

            }
        });
    }

    private void recomputeZones() {
        new Handler().post(() -> {
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.Clear_heart_rate_zone_settings)
                .setMessage(R.string.Are_you_sure)
                .setPositiveButton(R.string.OK, (dialog, which) -> {
                    ageSpinner.clear();
                    sexSpinner.clear();
                    maxHRSpinner.clear();
                    hrZones.clear();
                    dialog.dismiss();
                    skipSave = true;
                    finish();
                })
                .setNegativeButton(R.string.Cancel,
                        // Do nothing but close the dialog
                        (dialog, which) -> dialog.dismiss()
                )
                .show();
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
