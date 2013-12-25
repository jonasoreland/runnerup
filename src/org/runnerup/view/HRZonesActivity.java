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

import java.util.Vector;

import org.runnerup.R;
import org.runnerup.util.Constants;
import org.runnerup.util.HRZoneCalculator;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.TitleSpinner.OnSetValueListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZonesActivity extends Activity implements Constants {

	TitleSpinner ageSpinner;
	TitleSpinner sexSpinner;
	TitleSpinner maxHRSpinner;
	HRZoneCalculator hrZoneCalculator;
	
	Vector<EditText> zones = new Vector<EditText>();

	View addZoneRow(LayoutInflater inflator, ViewGroup root, int zone) {
		TableRow row = (TableRow) inflator.inflate(R.layout.heartratezonerow, null);
		TextView tv = (TextView) row.findViewById(R.id.zonetext);
		EditText lo = (EditText) row.findViewById(R.id.zonelo);
		EditText hi = (EditText) row.findViewById(R.id.zonehi);
		Pair<Integer,Integer> lim = hrZoneCalculator.getZoneLimits(zone);
		tv.setText("Zone " + zone + " " + lim.first + " - " + lim.second);
		lo.setTag("zone"+zone+"lo");
		hi.setTag("zone"+zone+"hi");
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.contains((String) lo.getTag())) {
			lo.setText(Integer.toString(pref.getInt((String) lo.getTag(), 0)));
		}
		if (pref.contains((String) hi.getTag())) {
			hi.setText(Integer.toString(pref.getInt((String) hi.getTag(), 0)));
		}
		zones.add(lo);
		zones.add(hi);
		
		return row;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.heartratezones);

		hrZoneCalculator = new HRZoneCalculator(this);
		ageSpinner = (TitleSpinner) findViewById(R.id.hrzAge);
		sexSpinner = (TitleSpinner) findViewById(R.id.hrzSex);
		maxHRSpinner = (TitleSpinner) findViewById(R.id.hrzMHR);
		TableLayout zonesTable = (TableLayout) findViewById(R.id.zonesTable);
		{
			int zoneCount = hrZoneCalculator.getZoneCount();
			LayoutInflater inflator = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			zones.clear();
			for (int i = 0; i < zoneCount ; i++ ) {
				View row = addZoneRow(inflator, zonesTable, i + 1);
				zonesTable.addView(row);
			}
		}
		ageSpinner.setOnSetValueListener(new OnSetValueListener(){

			@Override
			public String preSetValue(String newValue)
					throws IllegalArgumentException {
				recomputeMaxHR();
				return newValue;
			}

			@Override
			public int preSetValue(int newValue)
					throws IllegalArgumentException {
				recomputeMaxHR();
				return newValue;
			}});
		
		sexSpinner.setOnSetValueListener(new OnSetValueListener(){

			@Override
			public String preSetValue(String newValue)
					throws IllegalArgumentException {
				recomputeMaxHR();
				return newValue;
			}

			@Override
			public int preSetValue(int newValue)
					throws IllegalArgumentException {
				recomputeMaxHR();
				return newValue;
			}});

		maxHRSpinner.setOnSetValueListener(new OnSetValueListener(){

			@Override
			public String preSetValue(String newValue)
					throws IllegalArgumentException {
				recomputeZones();
				return newValue;
			}

			@Override
			public int preSetValue(int newValue)
					throws IllegalArgumentException {
				recomputeZones();
				return newValue;
			}});

		recomputeZones();
	}

	protected void recomputeMaxHR() {
		new Handler().post(new Runnable(){

			@Override
			public void run() {
				try {
					int age = Integer.parseInt(ageSpinner.getValue().toString());
					int maxHR = HRZoneCalculator.computeMaxHR(age, "Male".contentEquals(sexSpinner.getValue()));
					maxHRSpinner.setValue(Integer.toString(maxHR));
					maxHRSpinner.setValue(maxHR);
					recomputeZones();
				} catch (NumberFormatException ex) {

				}
			}});
	}

	protected void recomputeZones() {
		new Handler().post(new Runnable(){

			@Override
			public void run() {
				try {
					int zoneCount = hrZoneCalculator.getZoneCount();
					int maxHR = Integer.parseInt(maxHRSpinner.getValue().toString());
					for (int i = 0; i < zoneCount; i++) {
						Pair<Integer,Integer> val = hrZoneCalculator.computeHRZone(i + 1, maxHR);
						zones.get(2*i+0).setText(Integer.toString(val.first));
						zones.get(2*i+1).setText(Integer.toString(val.second));
					}
					saveHR();
				} catch (NumberFormatException ex) {
				}
			}
		});
	}

	protected void saveHR() {
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}