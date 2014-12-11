/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
import org.runnerup.common.util.Constants.DB;

public enum Sport {

    RUNNING(DB.ACTIVITY.SPORT_RUNNING, R.string.Running),
    BIKING(DB.ACTIVITY.SPORT_BIKING, R.string.Biking),
    OTHER(DB.ACTIVITY.SPORT_OTHER, R.string.Other);

    final int dbValue;
    final int textId;

    Sport(int dbValue, int txtValue) {
        this.dbValue = dbValue;
        this.textId = txtValue;
    }

    public int getDbValue() {
        return dbValue;
    }
    public int getTextId() { return textId; }

    static public Sport valueOf(int dbValue) {
        switch (dbValue) {
            case DB.ACTIVITY.SPORT_RUNNING:
                return RUNNING;
            case DB.ACTIVITY.SPORT_BIKING:
                return BIKING;
            default:
            case DB.ACTIVITY.SPORT_OTHER:
                return OTHER;
        }
    }
}
