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
import org.runnerup.util.Constants.DB.DIMENSION;

/**
 * This is just constant
 */
public enum Dimension {

    TIME(DIMENSION.TIME, R.string.txt_dimension_time),
    DISTANCE(DIMENSION.DISTANCE, R.string.txt_dimension_distance),
    SPEED(DIMENSION.SPEED, R.string.txt_dimension_speed),
    PACE(DIMENSION.PACE, R.string.txt_dimension_pace),
    HR(DIMENSION.HR, R.string.txt_dimension_heartrate),
    HRZ(DIMENSION.HRZ, R.string.txt_dimension_heartratezone);

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

    public static Dimension valueOf(int val) {
        switch(val) {
            case -1:
                return null;
            case DIMENSION.TIME:
                return TIME;
            case DIMENSION.DISTANCE:
                return DISTANCE;
            case DIMENSION.SPEED:
                return SPEED;
            case DIMENSION.PACE:
                return PACE;
            case DIMENSION.HR:
                return HR;
            case DIMENSION.HRZ:
                return HRZ;
            default:
                return null;
        }
    }
}
