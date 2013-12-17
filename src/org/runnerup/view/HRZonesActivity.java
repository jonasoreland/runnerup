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

import org.runnerup.R;
import org.runnerup.util.Constants;
import org.runnerup.util.HRZoneCalculator;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.TitleSpinner.OnSetValueListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.widget.EditText;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZonesActivity extends Activity implements Constants {

	TitleSpinner ageSpinner;
	TitleSpinner sexSpinner;
	TitleSpinner maxHRSpinner;

	EditText zones[] = {
			null, null,
			null, null,
			null, null,
			null, null,
			null, null,
			null, null
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.heartratezones);

		ageSpinner = (TitleSpinner) findViewById(R.id.hrzAge);
		sexSpinner = (TitleSpinner) findViewById(R.id.hrzSex);
		maxHRSpinner = (TitleSpinner) findViewById(R.id.hrzMHR);
		zones[0] = (EditText) findViewById(R.id.zone1lo);
		zones[1] = (EditText) findViewById(R.id.zone1hi);
		zones[2] = (EditText) findViewById(R.id.zone2lo);
		zones[3] = (EditText) findViewById(R.id.zone2hi);
		zones[4] = (EditText) findViewById(R.id.zone3lo);
		zones[5] = (EditText) findViewById(R.id.zone3hi);
		zones[6] = (EditText) findViewById(R.id.zone4lo);
		zones[7] = (EditText) findViewById(R.id.zone4hi);
		zones[8] = (EditText) findViewById(R.id.zone5lo);
		zones[9] = (EditText) findViewById(R.id.zone5hi);
		zones[10] = (EditText) findViewById(R.id.zone6lo);
		zones[11] = (EditText) findViewById(R.id.zone6hi);

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
					int age = Integer
							.parseInt(ageSpinner.getValue().toString());
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
					int maxHR = Integer.parseInt(maxHRSpinner.getValue().toString());
					for (int i = 0; i < 6; i++) {
						Pair<Integer,Integer> val = HRZoneCalculator.computeHRZone(i, maxHR);
						zones[2*i+0].setText(Integer.toString(val.first));
						zones[2*i+1].setText(Integer.toString(val.second));
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