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

package org.runnerup.workout;


import androidx.annotation.NonNull;

public class Range {

    public double minValue;
    public double maxValue;

    public Range(double minValue, double maxValue) {
        if (minValue <= maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
        } else {
            this.minValue = maxValue;
            this.maxValue = minValue;
        }
    }

    public boolean inside(double d) {
        return compare(d) == 0;
    }

    public int compare(double value) {
        if (value < minValue)
            return -1;
        if (value > maxValue)
            return 1;
        return 0;
    }

    public boolean contentEquals(Range range) {
        return this.maxValue == range.maxValue && this.minValue == range.minValue;
    }

    @NonNull
    public String toString() {
        return "[ " + minValue + " - " + maxValue + " ]";
    }
}
