/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import org.runnerup.R;

/**
 * This is just constant
 */
public enum Dimension {

    TIME(1, R.string.txt_dimension_time),
    DISTANCE(2, R.string.txt_dimension_distance),
    SPEED(3, R.string.txt_dimension_speed),
    PACE(4, R.string.txt_dimension_pace),
    HR(5, R.string.txt_dimension_heartrate),
    HRZ(6, R.string.txt_dimension_heartratezone);

    // TODO
    public static boolean SPEED_CUE_ENABLED = false;

    int value = 0;
    int textId = 0;

    private Dimension(int val, int textId) {
        this.value = val;
        this.textId = textId;
    }

    /**
     * @return the value
     */
    public int getValue() {
        return value;
    }

    public int getTextId() {
        return textId;
    }

    public boolean equal(Dimension what) {
        if (what == null || what.value != this.value)
            return false;
        return true;
    }
}
