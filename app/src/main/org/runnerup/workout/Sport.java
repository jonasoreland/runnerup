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
  RUNNING(DB.ACTIVITY.SPORT_RUNNING),
  BIKING(DB.ACTIVITY.SPORT_BIKING),
  OTHER(DB.ACTIVITY.SPORT_OTHER),
  ORIENTEERING(DB.ACTIVITY.SPORT_ORIENTEERING),
  WALKING(DB.ACTIVITY.SPORT_WALKING),
  TREADMILL(DB.ACTIVITY.SPORT_TREADMILL);

  final int dbValue;

  Sport(int dbValue) {
    this.dbValue = dbValue;
  }

  public int getDbValue() {
    return dbValue;
  }

  public static String textOf(int dbValue) {
    return textOf(null, dbValue);
  }

  public static String textOf(Resources res, int dbValue) {
    String sportName = null;
    if (res != null) {
      String[] sports = res.getStringArray(org.runnerup.common.R.array.sportEntries);
      if (0 <= dbValue && dbValue < sports.length) {
        sportName = sports[dbValue];
      }
      if (sportName == null) {
        sportName = res.getString(org.runnerup.common.R.string.Unknown);
      }
    }
    if (sportName == null) {
      // Some hardcoded values
      switch (dbValue) {
        case DB.ACTIVITY.SPORT_RUNNING:
        case DB.ACTIVITY.SPORT_ORIENTEERING:
        case DB.ACTIVITY.SPORT_WALKING:
        case DB.ACTIVITY.SPORT_TREADMILL:
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

  public static Sport valueOf(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_RUNNING:
        return RUNNING;
      case DB.ACTIVITY.SPORT_BIKING:
        return BIKING;
      case DB.ACTIVITY.SPORT_ORIENTEERING:
        return ORIENTEERING;
      case DB.ACTIVITY.SPORT_WALKING:
        return WALKING;
      case DB.ACTIVITY.SPORT_TREADMILL:
        return TREADMILL;
      default:
      case DB.ACTIVITY.SPORT_OTHER:
        return OTHER;
    }
  }

  public static int colorOf(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_RUNNING:
      case DB.ACTIVITY.SPORT_TREADMILL:
        return R.color.sportRunning;
      case DB.ACTIVITY.SPORT_BIKING:
        return R.color.sportBiking;
      case DB.ACTIVITY.SPORT_WALKING:
        return R.color.sportWalking;
      case DB.ACTIVITY.SPORT_ORIENTEERING:
        return R.color.sportOrienteering;
      case DB.ACTIVITY.SPORT_OTHER:
        return R.color.sportOther;
      default:
        return R.color.colorText;
    }
  }

  public static int drawableColored16Of(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_RUNNING:
      case DB.ACTIVITY.SPORT_TREADMILL:
        return R.drawable.sport_running;
      case DB.ACTIVITY.SPORT_BIKING:
        return R.drawable.sport_biking;
      case DB.ACTIVITY.SPORT_WALKING:
        return R.drawable.sport_walking;
      case DB.ACTIVITY.SPORT_ORIENTEERING:
        return R.drawable.sport_orienteering;
      case DB.ACTIVITY.SPORT_OTHER:
        return R.drawable.sport_other;
      default:
        return 0;
    }
  }

  public boolean IsWalking() {
    return dbValue == DB.ACTIVITY.SPORT_WALKING;
  }

  public boolean IsRunning() {
    return dbValue == DB.ACTIVITY.SPORT_RUNNING || dbValue == DB.ACTIVITY.SPORT_ORIENTEERING ||
        dbValue == DB.ACTIVITY.SPORT_TREADMILL;
  }

  public boolean IsCycling() {
    return dbValue == DB.ACTIVITY.SPORT_BIKING;
  }

  // part of filename used to determine type for Tapiriik files
  public String TapiriikType() {
    if (IsRunning()) {
      return "Running";
    } else if (IsCycling()) {
      return "Cycling";
    } else if (IsWalking()) {
      return "Walking";
    } else {
      return "Other";
    }
  }

  public static boolean isWithoutGps(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_TREADMILL:
        return true;
      default:
        return false;
    }
  }

  public boolean isWithoutGps() {
    return isWithoutGps(dbValue);
  }

  public static boolean HasManualDistance(int dbValue) {
    switch (dbValue) {
      case DB.ACTIVITY.SPORT_TREADMILL:
        return true;
      default:
        return false;
    }
  }
}
