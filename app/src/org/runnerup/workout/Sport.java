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

import android.content.res.Resources;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;

public enum Sport {
    RUNNING(DB.ACTIVITY.SPORT_RUNNING)
    , BIKING(DB.ACTIVITY.SPORT_BIKING)
    , OTHER(DB.ACTIVITY.SPORT_OTHER)
    , ORIENTEERING(DB.ACTIVITY.SPORT_ORIENTEERING)
    , WALKING(DB.ACTIVITY.SPORT_WALKING)
    ;

    final int dbValue;

    Sport(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    static public String textOf(int dbValue) {
        return textOf(null, dbValue);
    }

    static public String textOf(Resources res, int dbValue) {
        String sportName = null;
        if (res != null) {
            String sports[] = res.getStringArray(R.array.sportEntries);
            if (0 <= dbValue && dbValue < sports.length) {
                sportName = sports[dbValue];
            }
            if (sportName == null) {
                sportName = res.getString(R.string.Unknown);
            }
        }
        if (sportName == null) {
            //Some hardcoded values
            switch (dbValue) {
                case DB.ACTIVITY.SPORT_RUNNING:
                case DB.ACTIVITY.SPORT_ORIENTEERING:
                case DB.ACTIVITY.SPORT_WALKING:
                    sportName = "Running";
                    break;
                case DB.ACTIVITY.SPORT_BIKING:
                    sportName = "Biking";
                    break;
                default:
                    sportName = "Other";
                    break;
            }
        }
        return sportName;
    }

    static public Sport valueOf(int dbValue) {
        switch (dbValue) {
            case DB.ACTIVITY.SPORT_RUNNING:
                return RUNNING;
            case DB.ACTIVITY.SPORT_BIKING:
                return BIKING;
            case DB.ACTIVITY.SPORT_ORIENTEERING:
                return ORIENTEERING;
            case DB.ACTIVITY.SPORT_WALKING:
                return WALKING;
            default:
            case DB.ACTIVITY.SPORT_OTHER:
                return OTHER;
        }
    }

    public boolean IsWalking() {
        return dbValue == DB.ACTIVITY.SPORT_WALKING;
    }

    public boolean IsRunning() {
        return dbValue == DB.ACTIVITY.SPORT_RUNNING ||
                dbValue == DB.ACTIVITY.SPORT_ORIENTEERING;
    }

    public boolean IsCycling() {
        return dbValue == DB.ACTIVITY.SPORT_BIKING;
    }
}
