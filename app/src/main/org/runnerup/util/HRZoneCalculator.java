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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Pair;

import org.runnerup.R;


public class HRZoneCalculator {

    public static int computeMaxHR(int age, boolean male) {
        if (male) {
            return Math.round(214 - age * 0.8f);
        } else {
            return Math.round(209 - age * 0.7f);
        }
    }

    public HRZoneCalculator(Context ctx) {
        this(ctx.getResources(), PreferenceManager
                .getDefaultSharedPreferences(ctx));
    }

    private HRZoneCalculator(Resources res, SharedPreferences prefs) {
        final String pct = res.getString(R.string.pref_hrz_thresholds);
        if (prefs.contains(pct)) {
            int[] limits = SafeParse.parseIntList(prefs.getString(pct, ""));
            if (limits != null) {
                zoneLimitsPct = limits;
            }
        }
    }

    private int[] zoneLimitsPct = {
            63, // 1
            71, // 2
            78, // 3
            85, // 4
            92
    // 5
    };

    public int getZoneCount() {
        return zoneLimitsPct.length;
    }

    public Pair<Integer, Integer> getZoneLimits(int zone) {
        zone--; // 1-base => 0-based
        if (zone < 0)
            return null;

        if (zone >= zoneLimitsPct.length)
            return null;

        if (zone + 1 < zoneLimitsPct.length)
            return new Pair<>(zoneLimitsPct[zone],
                    zoneLimitsPct[zone + 1]);
        else
            return new Pair<>(zoneLimitsPct[zone], 100);
    }

    public Pair<Integer, Integer> computeHRZone(int zone, int maxHR) {
        Pair<Integer, Integer> limits = getZoneLimits(zone);
        if (limits == null)
            return null;

        return new Pair<>((int) Math.round(limits.first * maxHR / 100.0),
                (int) Math.round(limits.second * maxHR / 100.0d));
    }
}
