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
import org.runnerup.widget.TitleSpinner;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZonesActivity extends Activity implements Constants {

	TitleSpinner ageSpinner;
	TitleSpinner sexSpinner;
	TitleSpinner maxHRSpinner;
	EditText	 zone1lo, zone1hi;
	EditText	 zone2lo, zone2hi;
	EditText	 zone3lo, zone3hi;
	EditText	 zone4lo, zone4hi;
	EditText	 zone5lo, zone5hi;
	EditText	 zone6lo, zone6hi;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.heartratezones);

		ageSpinner = (TitleSpinner) findViewById(R.id.hrzAge);
		sexSpinner = (TitleSpinner) findViewById(R.id.hrzSex);
		maxHRSpinner = (TitleSpinner) findViewById(R.id.hrzMHR);
		zone1lo = (EditText) findViewById(R.id.zone1lo);
		zone1hi = (EditText) findViewById(R.id.zone1hi);
		zone2lo = (EditText) findViewById(R.id.zone2lo);
		zone2hi = (EditText) findViewById(R.id.zone2hi);
		zone3lo = (EditText) findViewById(R.id.zone3lo);
		zone3hi = (EditText) findViewById(R.id.zone3hi);
		zone4lo = (EditText) findViewById(R.id.zone4lo);
		zone4hi = (EditText) findViewById(R.id.zone4hi);
		zone5lo = (EditText) findViewById(R.id.zone5lo);
		zone5hi = (EditText) findViewById(R.id.zone5hi);
		zone6lo = (EditText) findViewById(R.id.zone6lo);
		zone6hi = (EditText) findViewById(R.id.zone6hi);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}