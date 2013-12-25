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

import android.os.Build;
import android.util.Pair;
import android.annotation.TargetApi;
import android.content.Context;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZoneCalculator {

	public static int computeMaxHR(int age, boolean male) {
		if (male) {
			return Math.round(214 - age * 0.8f);
		} else {
			return Math.round(209 - age * 0.7f);
		}
	}

	public HRZoneCalculator(Context ctx) {
		// TODO load from preferences...
	}
	
	int zoneLimits[] = { 		
		60, // 1
		65, // 2
		75, // 3
		82, // 4
		89, // 5
		94, // 6
	};

	public int getZoneCount() {
		return zoneLimits.length;
	}

	public Pair<Integer, Integer> getZoneLimits(int zone) {
		zone--; // 1-base => 0-based
		if (zone < 0)
			return null;

		if (zone >= zoneLimits.length)
			return null;

		if (zone + 1 < zoneLimits.length)
			return new Pair<Integer, Integer>(zoneLimits[zone],
					zoneLimits[zone + 1]);
		else
			return new Pair<Integer, Integer>(zoneLimits[zone], 100);
	}

	public Pair<Integer, Integer> computeHRZone(int zone, int maxHR) {
		Pair<Integer, Integer> limits = getZoneLimits(zone);
		if (limits == null)
			return null;

		return new Pair<Integer, Integer>((limits.first * maxHR + 50) / 100,
				(limits.second * maxHR + 50) / 100);
	}
}
