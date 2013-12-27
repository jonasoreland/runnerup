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
package org.runnerup.util;

import org.runnerup.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Pair;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZones {

	int zones[] = null;

	public HRZones(Context ctx) {
		this(ctx.getResources(), PreferenceManager.getDefaultSharedPreferences(ctx));
	}
	
	public HRZones(Resources res, SharedPreferences prefs) {
		final String pct = res.getString(R.string.pref_hrz_values);
		if (prefs.contains(pct)) {
			int limits[] = SafeParse.parseIntList(prefs.getString(pct, ""));
			if (limits != null) {
				zones = limits;
			}
		}
	}

	public boolean isConfigured() {
		return zones != null;
	}

	public double getZone(double value) {
		if (zones != null) {
			int z = 0;
			for (z = 0; z < zones.length; z++) {
				if (zones[z] >= value)
					break;
			}

			double lo = (z == 0) ? 0 : zones[z-1];
			double hi = (z == zones.length) ? Math.ceil(value) : zones[z];
			double add = (value - lo) / (hi - lo);
			return z + add;
		}
		return 0;
	}

	public Pair<Integer, Integer> getHRValues(int zone) {
		if (zones != null && zone + 1 < zones.length) {
			if (zone == 0) {
				return new Pair<Integer,Integer>(0, zones[1]);
			} else {
				return new Pair<Integer,Integer>(zones[zone], zones[zone+1]);
			}
		}
		return null;
	}
}
