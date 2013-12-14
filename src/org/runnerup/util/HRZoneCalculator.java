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

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZoneCalculator {

	static final double zoneLimits[] = {
		0.60, 0.65,
		0.65, 0.75,
		0.75, 0.82,
		0.82, 0.89,
		0.89, 0.94,
		0.94, 1.00
	};
	
	public static Pair<Integer, Integer> computeHRZone(int zone, int maxHR) {
		if (zone < 0)
			return null;
		
		if (zone == 0) {
			return new Pair<Integer, Integer>(0, (int) Math.round(maxHR * zoneLimits[0]));
		}
		
		zone--; // 1-base => 0-based

		if (2 * zone + 1 >= zoneLimits.length)
			return null;

		return new Pair<Integer,Integer>((int)Math.round(maxHR * zoneLimits[2*zone+0]),
                          				 (int)Math.round(maxHR * zoneLimits[2*zone+1]));
	}
}
